# Design a Key-Value Store — HLD Interview (1 Hour)

---

## 🎤 Opening — Clarifying Requirements (5 min)

> **Me:** "Before jumping in, I'd like to clarify the scope. When you say key-value store, are we talking about something like Amazon DynamoDB / Redis / Etcd scale? What kind of data size and traffic are we targeting?"

### Assumptions after clarification:

| Dimension | Requirement |
|---|---|
| **Operations** | `put(key, value)` and `get(key)` |
| **Data size** | Each KV pair ≤ 10 KB |
| **Scale** | ~100M keys, 100K QPS reads, 50K QPS writes |
| **Latency** | p99 < 10ms reads, < 50ms writes |
| **Availability** | Highly available (prefer AP over CP — eventual consistency is acceptable) |
| **Durability** | Data must not be lost once acknowledged |
| **Scalability** | Must scale horizontally |
| **Automatic failover** | Yes |

---

## 1️⃣ High-Level API (2 min)

```
PUT /kv/{key}
  Body: { "value": "<blob>" }
  Response: 200 OK / 201 Created

GET /kv/{key}
  Response: 200 { "value": "<blob>", "version": <ts> }

DELETE /kv/{key}
  Response: 200 OK
```

---

## 2️⃣ Single-Server Design — Start Simple (3 min)

The simplest key-value store is an in-memory hash map.

```
HashMap<String, String> store;
```

**Problems:**
- Memory is limited → can't fit 100M keys
- Single point of failure
- No durability (crash = data loss)

**Improvements on a single node:**
1. **Write-Ahead Log (WAL)** — append every write to disk before updating memory → durability.
2. **SSTable + LSM Tree** — when memory is full, flush sorted data to disk as immutable SSTables. Use a memtable (in-memory sorted structure like a red-black tree) for recent writes.

This is exactly how **LevelDB / RocksDB** work internally.

```
Client → Memtable (in-memory, sorted)
              ↓ flush when full
         SSTable L0 → SSTable L1 → SSTable L2 (on disk, sorted, immutable)
```

