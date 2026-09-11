# HLD — Railway Reservation Platform (IRCTC-like)

> Scope: two capabilities only — (1) **Search available trains** and (2) **Confirm a ticket (PNR)**.
> Everything else (payments, cancellations, waitlist, refunds) is called out but kept secondary.

---

## 1. Problem Restatement & Key Insight

A train runs on a **fixed route** = an ordered list of stations `S0 → S1 → … → Sn`.
The gaps between consecutive stations are **segments (legs)**: `L0=[S0,S1), L1=[S1,S2), … L(n-1)`.

A passenger booking from station `Si` to `Sj` (i < j) occupies **all segments `Li … L(j-1)`** — and *only* those segments.

**Central insight — a seat is a shared resource across segments, not a single unit.**
Two passengers can occupy the **same physical seat** on the same train/date if their journeys do **not overlap** on the route.

```
Route:  A --- B --- C --- D --- E
Seat 12 booked A→C  [xxxxxxx........ ]   occupies L0,L1
Seat 12 free   C→E             [....xxxxxxx]   L2,L3 still sellable
```

So **availability for a query (src, dst) = min over the requested segments of (free seats on that segment)**.
This segment model is the heart of the design; get it right and search + booking fall out naturally.

---

## 2. Requirements

### 2.1 Functional
- **Search:** given `(sourceStation, destStation, journeyDate)` → list of trains running that leg on that date, with **per-class availability count** and **fare**.
- **Book:** given `(trainId, journeyDate, class, src, dst, passengers[])` → issue a **confirmed PNR** with **allocated seats**; booked seats/segments must never be double-sold.

### 2.2 Non-Functional
- **Correctness under concurrency** is the #1 priority: no two confirmed tickets may hold the same seat on any overlapping segment.
- **High read : write ratio.** Search is ~100–1000× more frequent than booking → cache aggressively.
- **Booking spikes** (Tatkal window) → strong contention on a small hot set of `(train, date, class)` rows.
- **Availability** for search; **consistency** for booking (correctness > latency at commit time).
- Search p99 < ~300 ms; booking confirmation < ~2–3 s acceptable.

### 2.3 Rough Scale (back-of-envelope)
- ~10–15k trains/day, ~100–1000 seats/train/class, ~4–5 classes.
- Peak ~ hundreds of thousands of concurrent users in Tatkal; ~10k+ booking attempts/min on hot trains.
- Search QPS peak: tens of thousands/sec.

---

## 3. High-Level Architecture

```
                         ┌────────────────┐
  Clients ──► CDN/LB ──► │  API Gateway   │ (auth, rate-limit, idempotency keys)
                         └───────┬────────┘
                 ┌───────────────┼─────────────────┐
                 ▼                                  ▼
        ┌──────────────┐                   ┌──────────────────┐
        │ Search Svc   │                   │  Booking Svc     │
        │ (read path)  │                   │  (write path)    │
        └──────┬───────┘                   └────────┬─────────┘
               │ reads                              │ txn writes
        ┌──────▼───────┐   invalidate/refresh ┌─────▼──────────┐
        │ Availability │◄─────────────────────┤  Inventory DB  │  (source of truth)
        │ Cache (Redis)│                       │  (RDBMS, sharded by train+date)
        └──────────────┘                       └────────────────┘
        ┌──────────────┐   ┌──────────────┐   ┌────────────────┐
        │ Train/Route  │   │  Fare Svc    │   │  PNR / Ticket  │
        │ metadata DB  │   │  (rules)     │   │  store         │
        └──────────────┘   └──────────────┘   └────────────────┘
```

Two clearly separated paths:
- **Read path (Search):** served mostly from cache; eventually-consistent availability counts are fine.
- **Write path (Booking):** goes to the RDBMS source of truth inside a transaction; strongly consistent.

---

## 4. Data Model

> Full runnable DDL (PostgreSQL) + parameterized operation SQL: see **`zluri-schema.sql`** in this directory.

