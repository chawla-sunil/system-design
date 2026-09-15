# Design a Top-N Leaderboard over Rolling Windows (LinkedIn Article Shares)

**Round:** Software Design & Architecture — Senior SWE (~5 YoE)
**Format:** 45 minutes, whiteboard / virtual canvas
**Problem statement:** *People regularly share articles on LinkedIn. Design a system to maintain a Top-N leaderboard of the most-shared articles over rolling windows of 5 minutes, 1 hour, and 24 hours.*

---

## 0. How to Run the 45 Minutes

Drive the round. Open with the plan out loud:

> "I'll take ~5 minutes on requirements and assumptions, ~4 on scale, then sketch the architecture, and spend the bulk of the time on the windowing and Top-N algorithm since that's where the real difficulty is. Stop me if you'd rather go deeper somewhere else."

| Phase | Budget | Cumulative | Goal |
|---|---|---|---|
| 1. Clarify requirements | 5 min | 0:05 | Remove ambiguity, write assumptions on the board |
| 2. Scale estimates | 4 min | 0:09 | Derive the *design target*, not trivia |
| 3. API + data model | 3 min | 0:12 | Contract first |
| 4. High-level architecture | 8 min | 0:20 | One diagram, named components |
| 5. **Deep dive** (windowing + Top-N) | 15 min | 0:35 | This is the round |
| 6. Failures, scale, abuse, ops | 7 min | 0:42 | Senior signal lives here |
| 7. Wrap-up & trade-offs | 3 min | 0:45 | "v1 vs v2, what I'd cut, what I'd monitor" |

**Rules of thumb**
- Keep the assumptions list visible on the board the whole time — refer back to it.
- Never leave a choice unjustified. Every box gets a one-line "why this and not that."
- If you're running long, compress §6 — never compress §5.
- Leave the last 3 minutes for scoping judgement. It reads as seniority more than any algorithm does.

---

## 1. Clarifying Questions (5 min)

Ask these; do not assume silently.

| Question | Why it matters |
|---|---|
| **Exact or approximate ranking?** Does #97 vs #98 matter? | Decides exact hashmaps vs Count-Min Sketch |
| **True sliding window, or bucketed?** Is a 5m window that advances every 5s acceptable? | Bucketing is the single biggest memory/CPU saver |
| **What counts as a "share"?** Raw events or **distinct users**? | Distinct counting ⇒ HLL / sets, not integers |
| **Segmentation?** One global board, or per-country / per-topic / per-language? | Multiplies state by segment cardinality |
| **What is N?** | N=100 vs N=1M are different systems |
| **Freshness SLA per window?** | Drives emit cadence and cost |
| **Who reads it?** Feed module (huge QPS) or internal dashboard? | Decides caching/CDN tier |
| **Do deletes/unshares exist?** Do they retroactively decrement? | Affects idempotency and correction path |

### Assumptions I'd State and Write Down

1. **Bucketed sliding windows** are acceptable (stated error bound per window, see §5b).
2. **N = 100**, exact ordering required within the Top-100; tail may be approximate.
3. Scope: **global board + ~50 segments** (country / topic).
4. Freshness: 5m→ ~1s, 1h→ ~10s, 24h→ ~60s stale is acceptable.
5. Counting **raw share events**, deduped by `event_id`. (Distinct-user variant covered in §5f.)
6. Unshares are rare and ignored in v1; recorded as negative events in v2.

### Requirements Summary

**Functional**
- Ingest share events.
- Serve Top-N most-shared articles for windows {5m, 1h, 24h}, per segment.
- Expose the freshness (`as_of`) of every answer.

**Non-functional**
- Availability > strict consistency: a slightly stale board beats an error.
- Bounded, predictable memory. Horizontally scalable.
- Correct under restarts, rebalances, and out-of-order events.
- Resistant to bot/share-ring gaming.

---

## 2. Back-of-the-Envelope (4 min)

```
MAU                      ~300 M
Shares are rare          ~10 M shares/day
Average write rate       10M / 86400        ≈ 120 events/s
Peak (10x diurnal)       ≈ 1.5 K events/s
Design target (10x hdrm) ≈ 50 K events/s          <-- size for this
Event size               ~200 B  ⇒ 10 MB/s at target

Distinct articles shared in 24 h   ~10 M
Counter state                      10M × ~60 B ≈ 600 MB   (sharded, fits in memory)
Per-bucket state (5 min buckets)   far smaller

Read side: one answer = ~10 KB blob per (window, segment)
           150 combos × 10 KB ≈ 1.5 MB total hot set
```

