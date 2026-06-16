# Consistent Hashing — HLD Interview (1 Hour)

> **Simulated Interview Format**
> Interviewer asks broad questions → Candidate (you, a 6-7 YoE engineer) walks through Consistent Hashing — the problem it solves, the algorithm, and real-world uses.

---

## Table of Contents

1. [Opening — Clarify the Question (~3 min)](#1-opening--clarify-the-question-3-min)
2. [The Problem with Naive Hashing (~5 min)](#2-the-problem-with-naive-hashing-5-min)
3. [Consistent Hashing — Core Idea (~10 min)](#3-consistent-hashing--core-idea-10-min)
4. [Virtual Nodes (~7 min)](#4-virtual-nodes-7-min)
5. [Replication on the Ring (~5 min)](#5-replication-on-the-ring-5-min)
6. [Implementation Walkthrough (~10 min)](#6-implementation-walkthrough-10-min)
7. [Alternatives: Rendezvous, Jump, Maglev (~7 min)](#7-alternatives-rendezvous-jump-maglev-7-min)
8. [Real-World Uses (~5 min)](#8-real-world-uses-5-min)
9. [Edge Cases & Trade-offs (~5 min)](#9-edge-cases--trade-offs-5-min)
10. [Wrap-up (~3 min)](#10-wrap-up-3-min)

---

## 1. Opening — Clarify the Question (~3 min)

### Interviewer's Question
> "Explain Consistent Hashing. Why is it needed, how does it work, and where is it used in real systems?"

### Candidate's Response

Consistent Hashing is a partitioning technique that lets us distribute keys (data items, requests) across a cluster of nodes such that **adding or removing a node only re-maps a small fraction of keys**, not all of them.

It's a foundational technique used in:
- **Distributed caches**: Memcached (Ketama), Redis Cluster (variant).
- **NoSQL databases**: DynamoDB, Cassandra, Riak.
- **Load balancers**: Envoy, HAProxy (with appropriate config).
- **CDNs**: Cloudflare, Akamai.

I'll start with **why** we need it (the problem), then walk through the algorithm, virtual nodes, replication, alternatives, and real-world uses.

---

## 2. The Problem with Naive Hashing (~5 min)

### Naive Approach: `hash(key) % N`

Suppose we have 4 cache nodes:

```
   Key "foo" → hash("foo") % 4 → node 2
   Key "bar" → hash("bar") % 4 → node 0
   Key "baz" → hash("baz") % 4 → node 3
```

Simple. Distributes keys evenly. **But what if we add a 5th node?**

```
   Key "foo" → hash("foo") % 5 → node 1   ← was node 2
   Key "bar" → hash("bar") % 5 → node 4   ← was node 0
   Key "baz" → hash("baz") % 5 → node 1   ← was node 3
```

**Almost ALL keys get re-mapped.**

### Why is this a disaster?

```
   In a cache cluster: every key now hashes to a different node.
   → Almost every request is a cache miss.
   → Cache stampede on the backend DB.
   → Performance collapses.

   In a sharded DB: must physically move almost all the data.
   → Hours/days of rebalancing.
```

**Quantitatively:** With `N → N+1`, the expected fraction of keys re-mapped is `N/(N+1)` ≈ 100% for large N.

### What we WANT

Add/remove 1 node → re-map only **~1/N** of keys.

That's what Consistent Hashing achieves.

---

## 3. Consistent Hashing — Core Idea (~10 min)

### The Hash Ring

Imagine the hash space as a **circle** (ring) from `0` to `2^32 - 1` (or whatever your hash function range is).

```
                          0
                          │
              ┌───────────┴───────────┐
              │                       │
        2^32-1┤                       ├ 2^30
              │      HASH RING        │
              │                       │
        2^31 ─┤                       ├ 2^31 + 2^29
              │                       │
              └───────────┬────────���──┘
                          │
                       2^31
```

### Step 1: Place Nodes on the Ring

Each node is hashed by an identifier (IP, hostname) and placed at that position on the ring.

```
                       Node A (hash=0.1)
                        ●
                  ╱            ╲
                 ╱              ╲
       Node D  ●                  ● Node B (hash=0.3)
       (hash=0.9)                   (...)
                 ╲              ╱
                  ╲            ╱
                       ●
                  Node C (hash=0.6)
```

### Step 2: Place Keys on the Ring

Each key is hashed with the **same hash function** and placed at its position.

### Step 3: Assignment Rule

> A key is owned by the **first node encountered when moving clockwise** from the key's position.

```
   Key K1 at 0.2 → owner: Node B (next clockwise)
   Key K2 at 0.5 → owner: Node C
   Key K3 at 0.7 → owner: Node D
   Key K4 at 0.95 → owner: Node A (wraps around)
```

### Adding a Node

Suppose we add **Node E** at position 0.4.

```
                       Node A (0.1)
                        ●
                  ╱            ╲
                 ╱              ╲
       Node D  ●     Node E    ● Node B (0.3)
       (0.9)        (0.4) ★    
                 ╲              ╱
                  ╲            ╱
                       ●
                  Node C (0.6)
```

**Only keys between Node B (0.3) and Node E (0.4) get re-mapped** from Node C → Node E.

**All other keys are unaffected!**

### Removing a Node

Suppose Node B fails:
- Only keys that **were owned by B** need to move.
- They get re-assigned to the next clockwise node (Node C).
- Other keys are unaffected.

### Expected Re-mapping

With N nodes uniformly distributed: adding/removing 1 node moves **~1/N of keys**.

For N=100, only 1% of keys are re-mapped. ✅

### The Imbalance Problem

If we only have a few nodes, hash positions may cluster, creating uneven load:

```
   ●                   ← Node A might own 60% of ring
                 ●●    ← Nodes B and C own 40% combined
```

**Solution: Virtual Nodes (vnodes).**

---

## 4. Virtual Nodes (~7 min)

### The Idea

Instead of placing each physical node once, place it at **K different positions** on the ring (e.g., K=150).

```
   Node A → vnode_A_001, vnode_A_002, ..., vnode_A_150
   Node B → vnode_B_001, vnode_B_002, ..., vnode_B_150
```

Each vnode = `hash("nodeA-001")`.

### Benefits

```
   Before (3 nodes, no vnodes):
     Distribution might be:
       A: 60%   B: 25%   C: 15%   ← uneven!

   After (3 nodes × 150 vnodes each):
     Distribution:
       A: 33.5%   B: 33.2%   C: 33.3%   ← uniform!
```

**More vnodes per physical node → smoother distribution.**

### Heterogeneous Nodes

If Node A is twice as powerful as Node B, give it twice as many vnodes:
- Node A → 300 vnodes
- Node B → 150 vnodes
- Node C → 150 vnodes

Now Node A gets ~50% of traffic.

### Trade-off

- More vnodes = better distribution + smoother rebalancing.
- But more vnodes = more memory for the ring data structure + slower lookups (O(log V) where V = vnode count).

**Typical:** 100-200 vnodes per physical node.

---

## 5. Replication on the Ring (~5 min)

For fault tolerance, replicate each key on **N** consecutive nodes on the ring.

```
                       Node A
                        ●
                  ╱            ╲
                 ╱              ╲
       Node D  ●                  ● Node B
                                   
                 ╲              ╱
                  ╲            ╱
                       ●
                       Node C

   Key K (between B and C) with replication factor 3:
     Primary:  Node C  (next clockwise)
     Replica1: Node D  (next clockwise from C)
     Replica2: Node A  (next clockwise from D)
```

### "Preference List"

Dynamo paper terminology: the **first N unique physical nodes** clockwise are the preference list for a key.

(Skips additional vnodes of the same physical node — otherwise all replicas might be on the same machine.)

### Quorum

- **W** = writes must succeed on W replicas.
- **R** = reads from R replicas.
- **N** = replication factor.
- `W + R > N` → strong consistency.

Used by Cassandra, DynamoDB, Riak.

---

## 6. Implementation Walkthrough (~10 min)

### Data Structure: Sorted Map (TreeMap in Java)

```java
import java.util.*;
import java.security.MessageDigest;

public class ConsistentHashRing<T> {
    private final SortedMap<Long, T> ring = new TreeMap<>();
    private final int vnodesPerNode;

    public ConsistentHashRing(int vnodesPerNode) {
        this.vnodesPerNode = vnodesPerNode;
    }

    public void addNode(T node) {
        for (int i = 0; i < vnodesPerNode; i++) {
            long hash = hash(node.toString() + "-vn-" + i);
            ring.put(hash, node);
        }
    }

    public void removeNode(T node) {
        for (int i = 0; i < vnodesPerNode; i++) {
            long hash = hash(node.toString() + "-vn-" + i);
            ring.remove(hash);
        }
    }

    public T getNode(String key) {
        if (ring.isEmpty()) return null;
        long h = hash(key);
        // First entry with key >= h
        SortedMap<Long, T> tail = ring.tailMap(h);
        long firstKey = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(firstKey);
    }

    /** Get N distinct physical nodes for replication. */
    public List<T> getNodes(String key, int n) {
        if (ring.isEmpty()) return Collections.emptyList();
        Set<T> seen = new LinkedHashSet<>();
        long h = hash(key);
        SortedMap<Long, T> tail = ring.tailMap(h);
        Iterator<T> it = Stream.concat(
            tail.values().stream(),
            ring.values().stream()
        ).iterator();
        while (it.hasNext() && seen.size() < n) {
            seen.add(it.next());
        }
        return new ArrayList<>(seen);
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes("UTF-8"));
            // Use first 8 bytes as long
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Usage

```java
ConsistentHashRing<String> ring = new ConsistentHashRing<>(150);
ring.addNode("cache1.example.com");
ring.addNode("cache2.example.com");
ring.addNode("cache3.example.com");

String node = ring.getNode("user:42");
// → "cache2.example.com" (for example)

// For replication:
List<String> replicas = ring.getNodes("user:42", 3);
// → [cache2, cache3, cache1] — first 3 distinct nodes clockwise
```

### Complexity

| Operation       | Time         |
|-----------------|--------------|
| Add node        | O(V log N)   |
| Remove node     | O(V log N)   |
| Lookup key      | O(log N)     |

Where V = vnodes per node, N = total vnodes in ring.

### Hash Function Choice

- **MD5** (16 bytes) — widely used, fast enough, good distribution. (Used by Ketama.)
- **MurmurHash3** — non-cryptographic, very fast, excellent distribution.
- **xxHash** — fastest, great distribution.
- **CRC32** — too small, used in Redis Cluster's `slot = CRC16(key) % 16384`.

**Avoid:** Java's default `hashCode()` — poor distribution, prone to collisions.

---

## 7. Alternatives: Rendezvous, Jump, Maglev (~7 min)

Consistent hashing isn't the only way to do consistent assignment.

### 7.1 Rendezvous (Highest Random Weight) Hashing

For each key, compute `hash(key, node)` for every node and pick the node with the **highest** hash.

```
   For key K, for each node N_i:
       score_i = hash(K, N_i)
   pick N_i with max score
```

**Pros:**
- No ring data structure.
- No vnodes needed (perfectly uniform without them).
- Adding/removing a node re-maps exactly 1/N keys.

**Cons:**
- O(N) lookup per key (vs O(log N) for consistent hashing).
- Worse with many nodes; fine for small clusters.

**Used by:** GitHub's load balancers, some CDNs.

### 7.2 Jump Consistent Hash (Google)

A clever ~5-line algorithm using a deterministic pseudo-random sequence.

```c
int32_t JumpConsistentHash(uint64_t key, int32_t num_buckets) {
    int64_t b = -1, j = 0;
    while (j < num_buckets) {
        b = j;
        key = key * 2862933555777941757ULL + 1;
        j = (b + 1) * ((double)(1LL << 31) / ((key >> 33) + 1));
    }
    return b;
}
```

**Pros:**
- Extremely fast (no data structure, just a loop).
- Uniform distribution.
- Minimal re-mapping on bucket changes.

**Cons:**
- Only works when bucket IDs are contiguous integers 0..N-1.
- Hard to remove arbitrary nodes (only "shrink from end").
- Not as flexible as ring or rendezvous.

**Used by:** Google internal systems, some sharded databases.

### 7.3 Maglev Hashing (Google)

Each backend builds a lookup table mapping hash slots → backends. Designed for **load balancing** at very high speed.

**Pros:**
- O(1) lookup.
- Very minimal disruption on backend changes (~1/N).
- Even load balancing.

**Cons:**
- More complex setup.
- Lookup table memory.

**Used by:** Google's Maglev load balancer, Envoy.

### Comparison

```
┌────────────────────┬──────────────────┬──────────────────┬──────────────────┐
│                    │ Consistent Hash  │ Rendezvous       │ Jump             │
├────────────────────┼──────────────────┼──────────────────┼──────────────────┤
│ Lookup time        │ O(log N)         │ O(N)             │ O(log N)         │
│ Uniformity         │ Needs vnodes     │ Excellent natively│ Excellent       │
│ Memory             │ O(N × V)         │ O(N)             │ O(1)             │
│ Arbitrary remove   │ Yes              │ Yes              │ No (shrink only) │
│ Weighted nodes     │ Easy (more vnodes)│ Easy             │ Limited          │
│ Best for           │ Caches, NoSQL    │ Small clusters,  │ Sharding with    │
│                    │                  │ LBs              │ contiguous IDs   │
└────────────────────┴──────────────────┴──────────────────┴──────────────────┘
```

---

## 8. Real-World Uses (~5 min)

### Memcached (Ketama Hashing)

- 160 vnodes per server.
- MD5 hash.
- The original consistent hashing implementation in cache clients (libketama).

### Cassandra

- 256 vnodes per node (default).
- MurmurHash3.
- Token range determines data ownership.

### Amazon DynamoDB & Dynamo Paper

- Ring with vnodes (called "tokens").
- Replication factor 3.
- Preference list (next N distinct physical nodes).

### Riak

- Direct implementation of Dynamo paper.
- 64-vnode default, sloppy quorum.

### Redis Cluster (variant)

- Uses 16384 **fixed hash slots** (not a true ring).
- `slot = CRC16(key) % 16384`.
- Each node owns a contiguous range of slots.
- Reassigning slots = manual or automated rebalancing.
- Simpler than full consistent hashing, but achieves similar goals.

### Apache Kafka

- Uses partitioning, not ring-based hashing.
- `partition = hash(key) % num_partitions`.
- Increasing partitions DOES rebalance (so consumers handle re-keying carefully).
- Trade-off: simpler ops, but doesn't have CH's elastic resizing benefit.

### CDN edge selection

- Cloudflare, Fastly use consistent hashing to route requests to specific origin caches → maximize cache locality.

### Distributed Load Balancers (Envoy `ring_hash`, `maglev`)

- Sticky sessions without storing state.
- Same user → same backend most of the time.

---

## 9. Edge Cases & Trade-offs (~5 min)

### 9.1 Hot Spots

Even with vnodes, a single very hot key (e.g., celebrity's profile) hits one node hard.

**Mitigations:**
- **Local caching** at clients.
- **Read replicas** for hot keys.
- **Key splitting** (`user:bieber:shard1`, `user:bieber:shard2`).
- **Caching layer** (Redis) in front of the hot node.

### 9.2 Cascading Failures

If a node fails, its load shifts to neighbors. If neighbors can't handle it, they fail too → cascade.

**Mitigations:**
- **Over-provisioning** (~30% headroom).
- **Bulkheads / circuit breakers**.
- **Gradual draining** before removing a node.

### 9.3 Data Migration

When nodes are added/removed, data must move.

- **Background migration** with throttling.
- **Read from both old + new** during migration (dual reads).
- **Write to both** during cutover (dual writes).
- DynamoDB / Cassandra handle this transparently.

### 9.4 Bounded-Load Consistent Hashing (Google, 2017)

Extension to prevent any single node from getting too much load:
- Cap each node at `(1 + ε) × average load`.
- If a node is at cap, key spills over to the next clockwise node.
- Used in Google's load balancers.

### 9.5 Client-Side vs Server-Side

| Approach     | Pros                          | Cons                                       |
|--------------|-------------------------------|--------------------------------------------|
| Client-side  | No coordination needed; fast   | All clients need ring topology + updates    |
| Server-side  | Topology centralized           | Extra hop; coordinator may be a bottleneck  |

**Hybrid:** Clients have ring; gossip protocol propagates membership changes.

### 9.6 The "Splay Hashing" Failure Mode

If your hash function is bad, all your vnodes cluster in one part of the ring �� uneven distribution.

**Verify:** test your hash function with chi-squared distribution test.

---

## 10. Wrap-up (~3 min)

### Summary

```
┌────────────────────────────────────────────────────────────────┐
│              CONSISTENT HASHING CHEAT SHEET                    │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│ Problem:                                                       │
│   hash(key) % N  →  changes N re-maps almost all keys.        │
│                                                                │
│ Solution:                                                      │
│   Place nodes + keys on a circular hash ring.                  │
│   Key owned by first node clockwise.                           │
│   Add/remove node → only ~1/N keys move.                       │
│                                                                │
│ Virtual nodes (vnodes):                                        │
│   Each physical node placed at K positions on ring.            │
│   → Uniform distribution + smoother rebalancing.               │
│   Typical: K = 100-200.                                         │
│                                                                │
│ Replication:                                                   │
│   Replicate to next N DISTINCT physical nodes clockwise.       │
│   Quorum: W + R > N for strong consistency.                    │
│                                                                │
│ Hash function: MurmurHash3, xxHash, MD5 (Ketama).              │
│   Avoid: Java hashCode(), CRC32 (range too small).             │
│                                                                │
│ Implementation:                                                │
│   TreeMap<long, Node> in Java; ceilingEntry for clockwise look.│
│   Lookup: O(log N), Add/Remove: O(V log N).                    │
│                                                                │
│ Alternatives:                                                  │
│   - Rendezvous: O(N) lookup, no ring needed.                  │
│   - Jump: O(log N), only for contiguous bucket IDs.           │
│   - Maglev: O(1) lookup, used for load balancing.             │
│                                                                │
│ Used by: Memcached, Cassandra, DynamoDB, Riak, Envoy,         │
│          Cloudflare CDN, Redis Cluster (variant).             │
│                                                                │
│ Watch out: hot keys, cascading failures, bad hash functions.   │
└────────────────────────────────────────────────────────────────┘
```

### Common Interview Follow-up Questions

**Q: Why use vnodes? Can't we just hash node IDs multiple times?**
> That's exactly what vnodes are — each vnode is `hash(nodeID + index)`. The point is to place each physical node at MANY positions, evening out the distribution.

**Q: How would you handle a node being slow but not down?**
> Combine consistent hashing with active health checks. Mark slow node as "draining" — stop new writes, drain reads gradually, then remove from ring. Use bounded-load CH to spill traffic to neighbors.

**Q: How does Redis Cluster differ from classic consistent hashing?**
> Redis uses 16384 fixed hash slots; each master owns a range. It's a SIMPLIFICATION — slots are pre-allocated. Adding a node requires explicit slot migration. Simpler ops but less elastic than ring-based CH.

**Q: What if I want to support weighted nodes (different capacities)?**
> Give the more powerful node MORE vnodes proportionally. e.g., 2x capacity → 2x vnodes → 2x traffic share.

**Q: Sticky sessions with consistent hashing?**
> Use a "ring_hash" or "maglev" LB algorithm with userID as key. Same user → same backend most of the time, with graceful re-routing on backend changes.

**Q: Can consistent hashing replace a database index?**
> No. CH is about WHERE data lives (which node), not how to look it up within a node. Within a node, use B-Tree/LSM/Hash index as appropriate.

---

*This document simulates a complete 1-hour HLD interview on Consistent Hashing — from the underlying problem to implementation details, alternatives, and real-world systems.*