### 4.1 Static / metadata (rarely changes — cache heavily)
```
Station(station_id, code, name, ...)
Train(train_id, name, run_days_bitmask, ...)
RouteStop(train_id, seq, station_id, arr_time, dep_time, day_offset)
   -- ordered stops; segment Li is between seq=i and seq=i+1
Coach(coach_id, train_id, class, seat_count)
FareRule(train_id, class, from_seq, to_seq, price)  -- or distance-based
```

### 4.2 Inventory (source of truth, hot data) — **the crux**

Per `(train_id, journey_date, class)` we manage seat occupancy across segments. Two viable representations:

**Option A — Segment-count table (simple, count-only):**
```
SegmentAvailability(
   train_id, journey_date, class, segment_idx, available_count
)  PK(train_id, journey_date, class, segment_idx)
```
- Availability(src→dst) = `MIN(available_count)` over segments `[src_seq, dst_seq)`.
- Booking = decrement each segment in `[src, dst)` in one transaction (guarded so none goes negative).
- Pro: tiny, fast, easy to reason about. Con: does **not** track *which* physical seat — needs a separate step to allocate a concrete seat/berth.

**Option B — Per-seat segment bitmask (tracks physical seat, enables berth allocation):**
```
SeatInventory(
   train_id, journey_date, coach_id, seat_no,
   occupied_bitmask BIGINT/BYTES   -- bit i set => segment Li occupied
)
```
- Seat is bookable for `[i, j)` iff `occupied_bitmask & requestMask == 0`, where `requestMask` has bits `i..j-1` set.
- Book = `occupied_bitmask |= requestMask` (CAS/row-locked).
- Pro: exact berth allocation (lower/upper), supports seat maps. Con: heavier; allocation scans candidate seats.

**Recommended: hybrid.**
- Keep **Option A** as the fast availability counter that powers Search and the first-line booking guard.
- Keep **Option B** (or a `BookedSegment` ledger) to allocate the concrete seat once the count check passes.
- Both updated in the **same DB transaction** so they never diverge.

### 4.3 Booking / PNR
```
PNR(pnr_id, user_id, train_id, journey_date, class, src_seq, dst_seq,
    status, fare, created_at, idempotency_key UNIQUE)
Passenger(pnr_id, name, age, coach_id, seat_no, berth_pref)
```

---

## 5. Search Flow

```
GET /v1/search?src=NDLS&dst=BCT&date=2026-09-10&class=3A
```
1. Resolve trains whose route contains `src` before `dst` and that **run on that date**
   (precomputed index: `station_pair → [train_ids]`, filtered by run-day + date).
2. For each candidate train+class, compute availability:
   `avail = MIN(available_count over segments [src_seq, dst_seq))`.
3. Fetch fare from FareRule for `(train, class, src_seq, dst_seq)`.
4. Return `[{trainId, dep, arr, class, availableSeats, fare, status:AVAILABLE/WL/...}]`.

**Serving strategy**
- Availability counts cached in **Redis** keyed `avail:{train}:{date}:{class}` → list/segment structure; TTL short (secs) + **event-driven invalidation** on booking commit.
- Search reads are **eventually consistent** — a slightly stale count is acceptable; the authoritative check happens at booking time. This is the standard "availability shown is indicative" behavior.
- Static route/fare data in a read-replica + local cache.

---

## 6. Booking Flow (the hard part: correctness under concurrency)

```
POST /v1/bookings   { trainId, date, class, src, dst, passengers[], idempotencyKey }
```

### 6.1 Steps
1. **Idempotency:** upsert on `idempotency_key`; if a PNR already exists, return it (safe retries / double-submit / payment retries).
2. **Begin DB transaction** on the inventory shard for `(train, date)`.
3. **Guarded segment decrement** for every segment in `[src_seq, dst_seq)`:
   ```sql
   UPDATE SegmentAvailability
   SET available_count = available_count - :n
   WHERE train_id=:t AND journey_date=:d AND class=:c
     AND segment_idx BETWEEN :src AND :dst-1
     AND available_count >= :n;      -- guard: never oversell
   -- rows_affected must equal (dst-src); else rollback → not enough seats
   ```