**Read path:** memtable → L0 → L1 → L2 (use **Bloom filters** to skip SSTables that definitely don't contain the key).

> This single-server design is our **storage engine**. Now let's distribute it.

---

## 3️⃣ Distributed Architecture — The Big Picture (5 min)

```
                        ┌──────────────┐
                        │   Clients    │
                        └──────┬───────┘
                               │
                        ┌──────▼───────┐
                        │  Load        │
                        │  Balancer    │
                        └──────┬───────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
        ┌─────▼─────┐   ┌─────▼─────┐   ┌─────▼─────┐
        │ Coord.     │   │ Coord.     │   │ Coord.     │
        │ Node / API │   │ Node / API │   │ Node / API │
        └─────┬──────┘   └─────┬──────┘   └─────┬──────┘
              │                │                │
    ┌─────────▼────────────────▼────────────────▼──────────┐
    │              Consistent Hash Ring                     │
    │                                                      │
    │   ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐ │
    │   │Node A│  │Node B│  │Node C│  │Node D│  │Node E│ │
    │   │ +WAL │  │ +WAL │  │ +WAL │  │ +WAL │  │ +WAL │ │
    │   │ +LSM │  │ +LSM │  │ +LSM │  │ +LSM │  │ +LSM │ │
    │   └──────┘  └──────┘  └──────┘  └──────┘  └──────┘ │
    └──────────────────────────────────────────────────────┘
```

**Key components:**
1. **Coordinator nodes** — stateless, route requests to the right storage node.
2. **Storage nodes** — each owns a range of keys, stores data via LSM engine.
3. **Consistent hashing** — determines which node owns which key.

---

## 4️⃣ Data Partitioning — Consistent Hashing (5 min)

### Why not simple `hash(key) % N`?
Adding/removing a node reshuffles almost all keys → massive data movement.

### Consistent Hashing
- Nodes and keys are hashed onto a ring (0 to 2^128 - 1).
- A key is assigned to the **first node clockwise** from its hash position.
- Adding/removing a node only affects its immediate neighbors → **minimal data movement**.

### Virtual Nodes
Each physical node gets **V virtual nodes** (e.g., V = 150) spread across the ring.

**Benefits:**
- Even load distribution (avoids hotspots from non-uniform hashing)
- Heterogeneous hardware: give powerful nodes more virtual nodes

```
Physical Node A → vnode_A1, vnode_A2, ..., vnode_A150
Physical Node B → vnode_B1, vnode_B2, ..., vnode_B150
```

---

## 5️⃣ Replication (5 min)

Each key is replicated to **N nodes** (typically N = 3).

**Strategy:** After finding the primary node on the ring, replicate to the **next N-1 distinct physical nodes** clockwise.

```
Key "user:123" → hash lands on vnode_A42
  Replica 1: Node A (primary / coordinator for this key)
  Replica 2: Node B (next distinct physical node clockwise)
  Replica 3: Node C (next distinct physical node clockwise)
```

This ensures replicas are on **different physical machines** (and ideally different racks/AZs).

---

## 6️⃣ Consistency Model — Quorum (5 min)

We use **tunable consistency** with quorum parameters:

| Parameter | Meaning |
|---|---|
| **N** | Number of replicas (e.g., 3) |
| **W** | Write quorum — # of acks needed before returning success |
| **R** | Read quorum — # of nodes to read from |

**Rule:** If `W + R > N`, we get **strong consistency**.

| Config | Behavior |
|---|---|
| W=1, R=1 | Fast but eventually consistent (Dynamo default) |
| W=2, R=2 (N=3) | Strong consistency |
| W=3, R=1 | Slow writes, fast consistent reads |
| W=1, R=3 | Fast writes, slow but consistent reads |

> **For our AP system:** W=1, R=1 with background anti-entropy for repair.

### Handling Conflicts — Vector Clocks

Each value carries a **vector clock**: `{ NodeA: 3, NodeB: 1 }`.

- On write, the coordinator increments its counter.
- On read from multiple replicas, compare vector clocks:
  - One dominates → pick it.
  - Concurrent (neither dominates) → **conflict** → return both to client for resolution (or use last-write-wins with timestamps if simplicity preferred).

```
Client writes "foo" via Node A → VC: {A:1}
Client writes "bar" via Node A → VC: {A:2}
Network partition: 
  Client writes "baz" via Node B → VC: {A:2, B:1}
  Client writes "qux" via Node A → VC: {A:3}
  → {A:3} and {A:2, B:1} are concurrent → CONFLICT
```

---

## 7️⃣ Handling Failures (8 min)

### 7a. Failure Detection — Gossip Protocol

Every node periodically pings a random subset of nodes and shares membership info.

```
Every 1s:
  Node A picks random Node X
  A sends heartbeat + its membership list to X
  X merges and responds
  If no heartbeat from Y for T seconds → Y marked "suspected"
  If multiple nodes suspect Y → Y marked "down"
```

No single point of failure — fully decentralized.

### 7b. Temporary Failures — Sloppy Quorum + Hinted Handoff

If a replica node (say Node C) is down during a write:
1. The coordinator sends the write to the **next healthy node** on the ring (Node D) instead.
2. Node D stores the data in a **hinted handoff queue** with a hint: "this belongs to Node C."
3. When Node C comes back, Node D forwards the data → Node C catches up.

```
Normal:   Key → A, B, C
C is down: Key → A, B, D (hinted for C)
C recovers: D → forwards hint to C → deletes local hint
```

### 7c. Permanent Failures — Anti-Entropy with Merkle Trees

For long-term divergence, replicas periodically compare data using **Merkle trees**:

```
         Root Hash
        /         \
   Hash(L)      Hash(R)
   /    \       /    \
 H(0-3) H(4-7) H(8-11) H(12-15)
  ...    ...    ...      ...
```

- Each leaf = hash of a key range.
- Compare root hashes between replicas.
  - If equal → in sync.
  - If different → traverse down to find divergent key ranges → sync only those.

**Benefit:** Minimizes data transferred during repair.

---

## 8️⃣ Write & Read Path — Detailed Flow (5 min)

### Write Path

```
Client
  │  PUT(key, value)
  ▼
Coordinator (any node, or determined by client-side routing)
  │
  ├─ 1. Hash(key) → find N replica nodes on ring
  │
  ├─ 2. Send write to all N replicas in parallel
  │
  ├─ 3. Each replica:
  │      a. Append to WAL (durable on disk)
  │      b. Insert into Memtable (in-memory sorted structure)
  │      c. If memtable full → flush to SSTable on disk
  │      d. ACK back to coordinator
  │
  ├─ 4. Coordinator waits for W ACKs
  │
  └─ 5. Return success to client
```

### Read Path

```
Client
  │  GET(key)
  ▼
Coordinator
  │
  ├─ 1. Hash(key) → find N replica nodes
  │
  ├─ 2. Send read to R replicas in parallel
  │
  ├─ 3. Each replica:
  │      a. Check Bloom Filter → skip SSTables that don't have key
  │      b. Check Memtable
  │      c. Check SSTables L0 → L1 → ... (newest first)
  │      d. Return value + vector clock
  │
  ├─ 4. Coordinator collects R responses
  │      → Resolve via vector clock (pick latest or detect conflict)
  │
  ├─ 5. Read Repair: if stale replicas detected, send updated value to them async
  │
  └─ 6. Return value to client
```

---

## 9️⃣ Compaction & Storage Engine Details (3 min)

### LSM Tree Compaction Strategies

| Strategy | How | Trade-off |
|---|---|---|
| **Size-tiered** | Merge similarly-sized SSTables together | Good write throughput, more space amplification |
| **Leveled** | Each level is 10x the size of previous; merge into next level | Better read perf, more write amplification |

We use **leveled compaction** for read-heavy workloads, **size-tiered** for write-heavy.

### Bloom Filters

Probabilistic data structure per SSTable:
- **"Definitely not in this SSTable"** → skip (no false negatives)
- **"Maybe in this SSTable"** → check it (small false positive rate ~1%)

Saves enormous disk I/O on reads.

---

## 🔟 Scaling — Adding / Removing Nodes (3 min)

### Adding a Node
1. New node joins the ring, gets assigned virtual nodes.
2. It takes over key ranges from its neighbors.
3. Data is streamed from existing replicas to the new node.
4. Once caught up, it starts serving traffic.

### Removing a Node
1. Node's key ranges are redistributed to neighbors.
2. Data is re-replicated to maintain N copies.

**Zero downtime** — the ring adjusts organically.

---

## 1️⃣1️⃣ Architecture Diagram — Complete System (3 min)

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                      │
│  │ Client 1 │  │ Client 2 │  │ Client N │  (SDK with           │
│  │ (w/ SDK) │  │ (w/ SDK) │  │ (w/ SDK) │   consistent hash    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘   routing)           │
│       │              │              │                            │
└───────┼──────────────┼──────────────┼────────────────────────────┘
        │              │              │
        ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     COORDINATOR LAYER                            │
│  (Stateless — any node can be coordinator)                      │
│  • Routes to correct replicas via consistent hash               │
│  • Manages quorum reads/writes                                  │
│  • Performs read repair                                         │
│  • Hinted handoff for temp failures                             │
└───────┬──────────────┬──────────────┬────────────────────────────┘
        │              │              │
        ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────┐
│                CONSISTENT HASH RING (Data Layer)                │
│                                                                 │
│   ┌────────────────────────────────────────────────────┐        │
│   │                                                    │        │
│   │    NodeA ──── NodeB ──── NodeC ──── NodeD          │        │
│   │      │          │          │          │             │        │
│   │      ▼          ▼          ▼          ▼             │        │
│   │   ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐          │        │
│   │   │ WAL  │  │ WAL  │  │ WAL  │  │ WAL  │          │        │
│   │   │Memtbl│  │Memtbl│  │Memtbl│  │Memtbl│          │        │
│   │   │SSTabl│  │SSTabl│  │SSTabl│  │SSTabl│          │        │
│   │   │Bloom │  │Bloom │  │Bloom │  │Bloom │          │        │
│   │   └──────┘  └──────┘  └──────┘  └──────┘          │        │
│   │                                                    │        │
│   │   Gossip protocol for failure detection            │        │
│   │   Merkle trees for anti-entropy repair             │        │
│   └────────────────────────────────────────────────────┘        │
│                                                                 │
│   Replication: N=3 across distinct physical nodes / AZs         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 1️⃣2️⃣ Summary of Techniques Used (2 min)

| Problem | Technique |
|---|---|
| Store large data | Partition across nodes |
| Even distribution | Consistent hashing + virtual nodes |
| High availability | Replication (N=3) |
| Consistency | Quorum (W/R/N) + vector clocks |
| Temporary failures | Sloppy quorum + hinted handoff |
| Permanent failures | Merkle tree anti-entropy |
| Failure detection | Gossip protocol |
| Durability | WAL + SSTable flush |
| Fast reads | Bloom filters + in-memory memtable |
| Write optimization | LSM tree (append-only, sequential I/O) |
| Conflict resolution | Vector clocks / last-write-wins |

---

## 1️⃣3️⃣ Real-World Systems for Reference

| System | Notes |
|---|---|
| **Amazon DynamoDB** | AP, sloppy quorum, vector clocks, consistent hashing |
| **Apache Cassandra** | DynamoDB-inspired, LSM storage, tunable consistency |
| **Riak** | AP, vector clocks, Merkle trees, hinted handoff |
| **Etcd** | CP (uses Raft consensus), used for config/coordination |
| **Redis** | In-memory, single-threaded, primarily used as cache |

---

## 1️⃣4️⃣ Potential Follow-Up Questions & Answers

**Q: How do you handle hot keys (e.g., a celebrity's profile)?**
> Use a read-through cache (like Redis) in front. Or add a random suffix to the key to shard it across nodes and merge results on read.

**Q: How would you support range queries?**
> Consistent hashing doesn't preserve order. Use **order-preserving partitioning** (like Cassandra's partition key + clustering key), but accept the risk of hotspots.

**Q: How do you handle datacenter replication?**
> Each datacenter has a full ring. Writes replicate asynchronously across DCs. Use **per-DC quorum** (e.g., LOCAL_QUORUM in Cassandra).

**Q: What if you need strong consistency instead of eventual?**
> Switch to a consensus protocol (Raft/Paxos) for writes. Trade-off: higher latency, lower availability during partitions (CAP theorem).

**Q: How do you handle large values (e.g., 1MB)?**
> Store value in blob storage (S3), store the pointer in the KV store. Or chunk the value across multiple keys.

---

## ⏱️ Time Breakdown

| Section | Time |
|---|---|
| Requirements clarification | 5 min |
| API design | 2 min |
| Single-server design | 3 min |
| Distributed architecture | 5 min |
| Consistent hashing | 5 min |
| Replication | 5 min |
| Consistency / quorum / vector clocks | 5 min |
| Failure handling (gossip, hinted handoff, Merkle) | 8 min |
| Write & read path | 5 min |
| Storage engine (LSM, compaction, bloom) | 3 min |
| Scaling | 3 min |
| Architecture diagram | 3 min |
| Summary + follow-ups | 8 min |
| **Total** | **~60 min** |

---

*This design is heavily inspired by the Dynamo paper (Amazon, 2007) and is the foundation of systems like Cassandra and Riak.*

---

## 📚 Appendix — Core Concepts Explained

### A. Write-Ahead Log (WAL)

A WAL is an **append-only file on disk** where every write is recorded **before** updating memory.

```
Client: PUT("name", "Alice")
  1. Append to WAL on disk:  [PUT | name | Alice | ts=100]   ← durable now
  2. Update in-memory memtable
  3. ACK to client
```

**Why?** If the server crashes after step 1 but before flushing to SSTable, we can **replay the WAL** on restart to recover all writes. It's sequential I/O (fast) — just appending to end of file.

**Think of it as:** A journal/diary that you write in before doing the actual work. If you forget what you were doing, read the diary.

---

### B. Memtable

An **in-memory sorted data structure** (typically a **Red-Black Tree** or **Skip List**) that holds recent writes.

```
Memtable (in memory, sorted by key):
  ┌─────────────────────────┐
  │  "age"    → 30          │
  │  "city"   → "Delhi"     │
  │  "name"   → "Alice"     │  ← sorted!
  │  "zip"    → "110001"    │
  └─────────────────────────┘
```

- **Writes** go here first (after WAL) — O(log n) insertion.
- **Reads** check here first — O(log n) lookup.
- When it hits a **size threshold** (e.g., 64 MB), it's **flushed to disk** as an immutable SSTable, and a new empty memtable is created.
- The corresponding WAL is discarded after flush (data is now safely on disk).

---

### C. SSTable (Sorted String Table)

An SSTable is an **immutable, sorted file on disk** containing key-value pairs.

```
SSTable file on disk:
┌────────┬────────┬────────┬────────┬──────────────┐
│ age=30 │city=Del│name=Ali│zip=110 │ Index + Bloom │
└────────┴────────┴────────┴────────┴──────────────┘
  ↑ sorted by key                     ↑ metadata block
```

**Key properties:**
- **Immutable** — never modified after creation (no random writes to disk)
- **Sorted** — enables binary search and efficient merging
- Each SSTable has a **sparse index** (key → offset) for fast lookups
- Each SSTable has its own **Bloom filter** (see below)

---

### D. SSTable Levels: L0 → L1 → L2 (LSM Tree)

LSM = **Log-Structured Merge Tree**. SSTables are organized into levels:

```
         ┌─────────────────┐
         │    Memtable      │  ← newest writes (in memory)
         └────────┬────────┘
                  │ flush
         ┌────────▼────────┐
Level 0  │ SST-1  SST-2    │  ← recently flushed, may have overlapping key ranges
         └────────┬────────┘
                  │ compaction (merge + sort + deduplicate)
         ┌────────▼────────┐
Level 1  │ SST-A  SST-B    │  ← non-overlapping key ranges, 10x size of L0
         └────────┬────────┘
                  │ compaction
         ┌────────▼────────┐
Level 2  │ SST-X  SST-Y    │  ← non-overlapping, 10x size of L1
         └─────────────────┘
```

**How compaction works (leveled):**
1. L0 fills up → pick an SSTable, find overlapping SSTables in L1
2. **Merge-sort** them together → write new SSTables to L1, delete old ones
3. Same process L1 → L2 when L1 gets too big

**Why levels?**
- L0 has overlapping ranges (multiple memtable flushes) → slow reads (must check all)
- L1+ has **non-overlapping ranges** → only check 1 SSTable per level → fast reads
- Older/colder data sinks to deeper levels

**Read order:** Memtable → L0 (all SSTables) → L1 (1 SSTable) → L2 (1 SSTable) → ...  
Stop at the **first match** (newest version wins).

---

### E. Vector Clocks

A **version tracking mechanism** where each node maintains its own counter. It's a map: `{ NodeId → Counter }`.

```
Step 1: Client writes via Node A
        VC = {A:1}           value = "v1"

Step 2: Client reads {A:1}, writes update via Node A  
        VC = {A:2}           value = "v2"

Step 3: Network partition! Two clients write concurrently:

  Client X writes via Node A:    VC = {A:3}        value = "v3"
  Client Y writes via Node B:    VC = {A:2, B:1}   value = "v4"
```

**Comparing vector clocks:**
- `{A:3}` vs `{A:2, B:1}` → **neither dominates** the other
  - A:3 > A:2 ✓, but B:1 > B:0 (absent) — so they're **concurrent** → CONFLICT
- `{A:2}` vs `{A:3}` → A:3 dominates → `{A:3}` is strictly newer, no conflict

**Dominance rule:** VC₁ dominates VC₂ if **every** counter in VC₁ ≥ corresponding counter in VC₂, and at least one is strictly greater.

**On conflict:** Either return both versions to the client to resolve (like Amazon's shopping cart), or use **Last-Write-Wins (LWW)** with wall-clock timestamps (simpler but can lose data).

---

### F. Bloom Filter

A **space-efficient probabilistic data structure** that answers: *"Is this key in this SSTable?"*

```
Bloom Filter = bit array of size m (e.g., 10 bits), with k hash functions

Insert "name":
  h1("name") = 2,  h2("name") = 5,  h3("name") = 8
  Set bits 2, 5, 8 to 1:

  Bit array: [0, 0, 1, 0, 0, 1, 0, 0, 1, 0]
                    ↑           ↑        ↑

Query "name":  check bits 2, 5, 8 → all 1 → "MAYBE present" ✓
Query "age":   h1("age")=1, h2("age")=5, h3("age")=7
               check bits 1, 5, 7 → bit 1 is 0 → "DEFINITELY NOT present" ✗
```

| Answer | Meaning |
|---|---|
| **"No"** | Key is **definitely not** in this SSTable → skip it (100% accurate) |
| **"Yes"** | Key **might** be in this SSTable → check it (small false positive rate ~1%) |

**Impact:** Without Bloom filters, a read for a missing key would scan **every SSTable** on disk. With Bloom filters, we skip 99% of them → massive I/O savings.

**Size:** ~10 bits per key. For 1M keys = ~1.2 MB per SSTable. Tiny cost for huge speedup.