**The framing sentence to say out loud:**

> "This is a **write-side aggregation problem**. The read side is a tiny, trivially cacheable blob — even 500K QPS is a CDN problem, not a compute problem. So all my design effort goes into the streaming aggregation."

That one line reframes the whole interview and shows you found the actual hard part.

---

## 3. API & Data Model (3 min)

### Write path (internal, async)

```
POST /internal/v1/share-events
{
  "event_id":   "uuid",        // idempotency key
  "article_id": "urn:li:article:123",
  "actor_id":   "urn:li:member:456",
  "ts":         1736420000123, // event time, client/edge stamped
  "locale":     "en-IN",
  "topic":      "engineering"
}
→ 202 Accepted   (produced to Kafka; never blocks the user's share)
```

### Read path (public)

```
GET /v1/leaderboard?window=5m|1h|24h&segment=global&n=100

200 OK
Cache-Control: public, max-age=5, stale-while-revalidate=30
{
  "window":  "5m",
  "segment": "global",
  "as_of":   "2026-09-12T08:31:05Z",   // NON-NEGOTIABLE: expose staleness
  "version": 84412,                     // monotonic snapshot version
  "entries": [ {"rank":1, "article_id":"...", "count":15234}, ... ]
}
```

Design notes worth saying:
- Ingestion is **fire-and-forget into Kafka** — the leaderboard must never be on the share critical path.
- `as_of` + `version` let clients detect staleness and avoid rendering a rolled-back board.
- Snapshots are **immutable and versioned**, so reads are lock-free and cacheable.

### Core state (per aggregator task, in memory + RocksDB)

```
totals[window]  : map<article_id, long>            // rolling sum
buckets[window] : ring of B × map<article_id, long> // per-slot deltas
topK[window]    : size-K min-heap  (K ≈ 10 × N)
```

---

## 4. High-Level Architecture (8 min)

```
  Share Service
       │  (async, 202)
       ▼
  ┌──────────────────────────────────────────┐
  │ Kafka topic: article.shares              │
  │ key = ARTICLE_ID   (critical — see §5a)  │
  │ retention ≥ 25 h  (> largest window)     │
  └───────────────────┬──────────────────────┘
                      │
        ┌─────────────▼──────────────┐
        │ Anti-abuse / trust filter  │   drop bots, cap per-user contribution
        └─────────────┬──────────────┘
                      │
        ┌─────────────▼──────────────────────────────┐
        │ Aggregator (Flink / Kafka Streams)         │
        │  1 task per partition, RocksDB state       │
        │  • ring buffers for 5m / 1h / 24h          │
        │  • local Top-K heap per window             │
        │  • emits local Top-K on a per-window tick  │
        └─────────────┬──────────────────────────────┘
                      │ N × local Top-K
        ┌─────────────▼──────────────┐
        │ Merger                     │  merge N heaps → global Top-N snapshot
        │ (single task per segment)  │  write versioned, immutable blob
        └─────────────┬──────────────┘
                      ▼
        ┌────────────────────────────┐
        │ Snapshot store (Redis/S3)  │
        └─────────────┬──────────────┘
                      ▼
        Leaderboard API ──► CDN / edge cache ──► clients
```

Component one-liners:
- **Kafka** — durable buffer, replay source for recovery, natural sharding by key.
- **Aggregator** — stateful stream operator; owns counting and windowing.
- **Merger** — cheap fan-in; combines partial Top-Ks into the global answer.
- **Snapshot store + CDN** — absorbs all read traffic; aggregators never serve reads.

---

## 5. Deep Dive — The Part That Earns the Offer (15 min)

### (a) Partition by `article_id` — and prove why

This is **the** decision in this problem. State it and justify it.

If you partition by user ID or round-robin, an article's count is **split across nodes**. Then merging per-node Top-Ks is **unsound**: an article that sits at rank #200 on every one of 50 nodes never appears in any local Top-K, yet could be #1 globally.

Partitioning by `article_id` gives you: **one task owns the complete count for an article.**

> **Correctness argument:** if article A is in the global Top-N, its full count lives on exactly one partition. Within that partition, no other article can outrank A more times than it does globally. Therefore A is in that partition's Top-N. So the union of per-partition Top-Ns is a **superset** of the global Top-N, and merging them is **exact**.