4. **Allocate concrete seat(s)** using the bitmask ledger (Option B): pick seat(s) whose `occupied_bitmask & requestMask == 0`, set the bits.
5. **Insert PNR + Passenger** rows with allocated seats, status=CONFIRMED.
6. **Commit.** On commit, emit `BookingConfirmed` event → invalidate/refresh availability cache.
7. If any step fails/insufficient → **rollback**, return WAITLIST/NO-AVAILABILITY.

### 6.2 Concurrency control — options & choice
The contended resource is the small set of segment rows for a hot `(train, date, class)`.

- **Pessimistic row locking (SELECT … FOR UPDATE)** on the affected segment rows, ordered by `segment_idx` to avoid deadlocks. Simple, correct, and the guard makes overselling impossible. **Chosen default.**
- **Optimistic (version/CAS)**: `WHERE available_count = :expected`; retry on conflict. Great at low contention, but livelocks under Tatkal spikes.
- **Atomic conditional UPDATE with `available_count >= n` guard** (shown above): lock-light, DB enforces invariant even without explicit SELECT FOR UPDATE. **Preferred primitive** — combine with short transactions.

> The invariant "sum of confirmed bookings covering a segment ≤ seat_count" is enforced **atomically in one transaction** across all requested segments. That guarantees no double-selling on any overlapping leg.

### 6.3 Taming Tatkal contention (hot rows)
- **Short transactions**: do payment *after* a **soft hold**, not while holding row locks.
  - Phase 1: reserve inventory → create PNR in `HOLD` (e.g. 8–10 min TTL), decrement counts.
  - Phase 2: payment; on success `HOLD→CONFIRMED`; on timeout, a reaper releases the hold and increments counts back.
- **Queue / admission control** in front of hot trains: a per-`(train,date,class)` FIFO (Kafka/Redis stream) serializes attempts, smoothing lock contention and giving fair ordering.
- **Rate limiting & idempotency** at the gateway to blunt retries/bots.
- Optional: **waitlist/RAC** modeled as ordered queue once `available_count` hits 0.

---

## 7. Sequence Diagrams (D2)

> Diagrams are written in **D2** (`shape: sequence_diagram`). Source files live alongside this doc:
> `zluri-search.d2`, `zluri-booking.d2`, `zluri-reaper.d2`.
> Render: `d2 zluri-booking.d2 zluri-booking.svg` (or `d2 --watch <file>`).

### 7.1 Search
```d2
shape: sequence_diagram
C:  Client
GW: API Gateway
S:  Search Svc
R:  Redis (avail cache)
DB: Read Replica (metadata/inventory)

C -> GW: GET /v1/search?src&dst&date&class
GW -> S: forward (auth, rate-limit)
S -> DB: resolve trains for src<dst running on date\n(precomputed station_pair->trains index)

per candidate train+class: {
  S -> R: GET avail:{train}:{date}:{class}
  cache hit: {
    R -> S: per-segment counts
  }
  cache miss: {
    S -> DB: MIN(available_count) over [src_seq, dst_seq)
    DB -> S: counts
    S -> R: SET avail:{...} (short TTL)
  }
  S -> DB: fare(train, class, src_seq, dst_seq)
}
S -> C: [{train, dep, arr, class, availableSeats, fare, status}]
```

