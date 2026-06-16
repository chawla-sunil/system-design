# CAP Theorem — HLD Interview (1 Hour)

> **Simulated Interview Format**
> Interviewer asks broad questions → Candidate (you, a 6-7 YoE engineer) walks through CAP theorem, its implications, PACELC, and real-world database choices.

---

## Table of Contents

1. [Opening — Clarify the Question (~3 min)](#1-opening--clarify-the-question-3-min)
2. [What is CAP? (~8 min)](#2-what-is-cap-8-min)
3. [The Three Properties in Depth (~10 min)](#3-the-three-properties-in-depth-10-min)
4. [Why You Can Only Pick Two (~5 min)](#4-why-you-can-only-pick-two-5-min)
5. [CP vs AP Systems — Real Examples (~10 min)](#5-cp-vs-ap-systems--real-examples-10-min)
6. [PACELC — Beyond CAP (~7 min)](#6-pacelc--beyond-cap-7-min)
7. [Consistency Spectrum (~5 min)](#7-consistency-spectrum-5-min)
8. [Common Misconceptions (~5 min)](#8-common-misconceptions-5-min)
9. [Designing Around CAP — Practical Strategies (~5 min)](#9-designing-around-cap--practical-strategies-5-min)
10. [Wrap-up & Decision Framework (~2 min)](#10-wrap-up--decision-framework-2-min)

---

## 1. Opening — Clarify the Question (~3 min)

### Interviewer's Question
> "Explain the CAP theorem. How does it influence your design decisions? Walk me through real systems and how they handle these trade-offs."

### Candidate's Response

Great topic. Let me start with the theorem itself and then go deep into nuances most people miss.

The CAP theorem, proposed by **Eric Brewer (2000)** and formally proven by **Gilbert & Lynch (2002)**, is one of the most quoted (and most misunderstood) results in distributed systems.

I'll cover:
1. The classic definition.
2. The three properties precisely.
3. Why "pick 2 of 3" is a simplification — the real choice is during partitions.
4. PACELC, which extends CAP to non-partition scenarios.
5. Real systems (DynamoDB, Cassandra, Spanner, etc.).
6. Practical design implications.

---

## 2. What is CAP? (~8 min)

### Definition

> **In a distributed data system, you can guarantee at most 2 out of 3 of: Consistency, Availability, and Partition tolerance.**

```
                    ┌────────────┐
                    │     C      │
                    │ Consistency│
                    └──────┬─────┘
                           │
                  ╱        │        ╲
                 ╱  CP     │    CA   ╲
                ╱          │          ╲
   ┌──────────┐            │            ┌────────────┐
   │     A     │            │            │     P      │
   │Availability──── AP ────│────────────│ Partition  │
   └──────────┘            │            │ Tolerance  │
                           │            └────────────┘
```

### The Setup

- **Distributed system** = multiple nodes storing data, connected over a network.
- A **network partition** = nodes can't talk to some other nodes.
- **CAP says:** during a partition, you must choose **either**:
  - Continue serving requests with possibly stale data (**AP**), OR
  - Refuse requests until consistency is restored (**CP**).

### Visualizing a Partition

```
Before partition:                After partition (network split):
   ┌──────┐                          ┌──────┐  ╳  ┌──────┐
   │Node A│──────┐                   │Node A│  ╳  │Node C│
   └──────┘      │                   └──┬───┘  ╳  └──┬───┘
                 │                      │      ╳     │
   ┌──────┐      │                   ┌──┴───┐  ╳     │
   │Node B│──────┤                   │Node B│  ╳     │
   └──────┘      │                   └──────┘  ╳     │
                 │                                   │
   ┌──────┐      │                                ┌──┴───┐
   │Node C│──────┘                                │Node D│
   └──────┘                                       └──────┘

   All nodes can talk.                   Left {A,B} and right {C,D}
                                          can't communicate.
```

Now: a write arrives at Node A. Should we:
- (A) Accept it and risk Node C/D not knowing → **available but inconsistent**.
- (B) Reject it until partition heals → **consistent but unavailable**.

That's the CAP trade-off in action.

---

## 3. The Three Properties in Depth (~10 min)

### 3.1 Consistency (C)

> Every read receives the most recent write, **or an error**.

This is **linearizability** (strong consistency, single-copy semantics):
- After write(x=5) completes, every subsequent read returns 5.
- Reads/writes appear atomic across all nodes.

**Not the same as ACID's "C"!**
- ACID Consistency = "DB invariants are preserved" (constraints, triggers).
- CAP Consistency = "all nodes see the same data at the same time".

```
Linearizable example:
   t=1: Write(x=5) ✅
   t=2: Read(x) → MUST return 5 (or later value)

Non-linearizable:
   t=1: Write(x=5)
   t=2: Read(x) → might return old value (3) for some time
```

### 3.2 Availability (A)

> Every request receives a (non-error) response, **without guarantee that it contains the most recent write**.

- Every non-failing node serves every request.
- No "down" responses, even during partitions.
- Doesn't say anything about latency.

```
Available system, partition active:
   Client → Node A: Write(x=5) → OK ✅
   Client → Node C: Read(x)    → returns old value (3) ✅ (still a response)
```

### 3.3 Partition Tolerance (P)

> The system continues to operate despite arbitrary message loss or partial failure of the network.

- In any real distributed system, **partitions WILL happen**:
  - Network cable cut.
  - Switch failure.
  - Router crash.
  - Cloud AZ outage.

> **In practice: P is non-negotiable.** You don't get to choose to "not have partitions" — they happen whether you like it or not.

So the real choice is: **CP or AP**.

---

## 4. Why You Can Only Pick Two (~5 min)

### The Impossibility (intuition)

Imagine 2 nodes (A and B) that get partitioned:

```
   Client1 → Node A         Node B ← Client2
            (write x=5)              (read x = ?)
   ─────────╳─────── partition ───────╳────────
```

- If Node B returns the **old** value → not Consistent (returned stale).
- If Node B returns an **error** → not Available.
- If Node B waits for A → not Available (hangs).
- If Node B refuses to operate during partitions → not Partition tolerant (just CA — which doesn't work in real networks).

**Pick any 2:**

| Choice | Result | Real-world? |
|--------|--------|-------------|
| CA     | Consistent + Available, breaks on partition | Only single-node systems |
| CP     | Consistent + Partition tolerant, may reject requests | Strong-consistency DBs |
| AP     | Available + Partition tolerant, may return stale data | Eventually-consistent DBs |

> **CA is essentially a fiction in distributed systems.** Partitions happen, so you'll choose between CP and AP at some point.

---

## 5. CP vs AP Systems — Real Examples (~10 min)

### CP Systems (Consistency > Availability during partition)

| System         | Why CP?                                              |
|----------------|------------------------------------------------------|
| **HBase**      | Strongly consistent reads/writes per region          |
| **MongoDB**    | (default) Primary-only writes; secondaries lag       |
| **Etcd**       | Raft consensus; majority required                    |
| **ZooKeeper**  | ZAB consensus; clients see consistent state          |
| **Spanner**    | Globally consistent with TrueTime + Paxos            |
| **CockroachDB**| Raft-based, serializable transactions                |
| **Postgres**   | Single primary; replica is async/sync configurable   |
| **Redis (cluster, default)** | Writes only on primary; failover requires majority |

**Behavior during partition:**
- Minority partition rejects writes/reads → preserves consistency.
- "Better to return an error than wrong data."

**Use cases:**
- Banking / financial transactions.
- Inventory (no overselling).
- Distributed locks / leader election.
- Configuration management.

### AP Systems (Availability > Consistency during partition)

| System              | Why AP?                                          |
|---------------------|--------------------------------------------------|
| **Cassandra**       | Tunable consistency, leaderless, hinted handoff  |
| **DynamoDB** (eventual) | Eventually consistent reads by default      |
| **Riak**            | Inspired by Dynamo paper, vector clocks          |
| **CouchDB**         | Multi-master, conflict resolution                |
| **DNS**             | Caching, eventual propagation                    |

**Behavior during partition:**
- All partitions keep serving reads/writes.
- Conflicts resolved later (last-write-wins, vector clocks, CRDTs).

**Use cases:**
- Shopping cart (better stale than down).
- Social media feeds.
- Analytics / metrics collection.
- DNS, CDN, content distribution.

### Side-by-Side

```
┌──────────────────────┬─────────────────────────┬──────────────────────┐
│                      │  CP (Consistent)        │  AP (Available)      │
├──────────────────────┼─────────────────────────┼──────────────────────┤
│ During partition     │ Reject some requests    │ Serve all (may stale)│
│ Recovery model       │ Catch up after heal     │ Reconcile conflicts  │
│ Reads               │ Always fresh             │ Possibly stale       │
│ Writes              │ Coordinated (consensus)  │ Local; sync later    │
│ Examples            │ Spanner, HBase, Etcd,    │ Cassandra, DynamoDB, │
│                     │ ZooKeeper, MongoDB       │ Riak, CouchDB        │
│ Banking            ✅ │                         │                      │
│ Shopping cart      ❌ │                         │ ✅                   │
│ Social feed        ❌ │                         │ ✅                   │
│ Inventory          ✅ │                         │                      │
│ Service registry   ✅ │                         │                      │
│ Analytics            │                         │ ✅                   │
└──────────────────────┴─────────────────────────┴──────────────────────┘
```

---

## 6. PACELC — Beyond CAP (~7 min)

CAP only addresses what happens **during partitions**. But what about **normal operation** (which is most of the time)?

> **PACELC (Daniel Abadi, 2010):**
> If Partition (P), choose between Availability (A) or Consistency (C).
> Else (E), choose between Latency (L) or Consistency (C).

```
                    ┌─────────────────────┐
                    │   PACELC SQUARE      │
                    ├──────────┬──────────┤
                    │          │           │
                    │  PA/EL   │   PA/EC   │
                    │  Dynamo  │  Mongo    │
                    │  Cassandra│           │
                    ├──────────┼──────────┤
                    │          │           │
                    │  PC/EL   │   PC/EC   │
                    │ (rare)   │  Spanner  │
                    │          │  HBase    │
                    └──────────┴──────────┘
```

### Examples

| System          | PACELC | Explanation                                          |
|-----------------|--------|------------------------------------------------------|
| **Spanner**     | PC/EC  | Strong consistency always (even sacrifices latency)  |
| **HBase**       | PC/EC  | Same                                                 |
| **PostgreSQL** (sync repl) | PC/EC | Sync replication for both                |
| **MongoDB**     | PC/EC  | Or PA/EL with `readPreference=secondary`             |
| **DynamoDB**    | PA/EL  | Eventually consistent reads for low latency          |
| **Cassandra**   | PA/EL  | Tunable; default favors low latency                  |
| **Riak**        | PA/EL  | Same                                                 |

### Why PACELC matters

- Even when there's no partition, replicating across nodes/regions adds latency.
- Strong consistency → sync replication → higher write latency.
- Eventual consistency → async replication → lower latency, possibly stale reads.
- **You're trading consistency for latency 99%+ of the time, not just during partitions.**

---

## 7. Consistency Spectrum (~5 min)

CAP makes consistency sound binary, but reality is a spectrum:

```
   STRONGER ◄────────────────────────────────────────► WEAKER
   │                                                          │
   Linearizable ─ Sequential ─ Causal ─ Read-your-writes ─ Eventual
```

### Levels

1. **Linearizable** — global real-time order; appears as single copy.
2. **Sequential** — all clients see same order, but not necessarily real-time.
3. **Causal** — operations causally related appear in order; concurrent ops may differ.
4. **Read-your-writes** — you always see your own writes (may not see others' immediately).
5. **Monotonic reads** — never go backward in time.
6. **Eventual** — given no new writes, replicas converge.

### Tunable Consistency (Cassandra/DynamoDB style)

```
  W + R > N  →  Strong consistency
  (W = write replicas, R = read replicas, N = total replicas)

  Example: N=3
    W=3, R=1 → strong reads, slow writes
    W=1, R=3 → fast writes, strong reads
    W=2, R=2 → balanced (quorum)
    W=1, R=1 → eventually consistent (fastest)
```

### Quorum Math

```
Quorum = (N/2) + 1

  N=3 → quorum=2
  N=5 → quorum=3

Why? Ensures any read quorum overlaps with any write quorum
     (pigeon-hole principle).
```

---

## 8. Common Misconceptions (~5 min)

### Misconception 1: "CAP means you pick 2"

> Reality: **In a real distributed system, you always have P. Your real choice is C or A during partitions** — and on the latency/consistency axis the rest of the time (PACELC).

### Misconception 2: "MongoDB is CP, Cassandra is AP — full stop"

> Reality: Both are **tunable**. MongoDB with `readPreference=secondary` becomes more AP-leaning. Cassandra with `QUORUM` reads/writes becomes more CP-leaning. The defaults differ, but configuration matters.

### Misconception 3: "Eventual consistency is bad"

> Reality: For most use cases (social feed, product reviews, analytics), eventual consistency is **perfectly fine** and unlocks massive availability and scale gains.

### Misconception 4: "CAP applies to all systems"

> Reality: CAP applies to **distributed data stores**. A stateless web server doesn't have a CAP property.

### Misconception 5: "You can have CA"

> Reality: CA in a distributed system means "I'm assuming no partitions" — which is unrealistic. CA is really a property of single-node systems.

### Misconception 6: "ACID = Consistency in CAP"

> Reality: They're different.
> - ACID-C = invariant preservation in a single DB.
> - CAP-C = single-copy semantics across distributed nodes (linearizability).

---

## 9. Designing Around CAP — Practical Strategies (~5 min)

### Strategy 1: Choose the right DB per use case (polyglot persistence)

```
   E-commerce platform:

   Orders / Payments  → Postgres (CP, ACID)
   Product catalog     → DynamoDB (AP, fast reads)
   User session        → Redis (CP within cluster)
   Shopping cart       → DynamoDB (AP, last-write-wins ok)
   Search index        → Elasticsearch (AP, eventually consistent)
   Analytics           → Cassandra / ClickHouse (AP, write-heavy)
   Config / secrets    → Etcd / Consul (CP, must be consistent)
   User social feed    → Cassandra (AP, scale)
```

### Strategy 2: Use the right consistency level per operation

```java
// Strong consistency for critical reads
PaymentDetails p = dynamodb.getItem(
    GetItemRequest.builder()
        .consistentRead(true)   // strong consistency
        .key(...)
        .build()
);

// Eventual consistency for non-critical
ProductReview r = dynamodb.getItem(
    GetItemRequest.builder()
        // consistentRead defaults to false → eventual
        .key(...)
        .build()
);
```

### Strategy 3: Compensate with patterns
- **Saga** for distributed transactions instead of 2PC.
- **CQRS** to separate read/write paths.
- **Event sourcing** for audit + eventual reconstruction.
- **Idempotency** to safely retry under uncertainty.
- **CRDTs** for conflict-free replicated data (counters, sets).

### Strategy 4: Design UX for eventual consistency
- "Order placed, will update shortly."
- Optimistic UI — show local change immediately, reconcile later.
- Conflict resolution UI (e.g., "this item was modified, choose version").

### Strategy 5: Mitigate partition impact
- Multi-region replication with active-active (Cassandra, Cosmos DB).
- Health checks + automated failover.
- Geographic load balancing.
- Backpressure / graceful degradation.

---

## 10. Wrap-up & Decision Framework (~2 min)

### Decision Tree

```
            ┌─────────────────────────────────────┐
            │ Is your operation safety-critical?  │
            │ (money, inventory, locks, config)    │
            └────────────────┬─────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
            YES              │             NO
              │              │              │
              ▼              │              ▼
       Choose CP             │       ┌──────────────────┐
       (Spanner, Postgres,   │       │ Need low latency?│
        HBase, Etcd)         │       │ Tolerate stale?  │
              │              │       └────────┬─────────┘
              ▼              │                │
   Accept lower availability │   ┌────────────┼────────────┐
   during partitions         │   │            │            │
                             │  YES           │           NO
                             │   │            │            │
                             │   ▼            │            ▼
                             │ Choose AP      │   Choose CP w/
                             │ (DynamoDB,     │   tuning (e.g.,
                             │  Cassandra,    │   MongoDB writeMajority)
                             │  Riak)         │
                             │   │            │
                             │   ▼            │
                             │ Design app for│
                             │ eventual      │
                             │ consistency   │
                             │ (idempotency, │
                             │  CRDTs)       │
```

### Golden Rules

1. **P is mandatory.** Plan for partitions.
2. **Pick CP for correctness-critical data**, AP for high-availability + scale.
3. **Apply PACELC** — even without partitions, you trade consistency for latency.
4. **Most modern DBs are tunable** — choose the right knob per query.
5. **Design UX for eventual consistency** wherever possible.
6. **Combine** — polyglot persistence + sagas + CQRS often beats picking one DB for everything.

### Common Follow-up Questions

**Q: Is the CAP theorem outdated?**
> Not outdated, but oversimplified. PACELC is a more useful framework. Modern systems (like Spanner) push the boundaries with global consensus + tight clocks, but they still respect CAP — they're just CP with very high availability.

**Q: How does Spanner "beat" CAP?**
> It doesn't. Spanner is CP — during a partition, the minority side becomes unavailable. It just achieves near-five-nines availability by minimizing partitions (Google's private global network + Paxos).

**Q: Real-world example of CAP affecting design?**
> 2017 GitHub outage: a network partition caused MySQL primaries in two data centers to diverge. They chose **C over A** — paused writes, took ~24h to reconcile. Result: brief unavailability, no data loss. Worth it for git data.

**Q: What's a CRDT?**
> Conflict-free Replicated Data Type — data structures (counters, sets, maps) that converge automatically without coordination. Powers Riak, Redis CRDTs, and Redis Enterprise multi-region.

**Q: Quorum reads/writes — how does it work?**
> If `W + R > N`, reads always see latest writes. Example: N=3, W=2, R=2 → write goes to 2/3, read from 2/3, guaranteed overlap.

---

## Appendix: Quick Reference Card

```
┌────────────────────────────────────────────────────────────────┐
│                CAP THEOREM CHEAT SHEET                         │
├────────────────────────────────────────────────────────────────┤
│ C — Every read sees the latest write (or error)                │
│ A — Every request gets a response (maybe stale)                │
│ P — System works despite network partitions                    │
│                                                                │
│ In real systems: P is mandatory → choose CP or AP.             │
│                                                                │
│ CP examples: Spanner, HBase, Etcd, ZooKeeper, Postgres,        │
│              MongoDB (default), CockroachDB                    │
│ AP examples: Cassandra, DynamoDB, Riak, CouchDB, DNS           │
│                                                                │
│ PACELC: P → A or C, Else → L or C                              │
│                                                                │
│ Consistency spectrum:                                          │
│   Linearizable > Sequential > Causal >                         │
│   Read-your-writes > Monotonic > Eventual                      │
│                                                                │
│ Quorum: W + R > N for strong consistency.                      │
│ CRDTs: convergent data types, no coordination.                 │
│                                                                │
│ Golden rule: Pick CP for correctness; AP for scale+UX.        │
└────────────────────────────────────────────────────────────────┘
```

---

*This document simulates a complete 1-hour HLD interview on the CAP Theorem, going beyond the textbook definition into PACELC, consistency tuning, and real-world database design.*

