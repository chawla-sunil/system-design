# Concurrency Control in Distributed Systems — Optimistic & Pessimistic Locking (HLD Interview, ~60 min)

> **Format:** Interviewer (I) / Candidate (C). Simulated 1-hour HLD round for an engineer with 6–7 yrs of experience. Bias is toward **trade-offs, real systems, and production-grade design** — not textbook definitions.

---

## Table of Contents
1. [Clarify the Problem (~5 min)](#1-clarify-the-problem-5-min)
2. [Why Concurrency Control? First Principles (~5 min)](#2-why-concurrency-control-first-principles-5-min)
3. [ACID, Isolation Levels & Anomalies (~7 min)](#3-acid-isolation-levels--anomalies-7-min)
4. [Pessimistic Concurrency Control (PCC) (~10 min)](#4-pessimistic-concurrency-control-pcc-10-min)
5. [Optimistic Concurrency Control (OCC) (~10 min)](#5-optimistic-concurrency-control-occ-10-min)
6. [MVCC — How Real DBs Do It (~5 min)](#6-mvcc--how-real-dbs-do-it-5-min)
7. [Distributed Locking (~8 min)](#7-distributed-locking-8-min)
8. [End-to-End Design: Inventory + Wallet (~5 min)](#8-end-to-end-design-inventory--wallet-5-min)
9. [Anti-patterns & Decision Framework (~3 min)](#9-anti-patterns--decision-framework-3-min)
10. [Cheat Sheet & Follow-up Q&A (~2 min)](#10-cheat-sheet--follow-up-qa-2-min)

---

## 1. Clarify the Problem (~5 min)

**I:** Design concurrency control for a distributed system. Tell me how you'd approach it.

**C:** Before I jump into mechanisms, I want to scope the problem. Concurrency control means different things at different layers, so let me ask:

1. **What's the shared resource?** A single DB row (e.g., wallet balance), a document, a file, an external resource (payment, inventory SKU)?
2. **Single DB or multiple data stores?** Single-node Postgres vs sharded Postgres vs polyglot (Postgres + Redis + Kafka)?
3. **Read/write ratio and contention level?** High-contention (flash sale on 1 SKU) vs low-contention (user profile updates)?
4. **Latency budget?** Sub-100ms (checkout) vs background (analytics)?
5. **Consistency requirement?** Strict serializability (money), read-committed (catalog), eventual (likes counter)?
6. **What anomalies are acceptable?** Lost updates? Phantom reads? Write skew?

**I:** Assume an e-commerce platform — wallet debits, inventory decrements, and order placement. Strict consistency for money & inventory; high contention possible during sales. Scale: 50K TPS peak, sharded Postgres + Redis + Kafka.

**C:** Good. So I'll need:
- **Pessimistic locking** for hot, contended writes (single SKU during flash sale).
- **Optimistic locking** for low-contention writes (user profile, cart updates).
- **Distributed locks** (Redis/ZooKeeper) for cross-resource coordination (e.g., "only one worker can process this order").
- **MVCC** at the DB layer (Postgres gives us this for free).
- **Saga + idempotency** for cross-service transactions (covered in another doc).

Let me start from first principles.

---

## 2. Why Concurrency Control? First Principles (~5 min)

**C:** When multiple transactions touch the same data concurrently, without coordination we get **race conditions**. Classic example — the **lost update**:

```
Initial: balance = 100

T1 (debit 30):  READ 100 ──────────► WRITE 70
T2 (debit 50):           READ 100 ──────────► WRITE 50

Final: balance = 50   ❌  (should be 20)
```

`T2` overwrote `T1`'s update because both read the same snapshot. We lost ₹30.

### Goals of concurrency control
| Goal | Description |
|------|-------------|
| **Correctness** | Final state = some serial execution of the transactions |
| **Liveness** | No deadlocks/livelocks/starvation |
| **Performance** | Maximize throughput, minimize latency |
| **Isolation** | Concurrent txns don't see each other's intermediate state |

### Two philosophies
| Approach | Assumption | Mechanism |
|----------|------------|-----------|
| **Pessimistic** | "Conflicts are likely → prevent them" | Acquire lock **before** touching data |
| **Optimistic** | "Conflicts are rare → detect them" | Check at commit time; retry if conflict |

> **Rule of thumb:** Use OCC when conflict rate < ~10%. Above that, OCC retries waste CPU and you should switch to PCC.

---

## 3. ACID, Isolation Levels & Anomalies (~7 min)

**I:** Before mechanisms, explain isolation levels — that's what concurrency control is really about.

**C:** Right. ANSI SQL defines 4 levels, each preventing more anomalies:

### Anomalies
| Anomaly | Description | Example |
|---------|-------------|---------|
| **Dirty Read** | Read uncommitted data from another txn | T1 writes balance=70 (not committed), T2 reads 70, T1 rollbacks |
| **Non-Repeatable Read** | Re-reading same row returns different value | T1 reads balance=100, T2 commits balance=70, T1 reads again → 70 |
| **Phantom Read** | Re-running same query returns different rows | T1 `SELECT WHERE status='pending'` → 5 rows; T2 inserts; T1 re-runs → 6 rows |
| **Lost Update** | Two txns read-modify-write same row, one overwrites the other | Example above |
| **Write Skew** | Two txns read overlapping data, write disjoint data, violating an invariant | Both doctors on-call check "≥1 doctor on call", both go off-call simultaneously |

### Isolation Levels
| Level | Dirty Read | Non-Repeat | Phantom | Lost Update | Write Skew |
|-------|:---------:|:----------:|:-------:|:-----------:|:----------:|
| **Read Uncommitted** | ❌ Possible | ❌ | ❌ | ❌ | ❌ |
| **Read Committed** (Postgres default) | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Repeatable Read** (MySQL default) | ✅ | ✅ | ⚠️ Sometimes | ✅* | ❌ |
| **Snapshot Isolation** (Postgres "Repeatable Read") | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Serializable** | ✅ | ✅ | ✅ | ✅ | ✅ |

> *MySQL InnoDB Repeatable Read prevents lost updates via gap locks; Postgres Snapshot Isolation throws "could not serialize" error.

### Key takeaway
- **Read Committed** is sufficient for most workloads but does NOT prevent lost updates → you need OCC or PCC explicitly.
- **Serializable** is correct but expensive — only ~30–50% throughput of Read Committed under contention.
- For money/inventory I'd use **Serializable** or **Snapshot Isolation + explicit row locks**.

---

## 4. Pessimistic Concurrency Control (PCC) (~10 min)

**C:** Pessimistic = "lock first, ask questions later." Acquire a lock before reading/writing; release on commit/rollback.

### 4.1 Lock types
| Lock | Compatible with | Notes |
|------|-----------------|-------|
| **Shared (S)** | Other S | For reads |
| **Exclusive (X)** | None | For writes |
| **Update (U)** | S only | Intent-to-update; prevents deadlock on read-then-write |
| **Intention (IS/IX)** | Hierarchical | At table level when row-level lock is held |

### 4.2 Lock granularity
```
Table-level   ─── high concurrency cost, low overhead
   │
Page-level    ─── middle ground (SQL Server)
   │
Row-level     ─── high concurrency, more overhead  ← most common
   │
Predicate-level ─ for phantom prevention (Serializable)
```

### 4.3 Two-Phase Locking (2PL) — the theoretical foundation
```
   Growing phase     Shrinking phase
  ┌──────────────┐  ┌──────────────┐
  │ Acquire locks │  │ Release locks │
  └──────────────┘  └──────────────┘
        │                  │
        └─── No more acquires after first release ───┘
```

- **Strict 2PL** (used by all real RDBMS): release all locks at commit/rollback. Prevents cascading aborts.

### 4.4 SQL example — `SELECT … FOR UPDATE`
```sql
BEGIN;
-- Acquire row-level X lock; blocks other writers
SELECT balance FROM wallet WHERE user_id = 42 FOR UPDATE;
-- Now safe to read-modify-write
UPDATE wallet SET balance = balance - 30 WHERE user_id = 42;
COMMIT; -- locks released
```

Other flavors:
- `FOR UPDATE SKIP LOCKED` → great for work queues; skip rows other workers locked.
- `FOR UPDATE NOWAIT` → fail fast instead of waiting.
- `FOR SHARE` → S-lock, allows concurrent reads but blocks writes.

### 4.5 Deadlocks
```
T1: LOCK row A ─────► wants row B
                            ▲
T2: LOCK row B ─────► wants row A   ❌ Deadlock
```

**Detection:** DB builds a wait-for graph; if cycle → kill the youngest txn (Postgres) and return `deadlock_detected`.

**Prevention strategies:**
1. **Lock ordering** — always acquire locks in same order (e.g., always `min(user_a, user_b)` first in money transfer).
2. **Timeouts** — `SET lock_timeout = '2s'`.
3. **Smaller transactions** — hold locks for as short as possible.
4. **`SELECT … FOR UPDATE NOWAIT`** — fail fast and retry with backoff.

### 4.6 Pros & Cons
| Pros | Cons |
|------|------|
| Simple mental model | Reduced concurrency — readers/writers wait |
| Guarantees no conflicts | Risk of deadlocks |
| Predictable latency under contention | Holds locks across network roundtrips → tail latency |
| Works for long-running txns | Doesn't scale well across shards/services |

### 4.7 When to use PCC
- **High contention** (hot row, flash sale on SKU).
- **Long critical sections** where retry is expensive.
- **Money transfers** (typically pair with Serializable).
- **Work queues** (`SKIP LOCKED`).

---

## 5. Optimistic Concurrency Control (OCC) (~10 min)

**C:** Optimistic = "assume no conflict; detect on commit." No locks held — instead, attach a **version** to each row.

### 5.1 Mechanism — version column
```sql
CREATE TABLE wallet (
  user_id  BIGINT PRIMARY KEY,
  balance  NUMERIC,
  version  BIGINT NOT NULL DEFAULT 0
);
```

**Read-modify-write flow:**
```sql
-- 1. Read with version
SELECT balance, version FROM wallet WHERE user_id = 42;
-- → balance=100, version=7

-- 2. Compute new balance in app (balance - 30 = 70)

-- 3. Conditional update: succeeds only if version unchanged
UPDATE wallet
SET    balance = 70, version = version + 1
WHERE  user_id = 42 AND version = 7;

-- 4. Check rows-affected
--    1 → success
--    0 → conflict → re-read and retry (or fail)
```

This is essentially a **CAS (compare-and-swap)** at the row level.

### 5.2 Alternative: timestamp-based
Use `updated_at` instead of an integer version. Works but vulnerable to clock skew in distributed systems → integer version is safer.

### 5.3 OCC at the transaction level (the 3 phases)
1. **Read phase** — read data, buffer writes locally; no locks.
2. **Validation phase** — at commit, check no concurrent txn modified read-set.
3. **Write phase** — if valid, apply writes atomically; else abort & retry.

This is what **Postgres Serializable Snapshot Isolation (SSI)** does internally and is also how etcd / CockroachDB / Spanner work.

### 5.4 Retry pseudocode
```java
public boolean debit(long userId, BigDecimal amount) {
  for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
    var row = repo.findById(userId);          // read version
    if (row.balance.compareTo(amount) < 0) {
      throw new InsufficientFundsException();
    }
    int updated = jdbc.update("""
        UPDATE wallet
        SET balance = ?, version = version + 1
        WHERE user_id = ? AND version = ?
        """, row.balance.subtract(amount), userId, row.version);
    if (updated == 1) return true;            // success
    sleep(jitterBackoff(attempt));            // conflict → retry
  }
  throw new TooManyRetriesException();
}
```

**Always use exponential backoff + jitter** to avoid retry storms.

### 5.5 Pros & Cons
| Pros | Cons |
|------|------|
| No locks → high concurrency | Wasted work on conflicts |
| No deadlocks | Retries amplify load under contention |
| Better tail latency in low-contention workloads | App-layer retry logic complexity |
| Works across services (just include version in API) | Caller may see "stale state" errors |

### 5.6 When to use OCC
- **Low contention** (user profile, cart, document edits, JIRA tickets).
- **Read-mostly** workloads.
- **Stateless services** that can retry safely.
- **REST/gRPC APIs** — version becomes an `If-Match` ETag header.

### 5.7 HTTP ETag pattern (OCC over the wire)
```http
GET /users/42
→ 200 OK
  ETag: "v7"
  { "name": "Alice", "balance": 100 }

PUT /users/42
  If-Match: "v7"
  { "name": "Alice", "balance": 70 }

→ 200 OK            (if version still 7)
→ 412 Precondition Failed   (if someone else updated)
```

This pushes OCC all the way to the client — beautiful for collaborative apps (Google Docs, Notion, Figma use variants of this with CRDTs/OT).

---

## 6. MVCC — How Real DBs Do It (~5 min)

**I:** Postgres doesn't actually lock on reads. How?

**C:** **Multi-Version Concurrency Control (MVCC).** Instead of locking, the DB keeps multiple versions of each row tagged with transaction IDs (XIDs). Readers see a **snapshot** consistent with their txn start time; writers create new versions.

### How Postgres MVCC works
```
Row v1: xmin=100, xmax=120, balance=100   ← created by txn 100, deleted by txn 120
Row v2: xmin=120, xmax=null, balance=70   ← created by txn 120, still live
```

- **Reader** with snapshot XID=110 → sees v1 (xmin ≤ 110 < xmax).
- **Reader** with snapshot XID=130 → sees v2.
- **Writer** appends a new version; old versions cleaned up by **VACUUM**.

### Benefits
- **Readers never block writers and vice versa.**
- Natural support for snapshot isolation.
- Trade-off: storage bloat, VACUUM overhead, long-running txns block VACUUM.

### MVCC + OCC = Serializable Snapshot Isolation (SSI)
Postgres tracks read-write dependencies between concurrent txns; if a dangerous cycle is detected, one txn is aborted with `could not serialize access`. You then **retry**. That's why Serializable Postgres works well — it's OCC under the hood.

### Comparison
| DB | Default | Mechanism |
|----|---------|-----------|
| Postgres | Read Committed | MVCC |
| MySQL InnoDB | Repeatable Read | MVCC + gap locks |
| Oracle | Read Committed | MVCC |
| SQL Server | Read Committed | Locking (MVCC optional via RCSI) |
| CockroachDB / Spanner | Serializable | MVCC + OCC + global TrueTime/HLC |
| DynamoDB | Read Committed | Conditional writes (OCC) |

---

## 7. Distributed Locking (~8 min)

**I:** What if the resource isn't in a single DB? E.g., you need to ensure only one worker processes a Kafka message, or you need to debit a wallet stored across two services.

**C:** That's where **distributed locks** come in. The DB-local locks don't help — we need cross-process coordination.

### 7.1 Options

| Tool | Approach | Pros | Cons |
|------|----------|------|------|
| **Redis (SETNX)** | `SET key value NX EX 30` | Fast, simple | Not safe under failures (see Redlock debate) |
| **Redlock (multi-Redis)** | Quorum across N Redis | Better fault tolerance | Still has clock-skew issues (Kleppmann critique) |
| **ZooKeeper** | Ephemeral sequential znodes | Strong consistency, leader election | Operationally heavy |
| **etcd** | Lease-based locks via Raft | Strong consistency, modern | Latency higher than Redis |
| **DB row lock** | `SELECT … FOR UPDATE` on a "lock table" | Reuses existing DB | DB becomes bottleneck |
| **Fencing tokens** | Monotonic token validated by resource | Safest pattern | Requires resource cooperation |

### 7.2 Redis SETNX pattern
```python
# Acquire
ok = redis.set("lock:order:123", worker_id, nx=True, ex=30)
if not ok:
    return  # someone else has it

try:
    do_work()
finally:
    # Release safely — only if we still own it
    redis.eval("""
      if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('DEL', KEYS[1])
      end
    """, 1, "lock:order:123", worker_id)
```

**Always use Lua script for release** — avoids the classic race where TTL expires, someone else acquires, and you delete their lock.

### 7.3 The fencing token problem (Kleppmann's argument)
```
Client A acquires lock (token=33)
  ↓ GC pause for 60s, lock expires
Client B acquires lock (token=34)
  ↓ writes to storage with token=34
Client A wakes up, writes with token=33 ← stale! Corrupts data.
```

**Fix:** the protected resource (e.g., storage) must reject any write with token < highest seen. The lock is **advisory**; the resource is the source of truth.

```
Storage checks: incoming_token > last_token? accept : reject
```

ZooKeeper/etcd naturally provide monotonically increasing tokens (zxid/revision). Redis does not — must add your own.

### 7.4 When NOT to use distributed locks
- For **correctness in money transfers** — use DB transactions + idempotency keys instead.
- For **work distribution** — prefer **leader election** (single consumer per partition) or **Kafka consumer groups**.
- For **rate limiting** — use token bucket in Redis directly.

> **Distributed locks are for efficiency (avoid duplicate work), not correctness.** For correctness, rely on the underlying datastore's transactions or idempotency.

### 7.5 Leader election as an alternative
- ZooKeeper: ephemeral sequential znode → smallest = leader.
- Kubernetes: `Lease` object + controller-runtime leader election.
- Kafka: partition assignment → exactly one consumer per partition (effectively per-key serialization).

---

## 8. End-to-End Design: Inventory + Wallet (~5 min)

**I:** Put it together. Design checkout for an e-commerce flash sale.

**C:** Mixed strategy — different mechanism for each resource.

```
┌─────────────────────────────────────────────────────────────────┐
│                       Checkout Service                          │
└─────────────────────────────────────────────────────────────────┘
        │                          │                       │
        ▼                          ▼                       ▼
┌──────────────────┐     ┌───────────────────┐    ┌─────────────────┐
│ Inventory Svc    │     │  Wallet Svc        │    │  Order Svc      │
│ (high contention)│     │  (medium)          │    │  (low)          │
│                  │     │                    │    │                 │
│ Redis decrement  │     │ Postgres           │    │ Postgres        │
│ + Kafka          │     │ SERIALIZABLE       │    │ OCC (version)   │
│ reservation TTL  │     │ + FOR UPDATE       │    │                 │
└──────────────────┘     └───────────────────┘    └─────────────────┘
        │                          │                       │
        └──────► Saga orchestrator (Temporal) ◄────────────┘
                 Idempotency keys per step
                 Outbox + Debezium for events
```

### Per-resource strategy

| Resource | Mechanism | Why |
|----------|-----------|-----|
| **SKU inventory** (1 row, 50K TPS) | **Redis `DECRBY` + reservation TTL** | DB row lock = serialized bottleneck; Redis = single-threaded atomic, 100K+ ops/sec |
| **Wallet** (per-user, medium contention) | **Postgres `SELECT … FOR UPDATE` (PCC) + Serializable** | Money requires strict correctness; per-user contention low |
| **Order** (insert-only) | **OCC with version on update** | Inserts don't conflict; subsequent state transitions rare contention |
| **Saga coordination** | **Temporal workflow + idempotency keys** | Cross-service correctness without 2PC |
| **Worker queue (payment retries)** | **Postgres `FOR UPDATE SKIP LOCKED`** | Multiple workers, one row each, no deadlock |

### Flash-sale hot SKU pattern
Even Redis decrement on a single key becomes a bottleneck around 100K TPS. Solution: **sharded counters**.
```
Total stock = 10,000 → split into 10 buckets of 1,000
Hash user_id → bucket → DECRBY bucket
If bucket empty → try next bucket (gossiped via Redis pub-sub)
```
Trade-off: small fairness loss; massive throughput gain.

### Tying it back to anomalies
- **Lost update** on wallet → prevented by `FOR UPDATE`.
- **Overselling** on inventory → prevented by atomic Redis decrement + conditional check `stock >= 0`.
- **Double-charge** on retry → prevented by idempotency keys (separate doc).
- **Write skew** (rare) → handled at Serializable level.

---

## 9. Anti-patterns & Decision Framework (~3 min)

### 9.1 Common anti-patterns
| Anti-pattern | Why it's bad | Fix |
|--------------|--------------|-----|
| `SELECT count(*); INSERT IF count < N` | TOCTOU race | Unique constraint or `SELECT … FOR UPDATE` |
| `UPDATE WHERE id = ? SET balance = ?` without version | Lost update | OCC (`AND version = ?`) or PCC (`FOR UPDATE`) |
| Application-level locks (`synchronized` in Java) for distributed data | Only protects 1 JVM | Distributed lock or DB lock |
| Long transactions holding locks | Lock contention, blocked VACUUM | Keep txns short; do I/O outside |
| Retrying without backoff | Thundering herd | Exponential backoff + jitter |
| Using Redis lock for correctness | Not safe under partitions | Fencing tokens or DB txn |
| Sleep-then-retry without limits | Infinite loops | Bounded retries + DLQ |
| Holding locks across user input | Lock hold for minutes | Optimistic with ETag |

### 9.2 Decision framework
```
                ┌─────────────────────────────────────┐
                │ Need cross-resource coordination?    │
                └─────────────────────────────────────┘
                         │ Yes              │ No
                         ▼                  ▼
              ┌──────────────────┐    ┌──────────────┐
              │ Saga + Idempotency│    │ Single DB?   │
              │ + Distributed lock│    └──────────────┘
              │ (if needed)       │       │ Yes
              └──────────────────┘       ▼
                                ┌─────────────────────────┐
                                │ Conflict rate?          │
                                └─────────────────────────┘
                                  │ High           │ Low
                                  ▼                ▼
                          ┌─────────────┐   ┌─────────────┐
                          │ Pessimistic  │   │ Optimistic   │
                          │ FOR UPDATE   │   │ version col  │
                          │ Serializable │   │ + retries    │
                          └─────────────┘   └─────────────┘
```

### 9.3 Rules of thumb
1. **OCC first, PCC when forced** — OCC has better throughput in the common case.
2. **Always keep transactions short** — locks held = throughput killed.
3. **Use the DB, not Redis, for correctness** — Redis for speed/coordination.
4. **Idempotency over locking** — for cross-service ops, idempotency keys + at-least-once is simpler than distributed locks.
5. **Measure conflict rate** — `pg_stat_database.deadlocks`, retry-rate metrics → tune accordingly.
6. **Pair every retry with backoff + jitter + cap**.

---

## 10. Cheat Sheet & Follow-up Q&A (~2 min)

### Cheat sheet
| Concept | One-liner |
|---------|-----------|
| **PCC** | Lock before access (`FOR UPDATE`) |
| **OCC** | CAS at commit using version column |
| **MVCC** | Multiple row versions; readers see snapshots; no read locks |
| **2PL** | Two phases: grow locks, then shrink |
| **Strict 2PL** | Release all locks at commit (used by all RDBMS) |
| **Snapshot Isolation** | Read snapshot at txn start; prevents most anomalies except write skew |
| **Serializable (SSI)** | Snapshot + dependency tracking; aborts unsafe txns |
| **Deadlock** | Cycle in wait-for graph; DB detects and kills one |
| **Distributed lock** | Cross-process mutex (Redis/ZK/etcd); use **fencing tokens** for safety |
| **Lease** | Lock + TTL; auto-released if holder dies |
| **Fencing token** | Monotonic ID validated by resource; defeats stale lock holders |
| **SKIP LOCKED** | Skip rows locked by others — perfect for work queues |
| **ETag / If-Match** | OCC over HTTP for collaborative apps |

### Follow-up Q&A

**Q1: How would you implement a counter that handles 1M increments/sec?**
A: Sharded counters. Split into N shards (`counter:0`…`counter:N`); writers `INCR` random shard; readers `SUM`. Trade exact-real-time consistency for throughput. Or use approximate counters (HyperLogLog) if exactness not required.

**Q2: Postgres Serializable threw `could not serialize access`. What now?**
A: Catch `SQLSTATE 40001`, retry the entire transaction with exponential backoff + jitter. SSI works by deferring conflict detection to commit time, so retries are expected. Cap at ~5 attempts; beyond that, surface 5xx and let the client retry.

**Q3: Redis lock vs ZooKeeper lock — when to choose what?**
A: **Redis** — efficiency use cases (avoid duplicate cron, dedupe processing); 1-2ms latency; not safe under network partitions/GC pauses without fencing tokens. **ZooKeeper/etcd** — correctness-critical (leader election, distributed config); 10-50ms latency; Raft/Zab guarantees consistency. Default to ZK/etcd for "must-be-exactly-one" semantics.

**Q4: How does CockroachDB handle distributed transactions without 2PC blocking?**
A: It uses **MVCC + OCC + parallel commits + HLC (hybrid logical clocks)**. Writes go to a transaction record; reads see a consistent snapshot. Commit is split into a "staging" record (parallel commits), which lets reads finish committing in the background. It's serializable globally without classic 2PC blocking.

**Q5: What is write skew? How do you prevent it?**
A: Two txns read overlapping data, write disjoint rows, violating an invariant. Classic: two doctors check "≥1 on call", both set themselves off. Snapshot Isolation does NOT prevent it (both read same snapshot). Fixes: (a) **Serializable** isolation, (b) **materialize the constraint** (e.g., `SELECT FOR UPDATE` on a "shifts" summary row), or (c) **explicit advisory lock**.

**Q6: How do you handle "hot key" contention on a single row?**
A: Options in order of complexity:
1. **Batching / coalescing** — accumulate in memory, flush periodically.
2. **Sharded counter** — split row into N rows, sum on read.
3. **Per-key queue + single writer** — Kafka partition by key.
4. **Eventually consistent + reconciliation** — e.g., Cassandra counter + periodic correction.
5. **In-memory CRDTs** (G-Counter, PN-Counter) for distributed counts.

**Q7: Difference between optimistic locking and idempotency?**
A: **OCC** prevents lost updates by detecting concurrent modifications (version check). **Idempotency** prevents duplicate effects from retries of the SAME request (idempotency key). They solve different problems and often coexist: OCC for concurrent users editing same data, idempotency for the same user retrying a flaky request.

**Q8: What's the relationship between isolation level and locking?**
A: Isolation is the **observable behavior** (what anomalies you'd see). Locking (or MVCC, or OCC) is the **implementation**. Two DBs can both offer Serializable but one via 2PL (SQL Server) and another via SSI (Postgres). As an app developer, code against the isolation level guarantee; choose the DB knowing the perf characteristics of its mechanism.

---

### Closing summary

| Pillar | What I'd reach for |
|--------|-------------------|
| **In-DB concurrency** | MVCC (default) + OCC version column for most updates |
| **Hot rows / money** | `SELECT … FOR UPDATE` + Serializable + retry on `40001` |
| **Cross-shard / service** | Saga + idempotency keys + outbox |
| **Work queues** | `FOR UPDATE SKIP LOCKED` or Kafka consumer groups |
| **Distributed mutex** | etcd/ZK for correctness; Redis + fencing for efficiency |
| **Hot counter** | Sharded counters in Redis or CRDTs |
| **Always** | Short txns, backoff + jitter, observability on retries & deadlocks |

> **TL;DR for the interviewer:** "Default to MVCC + optimistic locking. Reach for pessimistic locks only under high contention or strict-correctness writes like money. Use distributed locks for efficiency, not correctness — for that, lean on the database's own transactional guarantees plus idempotency."