Take `K ≈ 10 × N` per partition purely as safety margin for ties and late updates — the proof only needs `K ≥ N`.

Most candidates merge local Top-Ks without justifying it. Proving it is the differentiator.

### (b) Rolling windows as bucketed ring buffers

Storing timestamped events per article so you can expire them precisely is too expensive (memory grows with *event count*). Instead, per window keep a ring of buckets:

| Window | Buckets | Granularity | Worst-case boundary error |
|---|---|---|---|
| 5 min | 60 | 5 s | ±5 s (1.7 %) |
| 1 hour | 60 | 1 min | ±1 min (1.7 %) |
| 24 hours | 288 | 5 min | ±5 min (0.35 %) |

Each bucket is `map<article_id, delta>`; plus `totals = map<article_id, count>` holding the rolling sum.

```
on_event(a, ts):
    totals[a]     += 1
    bucket[slot(ts)][a] += 1                      # O(1)

on_bucket_roll(expiring):                          # every granularity tick
    for (a, d) in expiring:
        totals[a] -= d
        if totals[a] == 0: delete totals[a]        # keep memory tight
    expiring.clear()
```

Properties to call out:
- **O(1) amortized per event.** The roll cost is bounded by one bucket's size, and every increment is removed exactly once ⇒ amortized O(1).
- **Memory is bounded by distinct articles in the window, not event volume.** 24h dominates; ~600 MB sharded across tasks.
- **One pass, three ring buffers** — do *not* design three separate pipelines. Same event updates all three windows, only the emit cadence differs.

### (c) Maintaining Top-K without re-sorting every tick

Naive: scan `totals` each tick with a size-K min-heap — O(M log K).

- **5m window:** small cardinality; just do the full scan. It's a few milliseconds, exact, and simple. Don't over-engineer.
- **1h / 24h windows:** millions of keys; a full scan every second is wasteful. So:
  - Maintain the size-K min-heap incrementally with threshold `T = heap.min()`.
  - On increment, if `totals[a] ≥ T`, insert/update the heap entry.
  - **Decrements from bucket expiry can make heap entries stale**, so do a **full rebuild on a slower cadence**.

| Window | Emit cadence | Full heap rebuild | Justification |
|---|---|---|---|
| 5 min | 1 s | every tick (cheap) | small state, freshness matters most |
| 1 hour | 10 s | every 10 s | moderate state |
| 24 hours | 60 s | every 60 s | window moves slowly; 60s stale is within SLA |

Tune cadence **per window** — a single global number is a smell.

### (d) The approximation lever (mention it, then consciously decline it)

If cardinality exploded to billions of keys, swap exact maps for:
- **Count-Min Sketch** — fixed memory, one-sided over-count error `ε·total` with probability `1-δ`.
- **Space-Saving / Misra–Gries** — bounded-memory heavy hitters, guaranteed to contain all true top-K above a frequency threshold.

At LinkedIn article cardinality (~10M/day), exact hashmaps fit comfortably, so **I'd choose exact and document the sketch as a scaling lever.**

> Showing you know the sketch *and* chose not to use it is a stronger signal than reaching for it reflexively.

### (e) Hot keys (a viral article makes one partition hot)

1. **Producer-side combiner (do this first — cheap, huge win):** each producer instance locally counts and flushes `(article_id, count, second)` every 1s. Collapses a 10K/s hot key into 1 message/s *per producer*. The aggregator adds `count` instead of `1` — same code path.
2. **Salted sub-keys** for detected hot keys: `article_id#0..15` with a second merge stage. Adds a stage, so apply selectively via hot-key detection, not globally.
3. Kafka partition count sized so a single hot partition still fits one task's budget.

### (f) Variant: distinct-user counting

Raw counts are trivially gameable. If the requirement is *distinct sharers*:
- Per-bucket, per-article **HyperLogLog** (~1.5 KB for 0.8 % error).
- Window total = **union of the live buckets' HLLs** — recomputed on roll.
- Trade-off to name: HLLs are **unionable but not subtractable**, which is fine here precisely because we *merge live buckets* rather than *subtract expired* ones. That's why the ring-buffer shape still works.
- Cost: 1.5 KB × articles × buckets is much larger than integers — so apply HLL only to the current Top-M candidates, exact counters for ranking below.

---

