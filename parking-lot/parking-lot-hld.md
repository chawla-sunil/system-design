# Design a Parking Lot System — Amazon Interview Playbook

> Audience: senior engineer (SDE-2 / SDE-3) preparing for an Amazon system design / OOD round.
> This doc is written as if I am *in* the interview: what I say, when I say it, why I say it,
> what the interviewer is probing for, and what they will push back on.

---

## Table of Contents

1. [How I Run the First 5 Minutes](#1-how-i-run-the-first-5-minutes)
2. [Clarifying Questions (the ones that actually change the design)](#2-clarifying-questions)
3. [Assumptions I State Out Loud](#3-assumptions-i-state-out-loud)
4. [Functional Requirements](#4-functional-requirements-fr)
5. [Non-Functional Requirements](#5-non-functional-requirements-nfr)
6. [Back-of-the-Envelope Capacity Estimation](#6-back-of-the-envelope-capacity-estimation)
7. [API Design](#7-api-design)
8. [Data Model](#8-data-model)
9. [Low-Level Design (OOD / Class Model)](#9-low-level-design-ood--class-model)
10. [High-Level Architecture](#10-high-level-architecture)
11. [The Concurrency Problem (this is the real interview)](#11-concurrency--the-core-problem)
12. [Pricing & Billing](#12-pricing--billing)
13. [Notifications](#13-notifications)
14. [Fault Tolerance & Failure Modes](#14-fault-tolerance--failure-modes)
15. [Latency Budget & Performance](#15-latency-budget--performance)
16. [Scaling: 1 Lot → 10,000 Lots](#16-scaling-1-lot--10000-lots)
17. [Observability & Operations](#17-observability--operations)
18. [Security & Privacy](#18-security--privacy)
19. [Edge Cases the Interviewer Will Fire at You](#19-edge-cases--tricky-scenarios)
20. [Likely Interviewer Questions + Model Answers](#20-likely-interviewer-questions--model-answers)
21. [Trade-off Summary Table](#21-trade-off-summary-table)
22. [Amazon-Specific: Leadership Principles in a Design Round](#22-amazon-specific-leadership-principles-in-a-design-round)
23. [Interview Best Practices & Anti-Patterns](#23-interview-best-practices--anti-patterns)
24. [Whiteboard Time Plan](#24-whiteboard-time-plan-60-min)

---

## 1. How I Run the First 5 Minutes

The single biggest differentiator at senior level is **not jumping to a solution**. My opening script:

> "Before I draw anything, I want to pin down scope, because 'parking lot' can mean three very
> different systems: (a) a single-garage embedded/OOD problem, (b) a multi-tenant SaaS like
> SpotHero/ParkWhiz serving thousands of operators, or (c) a real-time IoT sensor network.
> Which one are we building? I'll also confirm scale, because a 500-spot garage and a
> 10,000-garage platform have almost nothing in common architecturally."

Then I follow a fixed structure and **say the structure out loud** so the interviewer can steer:

```
Requirements  →  Scale estimate  →  API  →  Data model  →  HLD  →  Deep dive
   (5 min)         (3 min)        (5)      (7)          (10)      (20)
                                                        ↓
                                              Concurrency / FT / Latency
```

**Why this matters:** Amazon interviewers grade on a rubric that explicitly includes
"requirements gathering" and "handles ambiguity." Silence during design = no signal.
Narrate your reasoning continuously.

---

## 2. Clarifying Questions

Ask these. Don't ask all 30 — pick 6–8 high-leverage ones, note the rest as "I'll assume X, tell me if wrong."

### Scope & Product
| # | Question | Why it changes the design |
|---|---|---|
| 1 | Single lot or multi-lot platform? | Single lot → in-memory OOD, one DB. Multi-lot → sharding, tenancy, service decomposition |
| 2 | Do we own the hardware (gates, sensors, cameras) or is it app-only? | Hardware → offline mode, edge compute, eventual consistency mandatory |
| 3 | Reservations (book ahead) or walk-in only? | Reservations introduce a *scheduling* problem (interval overlap), not just a counter |
| 4 | Is payment in scope? | If yes → idempotency, PCI, refunds, reconciliation, external gateway failure handling |
| 5 | Who are the actors? | Driver, gate/kiosk, attendant, lot operator, admin, support agent |
| 6 | Do we assign a *specific* spot or just "a spot on level 2"? | Specific spot = tighter concurrency; zone-based = far more scalable |

### Domain Rules
| # | Question | Why |
|---|---|---|
| 7 | Vehicle types & spot types? | Motorcycle / Car / SUV / Truck / EV / Handicapped — drives the allocation matrix |
| 8 | Can a small vehicle take a large spot? | Fallback rules = allocation policy strategy, not hardcoded ifs |
| 9 | Pricing model? | Hourly, tiered, flat, day-max, dynamic/surge, validation codes, monthly passes |
| 10 | Overstay / no-show policy? | Drives background jobs + notification triggers |
| 11 | Lost ticket handling? | Real-world requirement graders love; flat penalty + attendant override |

### Scale & Ops
| # | Question | Why |
|---|---|---|
| 12 | How many lots, spots per lot, peak entries/sec? | Sizing, sharding, cache strategy |
| 13 | Read:write ratio? | Availability lookups massively dominate → read replicas + cache |
| 14 | Availability target? What's the cost of downtime? | 99.9 vs 99.99 changes multi-AZ/multi-region decision |
| 15 | Is stale availability acceptable to a browsing user? | Yes → cache aggressively. This unlocks huge scale |
| 16 | Regions / regulatory constraints? | GDPR (plate = PII), data residency |

### The question that scores points
> "What is worse for the business: telling a driver a spot is available when it isn't
> (overbooking), or telling them it's full when it isn't (lost revenue)?"

This forces the interviewer to hand you the **consistency vs. availability** decision, and it
proves you think in business trade-offs. My default: **never double-allocate a physical spot**
(bad UX at the gate, cars can't queue), so I choose **strong consistency at the allocation
boundary** and **eventual consistency everywhere else (browse/search/dashboard)**.

---

## 3. Assumptions I State Out Loud

Write these in a corner of the board so you can point at them later.

1. **Scale target:** multi-lot platform — 10,000 lots, avg 400 spots ⇒ **~4M spots**.
2. Peak entries/exits: **~2,000 TPS** globally; a single mega-lot peaks at **~10 entries/sec**.
3. Availability *reads* (app browsing) ~**50,000 QPS** — read-heavy, roughly **100:1** read:write.
4. Entry via **license-plate ANPR camera** with **QR/ticket fallback** (cameras misread ~2–5%).
5. Payment via external gateway (Stripe-like); we never store PANs.
6. Spot assignment is **specific spot** at entry (needed for enforcement + guidance signage).
7. Reservations are **in scope** as an extension (I design for it, deep-dive if asked).
8. p99 gate decision latency budget: **< 300 ms** end-to-end (gate must open before driver honks).
9. **Availability > perfect accuracy** for browse; **correctness > availability** for allocation & billing.
10. Money is **never** floating point — minor units (`long cents`) + currency code.

---

## 4. Functional Requirements (FR)

### P0 — must have (build these)
- **FR1 — Entry:** vehicle arrives → identify (plate/QR/ticket) → allocate a compatible spot →
  issue ticket → open gate. Reject with a reason if lot full.
- **FR2 — Exit:** identify vehicle → compute duration & fee → collect payment → free the spot →
  open gate.
- **FR3 — Spot allocation policy:** match `VehicleType → Set<SpotType>` with a pluggable strategy
  (nearest-to-elevator, nearest-to-exit, level-balancing, EV-charger-aware).
- **FR4 — Availability query:** free-spot counts per lot, per level, per spot type. Near-real-time.
- **FR5 — Pricing:** compute fee from duration, spot type, time-of-day, and applicable
  discounts/validations.
- **FR6 — Ticket lifecycle:** `ISSUED → PARKED → PAYMENT_PENDING → PAID → EXITED`, plus
  `LOST`, `VOIDED`.
- **FR7 — Admin:** onboard a lot, define levels/spots/rates, block spots for maintenance,
  manual override by attendant.

### P1 — strong nice-to-have
- **FR8 — Reservations:** reserve a spot for `[start, end)`; hold with TTL; no-show release.
- **FR9 — Notifications:** entry receipt, "your 2 free hours end in 15 min," overstay alert,
  payment receipt, "lot is 95% full" to operator.
- **FR10 — Monthly passes / subscriptions**, corporate accounts, validation codes.
- **FR11 — Enforcement:** detect a car in a spot without an active ticket (sensor mismatch) →
  raise a violation.

### P2 — explicitly out of scope (say this!)
- Valet, car wash, dynamic ML surge pricing, in-lot turn-by-turn navigation, EV charge
  session billing, license-plate ML model itself (treat ANPR as a black-box service).

> **Interview tip:** explicitly naming out-of-scope items is a strong signal. It shows you
> know the space is bigger than the 45 minutes you have, and it protects you from scope creep.

---

## 5. Non-Functional Requirements (NFR)

| NFR | Target | Justification / how I achieve it |
|---|---|---|
| **Availability** | 99.99% for gate/entry-exit path; 99.9% for admin/reporting | Cars physically can't wait. Multi-AZ, stateless services, graceful degradation to offline gate mode |
| **Latency** | p50 < 100 ms, p99 < 300 ms for allocate/exit; p99 < 150 ms for availability read | Human-perceptible at a gate; budget breakdown in §15 |
| **Consistency** | **Strong** for spot allocation + payment (linearizable per spot). **Eventual** (≤ 5 s) for availability counts, dashboards, search | Prevents double-allocation where it hurts; allows caching where it doesn't |
| **Durability** | 99.999999999% for tickets & payments; zero tolerance for lost billing records | RDBMS w/ synchronous replica + WAL + PITR; outbox pattern for events |
| **Scalability** | Linear horizontal scale to 10k lots / 4M spots / 2k TPS writes / 50k QPS reads | Stateless app tier, shard by `lot_id`, cache reads |
| **Throughput per lot** | ≥ 20 entries/sec sustained on a single lot (event/stadium egress) | Per-lot partitioning; per-spot row locks, not per-lot table locks |
| **Fault tolerance** | Survive AZ loss with no data loss (RPO=0), RTO < 60 s | Multi-AZ sync replication, automated failover, health-checked LB |
| **Idempotency** | 100% of state-changing APIs | Client-supplied `Idempotency-Key`, dedupe table |
| **Auditability** | Immutable, append-only log of every ticket & money event, retained 7 years | Event store / append-only `ticket_events` table |
| **Security** | Plate & payment data encrypted at rest + in transit; least privilege; no PAN storage | KMS envelope encryption, tokenized cards, PCI-DSS SAQ-A scope |
| **Maintainability** | Add a new vehicle/spot type or pricing rule with **config**, not a deploy | Strategy pattern + rules stored as data |
| **Cost** | Cache-first reads; cold ticket data → object storage after 90 days | Tiered storage, TTL cache |
| **Observability** | Every gate decision traceable end-to-end by `ticket_id` | Distributed tracing, structured logs, RED + business metrics |

---

## 6. Back-of-the-Envelope Capacity Estimation

Do this *fast* (2–3 min) and out loud. It justifies every later decision.

```
Lots                       = 10,000
Avg spots/lot              = 400
Total spots                = 4,000,000

Avg turns per spot per day = 3
Parking events/day         = 4M * 3        = 12,000,000
Avg write TPS (2 writes/event: entry+exit) = 24M / 86,400 ≈ 280 TPS
Peak factor                = 5–8x (rush hour, event egress)
Peak write TPS             ≈ 2,000 TPS

Availability reads: 50,000 QPS peak (app browse, signage, dashboards)
Read : Write ratio         ≈ 100 : 1     → CACHE IS MANDATORY

STORAGE
Ticket row ≈ 300 bytes (ids, timestamps, type, fee, status)
Hot data (12M/day * 300B)  ≈ 3.6 GB/day  ≈ 1.3 TB/year
+ event log (~5 events/ticket * 200B)     ≈ 12 GB/day ≈ 4.4 TB/year
Spot table: 4M rows * ~200B ≈ 800 MB     → fits entirely in memory / Redis

BANDWIDTH
2,000 TPS * ~2 KB request/response ≈ 4 MB/s writes — trivial.
```

**Conclusions I state immediately:**
- Spot state is **only ~1 GB** → the entire live availability map fits in **Redis**. Huge.
- Writes (2k TPS) are modest for a sharded RDBMS → **don't over-engineer**; Postgres/DynamoDB is fine.
- Reads dominate 100:1 → **cache + read replicas** carry the load.
- Storage grows ~5 TB/year → **archive tickets older than 90 days to S3/Glacier**.

---

## 7. API Design

REST for external, gRPC internally. All mutations idempotent.

```http
POST /v1/lots/{lotId}/entries
Idempotency-Key: 7f3c...   # required
{
  "vehicle":  { "plate": "KA01AB1234", "type": "CAR", "isEV": true },
  "gateId":   "gate-3",
  "reservationId": null,
  "capturedAt": "2026-09-10T09:01:22Z"
}
→ 201 {
  "ticketId": "tkt_01H...",
  "spot": { "id": "L2-A-014", "level": 2, "type": "COMPACT_EV" },
  "issuedAt": "...", "estimatedRate": { "amountMinor": 5000, "currency": "INR", "unit": "HOUR" },
  "gateAction": "OPEN"
}
→ 409 { "code": "LOT_FULL", "nearbyLots": [...] }        # actionable error
→ 409 { "code": "VEHICLE_ALREADY_INSIDE", "ticketId": "tkt_..." }
```

```http
POST /v1/tickets/{ticketId}/checkout        # price it, don't free the spot yet
→ 200 { "amountMinor": 12000, "currency":"INR", "breakdown": [...],
        "paymentIntentId": "pi_...", "validUntil": "...+15m" }

POST /v1/tickets/{ticketId}/exit
Idempotency-Key: ...
{ "gateId": "gate-exit-1", "paymentRef": "pi_..." }
→ 200 { "status": "EXITED", "gateAction": "OPEN", "receiptUrl": "..." }
→ 402 { "code": "PAYMENT_REQUIRED", "amountMinor": 12000 }

GET  /v1/lots/{lotId}/availability?type=CAR
→ 200 { "lotId":"...", "free": { "CAR": 42, "EV": 3, "HANDICAPPED": 5 },
        "total": {...}, "asOf": "2026-09-10T09:01:20Z", "staleBySeconds": 2 }

POST /v1/reservations
{ "lotId":"...", "spotType":"CAR", "start":"...", "end":"...", "vehicleId":"..." }
→ 201 { "reservationId":"...", "holdExpiresAt": "...+10m", "spotId": "L1-B-07" }

DELETE /v1/reservations/{id}
GET    /v1/lots/{lotId}/spots?status=OCCUPIED     # ops
POST   /v1/lots/{lotId}/spots/{spotId}:block      # maintenance
POST   /v1/tickets/{ticketId}:lost                # lost ticket → flat penalty
```

**Design notes to call out:**
- `asOf` + `staleBySeconds` on availability = **honest about eventual consistency**. Clients can decide.
- Separate `checkout` (quote + hold price) from `exit` (release spot) so payment failure doesn't strand the gate.
- Errors return **actionable data** (`nearbyLots`) — product thinking, an Amazon "Customer Obsession" signal.
- Idempotency-Key is **required, not optional**, on every mutation. Gates retry aggressively on flaky links.

---

## 8. Data Model

### Choice: relational (Postgres/Aurora) for transactional core + Redis for hot state

**Why RDBMS?** The allocation + billing path needs **ACID transactions across 2–3 rows**
(spot, ticket, payment). Doing that in an eventually-consistent KV store means hand-rolling
sagas and compensations for a workload that is only 2k TPS — that's unjustified complexity.
Shard by `lot_id`; a lot is a natural, perfectly-isolated partition key with no cross-shard
transactions in the hot path.

**Why also Redis?** 100:1 read ratio + 1 GB working set. Redis serves availability counters
and reservation holds.

> If the interviewer pushes DynamoDB: that's fine too — partition key `LOT#{lotId}`,
> sort key `SPOT#{spotId}` / `TICKET#{ticketId}`, and use `TransactWriteItems` +
> `ConditionExpression` for the atomic claim. I'd choose Dynamo if we needed
> multi-region active-active or unbounded scale; I'd choose Aurora for richer queries
> and simpler transactions. **State the trade-off; don't pretend there's one right answer.**

### Schema (core tables)

```sql
lot(          id PK, operator_id, name, geo POINT, timezone, address, status )
level(        id PK, lot_id FK, number, name )
spot(         id PK, lot_id FK, level_id FK, code,          -- 'L2-A-014'
              type ENUM(MOTORCYCLE, COMPACT, LARGE, EV, HANDICAPPED, OVERSIZE),
              status ENUM(FREE, HELD, OCCUPIED, OUT_OF_SERVICE),
              current_ticket_id NULL,
              version BIGINT,                                -- optimistic locking
              UNIQUE(lot_id, code),
              INDEX idx_alloc (lot_id, type, status)         -- the allocation index
)
vehicle(      id PK, plate_hash UNIQUE, plate_encrypted, type, owner_id NULL )
ticket(       id PK, lot_id, spot_id, vehicle_id, entry_gate, exit_gate,
              entered_at, exited_at NULL, status,
              amount_minor NULL, currency, payment_id NULL,
              reservation_id NULL,
              INDEX (lot_id, status), INDEX (vehicle_id, status) )
ticket_event( id PK, ticket_id, seq, type, payload JSONB, created_at )   -- append-only audit
rate_plan(    id PK, lot_id, spot_type, rules JSONB, effective_from, effective_to )
reservation(  id PK, lot_id, spot_id NULL, vehicle_id, start_at, end_at,
              status ENUM(HELD, CONFIRMED, CONSUMED, EXPIRED, CANCELLED),
              hold_expires_at,
              EXCLUDE USING gist (spot_id WITH =, tstzrange(start_at,end_at) WITH &&)
                 WHERE (status IN ('HELD','CONFIRMED'))      -- DB-enforced no double-booking
)
payment(      id PK, ticket_id, gateway_ref, amount_minor, currency, status, idempotency_key )
idempotency(  key PK, request_hash, response_body, status, created_at, expires_at )
outbox(       id PK, aggregate_id, event_type, payload, published_at NULL )
```

**Two lines worth pointing at on the whiteboard:**

1. `EXCLUDE USING gist (... && ...)` — Postgres range-exclusion constraint. It makes
   **double-booking a spot physically impossible at the database level**, not just in app logic.
   Interviewers love a defense-in-depth answer.
2. `outbox` — the transactional outbox pattern. We write the ticket **and** the "SpotOccupied"
   event in **one transaction**, then a relay publishes to Kafka. This eliminates the classic
   dual-write bug (DB commits, Kafka publish fails → cache/notifications permanently wrong).

---

## 9. Low-Level Design (OOD / Class Model)

If the round is OOD-flavored (very common for Amazon SDE-2), this is the main event.
**Lead with the design patterns and the reason for each.**

```
                       ParkingLotService (facade / orchestration)
                                  |
        +-------------------------+---------------------------+
        |                         |                           |
 SpotAllocator            PricingEngine              TicketRepository
 (Strategy)               (Strategy + Decorator)     (Repository)
        |                         |
 NearestToExitStrategy     HourlyRate, TieredRate, FlatRate,
 LevelBalancingStrategy    DayMaxDecorator, DiscountDecorator
 EvAwareStrategy
```

```java
public enum VehicleType { MOTORCYCLE, CAR, SUV, TRUCK, EV }
public enum SpotType    { MOTORCYCLE, COMPACT, LARGE, OVERSIZE, EV, HANDICAPPED }
public enum SpotStatus  { FREE, HELD, OCCUPIED, OUT_OF_SERVICE }

/** Which spots can hold which vehicle, in preference order. Data, not if-else. */
public interface SpotCompatibilityPolicy {
    List<SpotType> compatibleSpots(Vehicle v);   // CAR -> [COMPACT, LARGE, OVERSIZE]
}

/** Pluggable: nearest-to-exit, level-balancing, EV-aware, revenue-maximizing. */
public interface SpotAllocationStrategy {
    Optional<Spot> choose(List<Spot> candidates, AllocationContext ctx);
}

public interface PricingStrategy {
    Money price(Ticket t, Instant exitAt);       // Money = long minorUnits + Currency
}

public interface ParkingLotService {
    Ticket park(ParkRequest req) throws LotFullException, VehicleAlreadyInsideException;
    Quote  quote(TicketId id, Instant at);
    Receipt unpark(UnparkRequest req) throws PaymentRequiredException;
}
```

**Patterns and the one-line justification for each (say these verbatim):**

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | allocation, pricing | New rules per lot without touching core code; **Open/Closed Principle** |
| **Factory** | `Vehicle` / `Spot` creation | Centralizes type→object mapping |
| **State machine** | `Ticket` status | Illegal transitions rejected in one place, not scattered |
| **Repository** | persistence | Lets me unit-test allocation with an in-memory fake |
| **Observer / pub-sub** | notifications, analytics | Adding "notify operator at 95% full" costs zero changes to the parking path |
| **Decorator** | pricing modifiers | Compose `Hourly + DayMax + CorporateDiscount` |
| **Singleton** | ❌ avoid | Say explicitly: "I avoid singletons for lot state — it kills testability and breaks the moment we run more than one process." |

**The mistake 80% of candidates make:** they build `HashMap<SpotType, Queue<Spot>>` inside a
single JVM and call it done. Immediately flag it:

> "This in-memory model works for one process. The instant we run two app servers, the map is
> wrong. So the source of truth must be the DB/Redis, and the in-memory structure is only a
> cache. Let me show how the claim becomes atomic."

That single sentence separates SDE-1 from SDE-3.

---

## 10. High-Level Architecture

```
  Camera/ANPR ─┐
  Kiosk / QR  ─┼─► Gate Controller (edge, on-prem)
  Mobile App  ─┘        │  local cache + offline queue
                        ▼
                 API Gateway (authN/Z, rate limit, TLS)
                        │
      ┌─────────────────┼─────────────────┬──────────────────┐
      ▼                 ▼                 ▼                  ▼
 Entry/Exit Svc   Availability Svc   Reservation Svc    Pricing Svc
 (strong consist.) (cache-first)     (holds + TTL)      (stateless, pure)
      │                 │                 │                  │
      ├──────────► Redis (spot state, counters, holds, locks) ◄───┤
      │                 ▲
      ▼                 │ (cache updater consumes events)
  Aurora/Postgres ──► Outbox Relay ──► Kafka ──┬──► Notification Svc ──► SNS/SES/FCM
  (sharded by lot_id)                          ├──► Analytics / S3 / Redshift
        │                                      └──► Enforcement Svc (overstay scanner)
        ▼
  Read replicas ──► reporting / operator dashboards
                                          Payment Svc ──► External Gateway (Stripe)
```

**Service boundary rationale (say this — it's a senior signal):**
- **Entry/Exit** is the only service that mutates spot state → single writer, easy to reason about.
- **Availability** is read-only and cache-first → scales independently, can be down without
  blocking gates (degrade to "check at gate").
- **Pricing** is a **pure function** → trivially testable, cacheable, independently deployable,
  and safe to change during business hours.
- **Notification** is fully async off Kafka → a flaky SMS vendor can **never** block a gate.

---

## 11. Concurrency — The Core Problem

This is where the interview is won or lost. **The scenario:** 5 cars at 3 entry gates, 1 spot left.

### ❌ What breaks

```java
// RACE CONDITION — classic check-then-act
Spot s = repo.findFirstFree(lotId, COMPACT);   // T1 and T2 both read spot L2-A-014
s.setStatus(OCCUPIED);                          // both write
repo.save(s);                                   // two cars, one spot. Gate jam.
```

Even `synchronized` only fixes it inside **one JVM**. With N app servers it's still broken.

### ✅ Solution 1 — Atomic conditional update (my default)

Push the check-and-set into the storage engine. One statement, no distributed lock, no
coordination service:

```sql
UPDATE spot
   SET status = 'OCCUPIED', current_ticket_id = :ticketId, version = version + 1
 WHERE id = (
     SELECT id FROM spot
      WHERE lot_id = :lotId AND type = ANY(:compatibleTypes) AND status = 'FREE'
      ORDER BY distance_to_exit
      LIMIT 1
      FOR UPDATE SKIP LOCKED        -- ⭐ the key clause
 )
RETURNING id, code, level_id;
```

**`FOR UPDATE SKIP LOCKED` is the money line.** Explain it:
- `FOR UPDATE` row-locks the candidate so no one else can claim it.
- `SKIP LOCKED` means concurrent transactions **don't block** — they skip to the *next*
  free spot instead of queuing behind each other.
- Result: N gates allocating in parallel scale nearly **linearly** with zero contention,
  instead of serializing on a lot-level lock.
- Zero rows returned ⇒ genuinely full ⇒ return `LOT_FULL`. No ambiguity.

DynamoDB equivalent: `UpdateItem` with `ConditionExpression: status = :FREE`, retry on
`ConditionalCheckFailedException` with jitter.

### ✅ Solution 2 — Redis for the reservation *hold* (pre-claim)

Reservations and app-based "hold my spot for 10 min" need a **TTL'd** lock so a crashed client
can't leak a spot forever:

```
SET spot:{lotId}:{spotId} {holderId} NX PX 600000     # atomic, self-expiring
# release safely with a Lua CAS so you never delete someone else's lock:
if redis.call("GET",KEYS[1]) == ARGV[1] then return redis.call("DEL",KEYS[1]) else return 0 end
```

**Say the caveat before they ask it:** "Redis locks are an *optimization*, not the source of
truth. Under failover, Redlock's safety guarantees are debated. So the **DB conditional update
remains the final authority** — Redis just reduces contention and gives me cheap TTL semantics."
That sentence alone signals real production experience.

### ✅ Solution 3 — Per-lot sharding / partitioned ordering

For an extreme case (stadium egress, 100 entries/sec on one lot), route all writes for a lot to
one partition (Kafka key = `lot_id`, or a consistent-hash-routed shard). Serial processing per
lot, parallel across lots. **Trade-off:** simpler reasoning, but the per-lot partition becomes a
throughput ceiling and a failure domain.

### Other concurrency hazards to name proactively

| Hazard | Fix |
|---|---|
| **Double-submit from a flaky gate** (retries) | `Idempotency-Key` + dedupe table; same key returns the same stored response |
| **Same plate entering twice** (camera double-fire) | Unique partial index on `(vehicle_id) WHERE status='PARKED'`; dedupe window of ~60 s |
| **Exit while payment in flight** | Ticket state machine: only `PAID → EXITED`. Payment webhook is the trigger, not the client |
| **Lost spot release on crash** | Spots have no TTL — so run a **reconciliation job**: any `OCCUPIED` spot whose ticket is `EXITED`, or `HELD` past TTL, gets swept every 60 s |
| **Deadlock on multi-row txn** | Always acquire locks in a fixed order (`spot` then `ticket`); keep transactions < 50 ms; no external calls inside a txn |
| **Counter drift in Redis** | Counters are derived, not authoritative — periodic full recompute from DB (every 5 min) reconciles drift |
| **Clock skew across gates** | Server-assigned timestamps for billing; gate timestamps kept only as metadata |

> **Golden rule I state:** *"Never make a network call to an external system inside a database
> transaction."* Payment gateway calls happen **outside** the txn, coordinated by the ticket
> state machine.

---

## 12. Pricing & Billing

```java
Money price(Ticket t, Instant exit) {
    Duration d = Duration.between(t.enteredAt(), exit);
    // rounding policy is a BUSINESS decision — ask! ceil-to-15-min is typical
    long units = ceilDiv(d.toMinutes(), 15);
    ...
}
```

Things to raise (each earns a point):
- **Rounding policy** — round up to the next 15 min? Grace period of 10 min for
  enter-then-immediately-leave? *Ask the interviewer; it's a product decision.*
- **Day maximum / overnight rollover** at midnight in the **lot's local timezone**, not UTC.
- **DST**: a duration can legitimately be 25 h. Always compute on epoch millis; use zone rules
  only for display and day-boundary rules.
- **Idempotent charging**: `Idempotency-Key = ticketId + attempt`. Never charge twice.
- **Payment failure at exit**: don't strand the car. Open the gate, mark the ticket
  `EXITED_UNPAID`, and pursue collection async (registered card, invoice, plate-based dunning).
  *This is Customer Obsession — a blocked exit ramp is worse than a small bad-debt rate.*
- **Reconciliation**: nightly job compares our ledger vs. gateway settlement; alert on mismatch.
- **Money type**: `long amountMinor + Currency`. Never `double`. Say this out loud.

---

## 13. Notifications

**Principle: notifications are strictly asynchronous and never on the critical path.**

```
Entry/Exit Svc ──(same txn)──► outbox table
                                   │
                       Outbox Relay (CDC/Debezium or poller)
                                   ▼
                        Kafka topic: parking.events
                                   ▼
                        Notification Service
                     (templating, user prefs, dedupe, rate limit)
                       │        │        │        │
                     Push     SMS      Email    Webhook (operator)
                     (FCM)   (SNS)    (SES)
```

### Triggers
| Event | Channel | Audience |
|---|---|---|
| Ticket issued | Push + email | Driver — spot number + directions |
| Free-period ending in 15 min | Push | Driver — reduces disputes, drives revenue |
| Overstay beyond reservation | Push, then SMS | Driver |
| Payment succeeded / failed | Push + email receipt | Driver |
| Reservation hold expiring in 2 min | Push | Driver |
| Lot ≥ 95% full | Webhook + email | Operator (dynamic pricing / signage trigger) |
| Spot sensor mismatch (violation) | Ops dashboard + SMS | Attendant |
| Gate offline > 60 s | PagerDuty | On-call |

### Design details worth mentioning
- **Exactly-once is impossible; at-least-once + idempotent consumers is the real answer.**
  Dedupe key = `(ticketId, eventType, channel)` in Redis with a 24 h TTL, so a Kafka redelivery
  doesn't spam the driver twice.
- **User preferences & quiet hours** — don't SMS at 3 a.m.
- **Rate limiting / batching** — a 5,000-car stadium egress must not fan out 5,000 SMS in one
  second and get us blacklisted by the carrier. Token bucket per channel.
- **Priority lanes** — payment/security alerts bypass marketing throttles.
- **Templating + i18n** — templates are data (S3/DB), not code; supports per-operator branding.
- **DLQ** — failed sends land in a dead-letter queue with exponential backoff and a max of 5
  retries, then a manual-review queue.
- **Scheduled notifications** (e.g., "your 2 free hours end at 11:00") — use a delay queue
  (SQS delay / Redis sorted set keyed by fire-time) rather than polling every ticket.

---

## 14. Fault Tolerance & Failure Modes

Walk a **failure matrix**. This is a top-tier senior signal — juniors describe the happy path only.

| Component fails | Blast radius | Mitigation | Degraded behavior |
|---|---|---|---|
| **Gate ↔ cloud network down** | One lot can't allocate | **Offline mode**: gate has a local pre-allocated block of spot IDs + local ticket sequence; queues events and syncs on reconnect | Gate keeps operating; counts reconcile later. *Availability > perfect accuracy for entry.* |
| **ANPR misreads plate** | One vehicle | QR ticket fallback, manual attendant entry, fuzzy plate match (edit distance ≤ 1) on exit | Attendant override; log for model retraining |
| **App server dies mid-allocation** | 1 request | Txn rolls back — spot stays `FREE`. Client retries with same Idempotency-Key | No leak |
| **DB primary fails** | One shard's lots | Multi-AZ sync replica, automated failover (RTO < 60 s, RPO = 0) | Brief write freeze; gates fall back to offline mode |
| **Redis cluster down** | Availability API slow | **Cache is optional**: fall back to DB read replicas; circuit breaker prevents a stampede | Higher latency, correct data |
| **Kafka down** | Notifications & analytics delayed | Outbox table retains events durably; relay drains on recovery | Notifications late, **parking still works** |
| **Payment gateway down** | Exits | Circuit breaker → open gate, mark `EXITED_UNPAID`, collect async | Cars flow; small bad-debt risk |
| **Notification vendor down** | Messages | DLQ + retry with backoff; multi-vendor failover for SMS | Delayed, never lost |
| **Entire AZ lost** | Nothing (if designed right) | Stateless tier across ≥ 3 AZs, DB multi-AZ, LB health checks | Transparent |
| **Region lost** | Regional | Cross-region async replica, DNS failover; RPO ~seconds | Manual promote; accept small RPO for cost |
| **Poison message in Kafka** | Consumer stuck | Max-retry then DLQ; never block the partition | Isolated |
| **Sensor says occupied, no ticket** | Revenue leak | Enforcement service raises a violation for attendant | Human in the loop |

**Cross-cutting patterns to name:**
- **Circuit breaker + bulkhead** around every external dependency (payment, ANPR, SMS).
- **Timeouts everywhere** (no unbounded waits) + **retry with exponential backoff and jitter**
  — mention jitter explicitly to avoid thundering-herd/retry-storm.
- **Graceful degradation ladder:** full → cached availability → offline gate → attendant + paper
  ticket. *There is always a manual fallback; a physical business must never fully stop.*
- **Reconciliation jobs** as the safety net for every eventual-consistency choice.
- **Chaos testing / game days** on the gate path.

---

## 15. Latency Budget & Performance

**Why it matters:** the driver is stopped at a boom barrier. Above ~1 s of silence they think
it's broken and start reversing/honking. Budget:

```
Total gate decision p99 target: 300 ms
──────────────────────────────────────────
ANPR capture → API call            60 ms   (edge, parallel with plate normalization)
TLS + API GW + authN/Z             15 ms
Entry service business logic        5 ms
Spot allocation DB txn             25 ms   ← FOR UPDATE SKIP LOCKED, single round trip
Ticket insert (same txn)          (incl.)
Outbox insert (same txn)          (incl.)
Commit + sync replica ack          20 ms   ← the cost of RPO=0. Worth it.
Response + gate actuation          80 ms
Slack / network variance           95 ms
──────────────────────────────────────────
```

**Optimizations, and when to apply them:**
- **One round trip, not three** — allocation is a single SQL statement, not
  read-then-decide-then-write.
- **Connection pooling** (PgBouncer) — connection setup would blow the budget.
- **No external network calls inside the txn** — payment/notification are post-commit and async.
- **Precompute distance ordering** on spots (`distance_to_exit` column) so allocation never sorts
  at query time.
- **Cache-first availability reads**: Redis `HGETALL lot:{id}:counts` ⇒ **< 5 ms**; DB is fallback.
- **Async everything non-blocking**: notifications, analytics, receipt generation.
- **Regional deployment** — put the API in the same region as the lots; a Mumbai gate hitting
  us-east-1 pays 200 ms RTT before doing any work.
- **Measure p99/p999, not averages** — averages hide the driver who waited 4 seconds.
- **Load shedding** — under overload, shed dashboard/reporting traffic first; **never** shed the
  gate path. Priority-aware rate limiting.

---

## 16. Scaling: 1 Lot → 10,000 Lots

| Dimension | Approach |
|---|---|
| **App tier** | Fully stateless → autoscale on RPS/CPU; no sticky sessions |
| **DB writes** | Shard by `lot_id` (hash). Natural boundary: **no cross-shard transactions** in the hot path |
| **DB reads** | Read replicas for reporting; hot reads from Redis |
| **Hot lot (stadium)** | The shard is per-lot anyway; within a lot, `SKIP LOCKED` gives parallelism. If still hot: split the lot's spots into logical sub-pools per gate |
| **Availability reads** | Redis + short-TTL CDN/edge caching for public browse; `asOf` tells clients the staleness |
| **Geo search ("lots near me")** | Separate read model: Elasticsearch/PostGIS or Redis GEO, fed off Kafka. Never geo-query the transactional DB |
| **Cold data** | Tickets > 90 days → S3/Glacier via a nightly job; queries route to Athena |
| **Multi-region** | Lots are geo-pinned → **partition by region**, not active-active replication. Avoids conflict resolution entirely |
| **Tenancy** | `operator_id` on every row + row-level security; noisy-neighbor protection via per-tenant rate limits |

> **Key insight to voice:** "Parking is inherently geo-partitioned. A car in Bangalore will never
> park in Seattle. So I get near-perfect sharding for free and I should exploit it rather than
> build a globally-consistent system I don't need." Recognizing that the *domain* gives you the
> partition key is a strong senior signal.

---

## 17. Observability & Operations

- **RED metrics** per endpoint: Rate, Errors, Duration (p50/p90/p99/p999).
- **Business metrics** (these page you before customers complain):
  `occupancy_rate` by lot, `allocation_failure_rate`, `avg_gate_open_latency`,
  `unpaid_exit_rate`, `sensor_ticket_mismatch_count`, `revenue_per_spot_per_day`.
- **Distributed tracing** with `ticket_id` as a correlation ID across every hop —
  support can answer "why didn't my gate open at 9:03?" in one query.
- **Alerts that matter:** gate offline > 60 s, allocation failure rate > 1%, payment
  gateway error rate > 5%, replication lag > 5 s, outbox backlog growing.
- **Structured JSON logs**; **never log full plate numbers or card data**.
- **Runbooks + game days**; feature flags for kill-switching new pricing rules instantly.
- **Canary / blue-green deploys** — never deploy to all gates at once.

---

## 18. Security & Privacy

- **License plates are PII** (GDPR/CCPA). Store `plate_hash` (HMAC with a KMS-held pepper) for
  lookups, and `plate_encrypted` (envelope encryption) for display. Support right-to-erasure.
- **No PAN storage** — tokenize via the gateway; keeps us in PCI-DSS SAQ-A scope.
- **AuthN/Z**: OAuth2/OIDC for users; **mTLS + device certs for gates** (a gate is an untrusted
  edge device on someone else's network).
- **Rate limiting** per IP/device/tenant; a compromised gate must not be able to mint infinite tickets.
- **Least privilege IAM**; secrets in KMS/Secrets Manager, rotated.
- **Immutable audit log** of every override (attendant manual open, lost ticket, fee waiver) —
  the top internal fraud vector in parking.
- **Anti-fraud**: detect plate cloning (same plate in two lots simultaneously), abnormal
  attendant override rates.

---

## 19. Edge Cases & Tricky Scenarios

Have ~10 ready; drop them in when there's a lull. It shows breadth.

1. **Car parks in the wrong spot** → sensor mismatch → violation + notify driver.
2. **Oversized vehicle occupies two spots** → mark both `OCCUPIED`, price as a multiplier.
3. **Vehicle exits without paying / tailgates** → `EXITED_UNPAID`, plate-based collection.
4. **Lost ticket** → identify by plate + entry-time camera log; else flat max-day penalty.
5. **Car never leaves (abandoned, 30+ days)** → escalation workflow → tow.
6. **Entry gate opens but the car reverses out** → no exit event. TTL sweep: no sensor activity +
   no exit in N hours → auto-void and free the spot.
7. **Motorcycle in a car spot** → allowed by policy but wasteful; allocation strategy penalizes it.
8. **Reservation no-show** → hold expires (TTL), spot released, cancellation-fee policy.
9. **Reserved spot occupied by someone else on arrival** → relocate + comp; that's why we keep a
   small **buffer of unsold spots (overbooking guard)**.
10. **Power failure at the lot** → gates fail-open (safety/fire code!) + local UPS on the controller.
11. **Two cameras double-fire on one car** → dedupe window on `(plate, gate, 60 s)`.
12. **Clock skew / DST rollover** → server-side epoch timestamps for all billing.
13. **Handicapped spot abuse** → permit validation at entry, not exit.
14. **EV finishes charging but stays** → idle fee after the charge session ends.
15. **Attendant fraud** → every override audited and rate-monitored.

---

## 20. Likely Interviewer Questions + Model Answers

**Q: Two cars arrive at the last spot at the same instant. Walk me through it.**
> Both requests hit different app servers. Both run the atomic conditional `UPDATE ... WHERE
> status='FREE' ... FOR UPDATE SKIP LOCKED`. The DB serializes at the row level: one transaction
> wins and gets the row back; the other's inner SELECT skips the locked row, finds nothing, and
> returns zero rows → `LOT_FULL` with nearby-lot suggestions. No application-level lock, no
> ZooKeeper, one round trip. It's correct because the check-and-set is a single atomic operation
> in the storage engine, not a read followed by a write.

**Q: Why not just `synchronized` / a Java lock?**
> It only protects one JVM. We run N stateless app servers behind a load balancer, so the moment
> we scale past one instance it silently breaks — and it fails in the worst way: intermittently,
> under peak load, in production. Correctness has to live where the shared state lives.

**Q: Why not a distributed lock in ZooKeeper/etcd/Redis?**
> It's a valid option but adds a component, a failure mode, and 5–20 ms per request for something
> my database already does correctly and atomically. I'd only reach for a distributed lock if the
> critical section spanned multiple datastores. Also, Redis-based locks have known safety caveats
> under failover, so I'd never make one the sole guardian of money or physical resources.

**Q: SQL or NoSQL?**
> SQL for the transactional core: the allocation touches spot + ticket + outbox and must be
> atomic; 2,000 write TPS is comfortably within a sharded Aurora setup; and `lot_id` is a perfect
> shard key with no cross-shard transactions. I'd pick DynamoDB if the requirement were global
> active-active or truly unbounded scale, using `TransactWriteItems` with `ConditionExpression`.
> Both work — the deciding factor is whether we need rich queries and easy transactions (SQL) or
> global scale and operational simplicity (Dynamo).

**Q: How do you keep availability counts accurate with caching?**
> Counts are a **derived, eventually-consistent** view, and I make that explicit in the API via
> `asOf`/`staleBySeconds`. They're updated by a consumer reading spot events from Kafka (fed by
> the transactional outbox, so no dual-write bug), plus a full recompute from the DB every 5
> minutes to correct any drift. Crucially, **allocation never trusts the cache** — it always
> does the atomic DB claim. So a stale count can at worst cause one optimistic driver to be told
> `LOT_FULL` at the gate; it can never cause a double-allocation.

**Q: How does the system behave if the cloud is unreachable from the lot?**
> The gate controller runs in offline mode: it holds a pre-allocated block of spot IDs and a
> local monotonic ticket sequence, keeps admitting cars, and queues events locally. On reconnect
> it replays the queue and we reconcile. I'm consciously choosing availability over consistency
> here because a physically blocked entrance is a far worse customer outcome than a temporarily
> inaccurate spot count — and the inaccuracy is bounded and self-healing.

**Q: How do you prevent double-charging?**
> Every mutating API requires an `Idempotency-Key`. We persist `key → response` in an idempotency
> table within the same transaction; a replay returns the stored response instead of re-executing.
> Payments additionally use the gateway's own idempotency key derived from `ticketId`. And the
> ticket state machine only permits `PAYMENT_PENDING → PAID` once.

**Q: 10x traffic tomorrow. What breaks first?**
> Reads, but they're cache-served, so the first real pressure is DB connections and the write path
> on hot shards. Order of action: (1) scale the stateless tier — free; (2) add read replicas and
> raise cache TTLs; (3) add PgBouncer pooling; (4) resplit hot shards. What does *not* need to
> change is the design, because `lot_id` partitioning is linear. The one thing I'd watch is a
> single mega-lot at a stadium — that's a genuine hotspot, and I'd handle it with per-gate spot
> sub-pools.

**Q: How would you test this?**
> Unit tests for pricing (pure function — property-based tests around DST and day-max),
> in-memory-repository tests for allocation strategies, integration tests with a real Postgres via
> Testcontainers, and specifically a **concurrency test that fires 100 threads at the last spot
> and asserts exactly one wins**. Plus contract tests for the payment gateway, chaos/game-day
> tests for gate offline mode, and load tests replaying a stadium-egress profile.

**Q: What would you build first / what's the MVP?**
> Single-lot, walk-in only, hourly flat rate, QR ticket, one Postgres, no Redis, no Kafka —
> notifications via a direct call behind an interface. That validates the core allocation and
> billing loop in weeks. Then I'd add the outbox + Kafka when notifications and analytics arrive,
> and Redis when availability read load justifies it. **I'd resist building the 10k-lot
> architecture on day one** — it's the classic over-engineering trap.

**Q: What's the weakest part of your design?**
> (Always have an honest answer — it reads as self-aware, not weak.) The offline gate mode. It's
> the most complex piece, the hardest to test, and the reconciliation logic is where subtle
> revenue bugs hide. I'd invest disproportionately in its test coverage and add a daily
> reconciliation report with alerting on any mismatch.

---

## 21. Trade-off Summary Table

| Decision | Chosen | Alternative | Why |
|---|---|---|---|
| Allocation consistency | Strong (atomic DB claim) | Eventual + compensate | A physical spot can't be "compensated" — the car is already there |
| Availability reads | Eventual (cached) | Strong | 100:1 read ratio; staleness is harmless and disclosed via `asOf` |
| Store | Sharded RDBMS | DynamoDB | Multi-row ACID in hot path; 2k TPS doesn't need NoSQL scale |
| Locking | `FOR UPDATE SKIP LOCKED` | Distributed lock | No extra component; non-blocking; linear parallelism |
| Events | Transactional outbox → Kafka | Direct publish | Eliminates dual-write inconsistency |
| Notifications | Async, at-least-once + idempotent | Sync inline | Vendor outage must never block a gate |
| Gate offline | Available (AP) | Consistent (CP) | Blocked entrance > temporary count drift |
| Payment failure at exit | Open gate, collect later | Block gate | Customer Obsession; bad debt < blocked exit ramp |
| Multi-region | Geo-partitioned | Active-active | Domain is geo-local; avoids conflict resolution entirely |

---

## 22. Amazon-Specific: Leadership Principles in a Design Round

Amazon grades LPs **inside** the technical round. Weave these in naturally — never announce
"this demonstrates Customer Obsession."

| LP | How to show it here |
|---|---|
| **Customer Obsession** | Open the gate when payment fails; return nearby lots on `LOT_FULL`; latency budget framed as "the driver is stopped at a barrier" |
| **Ownership** | Talk about on-call, runbooks, alerts, reconciliation jobs, and archiving costs — not just the happy path |
| **Invent and Simplify** | `SKIP LOCKED` instead of a whole distributed-lock service; "the domain gives me the shard key for free" |
| **Are Right, A Lot** | Give trade-offs with data (the 100:1 ratio, the 1 GB working set), not opinions |
| **Dive Deep** | Know *why* `SKIP LOCKED` works, what RPO=0 costs in commit latency, why exactly-once is a myth |
| **Bias for Action** | Propose an MVP and a staged rollout instead of designing all 10k lots on day one |
| **Frugality** | Cache-first reads, tiered storage to Glacier, "don't add Kafka until it's earned" |
| **Deliver Results** | Phase the plan: MVP → multi-lot → reservations → dynamic pricing |
| **Highest Standards** | Idempotency on every mutation, immutable audit log, concurrency tests |
| **Disagree and Commit** | If the interviewer pushes NoSQL: "Here's my concern — multi-row atomicity. If we accept sagas for that, Dynamo works and here's how I'd do it." Then commit and design it. |

---

## 23. Interview Best Practices & Anti-Patterns

### Do
- ✅ **Drive the interview.** Announce your structure, then follow it. Interviewers reward candidates they don't have to steer.
- ✅ **Think out loud constantly.** An unspoken thought earns zero.
- ✅ **State assumptions explicitly** and write them down — they become your defense later.
- ✅ **Always give the trade-off, never just the answer.** "X, because Y; the cost is Z; I'd revisit if W."
- ✅ **Number your requirements** (FR1, NFR3) so you can point back: "this satisfies NFR2."
- ✅ **Watch the clock.** Don't spend 25 minutes on the class diagram and never reach fault tolerance.
- ✅ **Read the interviewer.** If they say "let's talk about concurrency," drop everything and go there — that's the rubric item they need to fill.
- ✅ **Draw. Keep it readable.** Boxes and arrows, left-to-right, label every arrow.
- ✅ **Admit what you don't know**, then reason from first principles. "I haven't operated Redlock in prod, but the failure mode I'd worry about is X."
- ✅ **Ask "should I go deeper here or move on?"** at each section boundary.
- ✅ **Close with a summary**: design in 3 sentences + biggest risk + what you'd build first.

### Don't
- ❌ Start drawing boxes in the first 60 seconds.
- ❌ Design for 10,000 lots when they asked for one garage (**over-engineering is a documented downlevel reason**).
- ❌ Name-drop Kafka/K8s/microservices without justifying each one. Every component you add, you must defend.
- ❌ Say "we'll just use a lock" and move on. Say **which** lock, **where**, and **why**.
- ❌ Ignore failure modes — the happy path is table stakes.
- ❌ Use `double` for money, `synchronized` for distributed state, or `Singleton` for lot state.
- ❌ Go silent for 30 seconds. Narrate: "I'm weighing two options here…"
- ❌ Argue with the interviewer. Acknowledge, address, then decide.
- ❌ Forget the **operator/admin** persona — it's half the product.

### The 3-sentence close (rehearse this)
> "So: a stateless service tier over a lot-sharded relational store, where the entire correctness
> of the system rests on a single atomic conditional claim on the spot row, with everything
> non-critical — notifications, analytics, availability counts — pushed off a transactional outbox
> into Kafka so it can never block a gate. My hard constraint was a 300 ms p99 gate decision, and
> my consistency line is drawn at the allocation boundary: strong there, eventual everywhere else.
> The riskiest part is the offline gate mode, so that's where I'd concentrate testing, and I'd
> ship a single-lot MVP first to validate the allocation and billing loop."

---

## 24. Whiteboard Time Plan (60 min)

| Time | Activity | Output on the board |
|---|---|---|
| 0–5 | Clarify scope + questions | Scope box, actors |
| 5–8 | Assumptions + capacity | Numbers in the corner |
| 8–14 | FR (P0/P1/out-of-scope) + NFR table | Two numbered lists |
| 14–19 | API sketch | 5–6 endpoints |
| 19–26 | Data model | 6 tables + key indexes |
| 26–36 | HLD boxes-and-arrows | The architecture diagram |
| 36–50 | **Deep dive** (usually concurrency, sometimes reservations or notifications) | The `SKIP LOCKED` query |
| 50–55 | Fault tolerance + latency budget | Failure matrix |
| 55–58 | Summary + biggest risk + MVP | 3-sentence close |
| 58–60 | Your questions for them | — |

**If you're running out of time:** skip the API details and the class diagram; **never** skip
requirements, the deep dive, or fault tolerance. Those carry the most rubric weight.

---

## Appendix A — Minimal Reference Implementation Sketch (Java)

```java
@Service
public class EntryService {

    private final SpotRepository spots;
    private final TicketRepository tickets;
    private final OutboxRepository outbox;
    private final SpotCompatibilityPolicy compatibility;
    private final IdempotencyStore idempotency;

    /** Single short transaction. No external calls inside. */
    @Transactional
    public Ticket park(ParkRequest req) {
        return idempotency.execute(req.idempotencyKey(), () -> {

            tickets.findActiveByVehicle(req.vehicleId())
                   .ifPresent(t -> { throw new VehicleAlreadyInsideException(t.id()); });

            List<SpotType> preferred = compatibility.compatibleSpots(req.vehicle());

            // Atomic claim: SELECT ... FOR UPDATE SKIP LOCKED + UPDATE in one statement.
            Spot spot = spots.claimFirstFree(req.lotId(), preferred)
                             .orElseThrow(() -> new LotFullException(req.lotId()));

            Ticket ticket = Ticket.issue(req.lotId(), spot.id(), req.vehicleId(),
                                         req.gateId(), Instant.now());
            tickets.save(ticket);

            // Same transaction => no dual-write bug. Relay publishes to Kafka after commit.
            outbox.append(new SpotOccupied(spot.id(), ticket.id(), ticket.enteredAt()));

            return ticket;
        });
    }
}
```

```java
@Transactional
public Receipt unpark(UnparkRequest req) {
    Ticket t = tickets.findByIdForUpdate(req.ticketId());
    t.requireStatus(PAID, EXITED_UNPAID);          // state machine guards the transition

    spots.release(t.spotId(), t.id());             // conditional: WHERE current_ticket_id = ?
    t.markExited(Instant.now(), req.gateId());
    tickets.save(t);
    outbox.append(new SpotFreed(t.spotId(), t.id()));
    return Receipt.from(t);
}
```

## Appendix B — One-Page Cheat Sheet

```
CLARIFY   single lot vs platform? reservations? payments? specific spot? scale? SLA?
ASSUME    10k lots · 4M spots · 2k write TPS · 50k read QPS · 100:1 R:W · p99 300ms
FR        entry · exit · allocate · availability · price · ticket lifecycle · admin
          (+reserve, notify, passes, enforcement)  (−valet, ML pricing, navigation)
NFR       99.99% gate · p99 300ms · strong@allocation, eventual@browse · RPO 0 · idempotent
DATA      lot/level/spot/ticket/ticket_event/reservation/payment/idempotency/outbox
          shard by lot_id · Redis for counters+holds
CORE      UPDATE spot SET status='OCCUPIED' WHERE id=(SELECT ... FOR UPDATE SKIP LOCKED)
EVENTS    outbox (same txn) → relay → Kafka → cache updater / notifications / analytics
NOTIFY    async · at-least-once + idempotent consumer · prefs + quiet hours · DLQ · rate limit
FT        gate offline mode · circuit breakers · multi-AZ · reconciliation sweeps · fail-open gates
LATENCY   cache-first reads · 1 DB round trip · no external calls in txn · regional deploy
CLOSE     3-sentence summary + biggest risk (offline mode) + MVP first
```