### 7.2 Booking (soft-HOLD → pay → confirm, with anti-oversell guard)
```d2
shape: sequence_diagram
C:   Client
GW:  API Gateway
B:   Booking Svc
DB:  Inventory DB (shard: train+date)
PAY: Payment Svc
MQ:  Event Bus
R:   Redis (avail cache)

C -> GW: POST /v1/bookings (Idempotency-Key)
GW -> B: forward
B -> DB: SELECT pnr BY idempotency_key

already exists: {
  DB -> B: existing PNR
  B -> C: return existing PNR (idempotent)
}

new request: {
  B -> DB: BEGIN TxN
  B -> DB: UPDATE segment_availability -n\nWHERE segment in [src,dst) AND available_count >= n

  insufficient (rows_affected != dst-src): {
    B -> DB: ROLLBACK
    B -> C: 409 NO_AVAILABILITY / WAITLIST
  }

  guard passed: {
    B -> DB: allocate seat(s): occupied_bitmask & mask == 0 -> set bits
    B -> DB: INSERT pnr(status=HOLD, hold_expiry), passenger rows
    B -> DB: COMMIT
    B -> C: 200 { pnr, status: HOLD, seats, holdExpiry }

    C -> GW: POST /v1/bookings/{pnr}/pay
    GW -> B: forward
    B -> PAY: charge

    payment success: {
      PAY -> B: OK
      B -> DB: UPDATE pnr HOLD -> CONFIRMED
      B -> MQ: publish BookingConfirmed (outbox)
      MQ -> R: invalidate/refresh avail:{train}:{date}:{class}
      B -> C: 200 CONFIRMED (PNR + seats)
    }
    payment fail/timeout: {
      PAY -> B: FAIL
      B -> C: 402 PAYMENT_FAILED
      B -> DB: reaper later: HOLD expired ->\nrestore counts, free seat bits, PNR -> EXPIRED
    }
  }
}
```

### 7.3 Hold reaper (background, idempotent)
```d2
shape: sequence_diagram
J:  Reaper Job (cron)
DB: Inventory DB
MQ: Event Bus
R:  Redis

J -> DB: SELECT pnr WHERE status=HOLD AND hold_expiry < now()\nFOR UPDATE SKIP LOCKED

per expired hold: {
  J -> DB: BEGIN TxN
  J -> DB: UPDATE segment_availability +n over [src,dst)
  J -> DB: clear occupied_bitmask bits for freed seats
  J -> DB: UPDATE pnr HOLD -> EXPIRED
  J -> DB: COMMIT
  J -> MQ: publish HoldReleased
  MQ -> R: refresh availability cache
}
```

---

## 8. Scaling & Reliability

- **Sharding:** inventory partitioned by `hash(train_id, journey_date)` — a train-date's segments live together so a booking is a **single-shard transaction** (no distributed txn needed). This is the key that keeps bookings ACID-simple.
- **Read replicas + Redis** absorb search load; writes go to primary of the owning shard.
- **Cache coherence:** availability cache updated via CDC/booking events; short TTL as backstop.
- **Precompute** `station_pair → trains` and run-day filters offline for fast search.
- **Reaper job** releases expired HOLDs and restores counts (idempotent).
- **Observability:** track oversell attempts (must be 0), hold→confirm conversion, lock wait times, cache hit ratio.
- **Failure handling:** payment failure → hold auto-expires; DB primary failover via replication; outbox pattern for reliable event emission.

---

## 9. API Summary

| Method | Path | Purpose |
|-------|------|---------|
| GET | `/v1/search?src&dst&date&class` | list trains + per-class availability + fare |
| POST | `/v1/bookings` (Idempotency-Key) | reserve + confirm → returns PNR & seats |
| GET | `/v1/bookings/{pnr}` | fetch ticket status |

---

## 10. Key Trade-offs (senior lens)

| Decision | Choice | Why / cost |
|---|---|---|
| Search consistency | Eventually consistent (cached) | Read scale; authoritative recheck at booking |
| Booking consistency | Strong, single-shard txn | Correctness is non-negotiable; avoids 2PC |
| Inventory model | Segment counts + seat bitmask ledger | Fast availability + real berth allocation |
| Concurrency | Guarded atomic UPDATE + short txn (+ optional queue) | No oversell; survives Tatkal spikes |
| Payment vs lock | Soft HOLD then pay | Never hold row locks across a slow payment |
| Sharding key | `(train_id, journey_date)` | Keeps each booking on one shard |

## 11. Out of Scope (acknowledged, secondary)
Cancellations/refunds, waitlist promotion & RAC lifecycle, dynamic/surge pricing, quotas (Ladies/Senior/Tatkal), multi-leg/connecting journeys, notifications, and chart preparation. Each layers on top of the segment-inventory + PNR core above.