## 6. Correctness, Failures & Operations (7 min)

### Event time vs processing time
- Aggregate on **event time** with watermarks and bounded lateness (~30 s).
- Events later than the watermark: fold into the current bucket **and** increment a `late_events` metric. **Never silently drop.**
- Extremely late events (> window) are dropped with a counter — say this explicitly.

### Idempotency & exactly-once
- Dedup on `event_id` via a short-TTL Redis set or rotating Bloom filter (TTL > max expected retry window).
- Kafka **idempotent producer** + **transactional sink** ⇒ effectively-once within the pipeline.
- Snapshot writes are versioned and idempotent — replaying a version is a no-op.

### State & recovery
- RocksDB-backed operator state, periodic **checkpoints to S3**.
- **Kafka retention ≥ largest window (25 h for a 24 h board).** This is the key connection: a cold node with no checkpoint must **replay 24 h of events** to rebuild the longest window. Retention *is* your recovery bound.
- On rebalance, the new partition owner restores from the latest checkpoint and replays only the delta.
- Recovery time estimate: 10M events at 200 MB/s replay ⇒ minutes, not hours. Say the number.

### Serving degradation
- If aggregators die, **keep serving the last good snapshot** with an honest `as_of`. A stale leaderboard beats a 500.
- Add a client-visible `stale: true` flag past a threshold, so UI can soften the presentation.
- Never let the read path depend on aggregator liveness.

### Monitoring (name the SLI, not just "add metrics")
| Signal | Why |
|---|---|
| **Snapshot age per window** | The real SLI — directly maps to the freshness SLA |
| Consumer lag per partition | Early warning before snapshot age moves |
| Late-event rate | Clock skew / upstream problems |
| Leaderboard churn rate (rank turnover per tick) | Sudden full turnover = bug or attack |
| Heap rebuild duration, state size per task | Capacity planning |

### Abuse & product thinking (raise this unprompted)
- Bot share-rings will farm any public leaderboard. Put the trust/anti-abuse filter **before** aggregation, not after.
- **Cap per-user contribution** per article per window (e.g. 1).
- **Weight by account reputation** (age, connection graph, prior abuse signals).
- Consider a **shadow board** with unfiltered numbers for the integrity team to compare against.

---

## 7. Alternatives Considered (3 min)

| Approach | Verdict |
|---|---|
| **Redis ZSETs** — one ZSET per minute bucket, `ZUNIONSTORE` over the window | **Great day-1 build.** ~50 lines of code. Breaks at 24 h: 1440 buckets, blocking O(N log N) union on a single-threaded server. Salvageable by capping each bucket to top-1000 — but that makes it approximate. |
| **Apache Pinot / Druid**, query at read time | **Strong buy-vs-build option.** Real-time Kafka ingestion, `SELECT article_id, COUNT(*) ... WHERE ts > now()-24h GROUP BY article_id ORDER BY 2 DESC LIMIT 100`. Far less custom code. Cost: read-time compute and a new system to operate. **I'd genuinely propose this if the org already runs one.** |
| **Batch (Spark, hourly)** | Fails the 5-minute window outright. Non-starter. |
| **Compute at read time from raw events** | Impossible: read QPS × full scan cost. |
| **Per-article row in a DB with `UPDATE ... SET count = count+1`** | Write amplification + hot-row contention on viral articles. Also can't express rolling expiry. |

### What I'd actually ship

- **Week 1:** Redis ZSET version for 5m and 1h only. Proves product value, minimal cost.
- **Week 4:** add 24 h — this is where ZSETs start hurting, so migrate to the Flink ring-buffer design.
- **Later:** distinct-user counting, segments, anti-abuse weighting.
- **Deferred deliberately:** sketches (not needed at this cardinality), salted hot keys (producer combiner likely suffices).

---

## 8. Cheat Sheet

```
FRAMING      Write-side aggregation problem; read side is a cacheable 10 KB blob.

KEY DECISION Partition Kafka by ARTICLE_ID.
             ⇒ one node owns an article's full count
             ⇒ global Top-N ⊆ union of per-partition Top-Ns
             ⇒ merging local Top-Ks is EXACT.  (state this proof)

WINDOWS      Ring buffer of buckets per window, one pass over the stream.
             5m: 60×5s | 1h: 60×1m | 24h: 288×5m
             totals[a] += 1 on event; totals[a] -= d on bucket expiry
             O(1) amortized, memory ∝ distinct articles (not events)

TOP-K        5m  → full scan + min-heap each tick (exact, simple)
             1h  → incremental heap, rebuild every 10 s
             24h → incremental heap, rebuild every 60 s

SCALE LEVERS producer-side combiner (hot keys) → salted sub-keys
             exact maps → Count-Min Sketch + Space-Saving (only if cardinality explodes)
             distinct users → per-bucket HLL (unionable, not subtractable)

OPS          event time + watermarks, bounded lateness, late_events metric
             event_id dedup, idempotent producer, transactional sink
             RocksDB + S3 checkpoints; KAFKA RETENTION ≥ LARGEST WINDOW
             serve last good snapshot with honest as_of; SLI = snapshot age
             anti-abuse BEFORE aggregation; cap per-user contribution

BUILD ORDER  Redis ZSET (v1) → Flink ring buffers (v2) → HLL/segments (v3)
```

---

## 9. Common Follow-up Questions

**Q: Why not just use a sliding window with exact timestamps?**
Memory grows with event count instead of distinct articles, and expiry becomes O(events). Bucketing trades a bounded, stated error (±1 bucket) for O(1) amortized cost and bounded memory. I'd state the error in the API docs.

**Q: What if the interviewer insists on an exact sliding window?**
Shrink the bucket granularity until the error is below the product's tolerance (e.g. 1s buckets for the 5m window = 300 buckets, still cheap). Exactness here is a granularity dial, not a different architecture.

**Q: How do you handle an unshare / delete?**
Emit a `-1` event with the same `article_id`. It decrements `totals` and the current bucket. Risk: the original `+1` may have already expired, driving a count negative — clamp at zero and emit a correction metric.

**Q: Two articles tie at rank 100. What happens?**
Define a deterministic tiebreaker (e.g. lexicographic `article_id`, or earliest first-seen timestamp) so the board is stable across rebuilds and doesn't flicker. Non-deterministic ties are a real UX bug.

**Q: How do you add per-country boards without 50× the cost?**
Key by `(segment, article_id)`. State scales with *observed* (segment, article) pairs, not the cross product — most articles are concentrated in a few locales. Also consider only maintaining segment boards for segments above a traffic threshold.

**Q: Merger is a single point of failure — isn't that bad?**
It's a stateless fan-in over N small heaps (N × K × ~30 B ≈ a few MB). Run active/standby with leader election; failover costs one tick. Snapshots are versioned and immutable so a double-write is harmless.

**Q: What happens on a Kafka partition count change?**
Re-partitioning breaks the "one node owns the article" invariant during the transition. Handle it as a planned migration: drain, checkpoint, re-key, replay. Or over-provision partitions up front — that's the cheaper answer.

**Q: How do you test this?**
Deterministic replay of a recorded event file through the pipeline, compared against a brute-force in-memory reference implementation. Property tests: ring-buffer sum invariant (`sum(buckets) == totals`), and Top-K equivalence vs a full sort.

**Q: Why Flink rather than Kafka Streams?**
Flink for first-class event-time/watermark support and richer windowing; Kafka Streams if the org is already Kafka-centric and wants fewer moving parts. Either satisfies the design — the ring-buffer logic is identical. Pick whichever the team already operates.

---

## 10. Interviewer Signal Checklist

Things that read as **senior** in this round:

- [ ] Reframed it as a write-side problem in the first 10 minutes.
- [ ] Led with the partitioning choice **and proved** the Top-K merge is exact.
- [ ] Named the trade-off axes explicitly: exact↔approximate, granularity↔memory, freshness↔cost.
- [ ] One pipeline, three ring buffers — not three pipelines.
- [ ] Connected **Kafka retention ≥ largest window** to cold-start replay.
- [ ] Raised **abuse/gaming** unprompted.
- [ ] Defined the SLI (**snapshot age**), not just "we'll add dashboards."
- [ ] Mentioned Count-Min Sketch and **consciously declined it** with a reason.
- [ ] Closed with v1-vs-v2 scoping and what would be cut.

Things that read as **junior**:

- Jumping straight to "Redis sorted set" with no window analysis.
- Merging per-node Top-Ks with no correctness argument.
- Using sketches reflexively where exact counting fits in memory.
- Ignoring event-time vs processing-time entirely.
- Designing three separate pipelines for the three windows.
- No staleness exposed in the API response.
