# Distributed Cache & Caching Strategies — HLD Interview (1 Hour)

> **Simulated Interview Format**
> Interviewer asks broad questions → Candidate (you, a 6-7 YoE engineer) answers step by step, starting high-level and drilling into details. The flow mimics a real 60-minute system-design round.

---

## Table of Contents

1. [Opening — Clarify the Problem (~5 min)](#1-opening--clarify-the-problem-5-min)
2. [Why Do We Need a Cache? (~5 min)](#2-why-do-we-need-a-cache-5-min)
3. [High-Level Architecture (~10 min)](#3-high-level-architecture-10-min)
4. [Caching Strategies / Patterns (~15 min)](#4-caching-strategies--patterns-15-min)
5. [Cache Eviction Policies (~5 min)](#5-cache-eviction-policies-5-min)
6. [Distributed Cache Deep Dive (~10 min)](#6-distributed-cache-deep-dive-10-min)
7. [Cache Consistency, Invalidation & Thundering Herd (~5 min)](#7-cache-consistency-invalidation--thundering-herd-5-min)
8. [Technology Choices (~3 min)](#8-technology-choices-3-min)
9. [Wrap-up & Trade-offs (~2 min)](#9-wrap-up--trade-offs-2-min)

---

## 1. Opening — Clarify the Problem (~5 min)

### Interviewer's Question
> "Design a Distributed Caching System. Walk me through caching strategies you'd use in a large-scale application."

### Candidate's Response

Before I jump into the design, let me clarify a few things:

**Q: What kind of system are we caching for?**
- A high-traffic web application (e.g., e-commerce, social media feed, content platform).
- Millions of reads per second, write-heavy in bursts.

**Q: What are our goals?**
- **Low latency** — sub-millisecond reads.
- **High throughput** — handle millions of QPS.
- **High availability** — cache should not be a single point of failure.
- **Scalability** — horizontally scalable as traffic grows.
- **Consistency** — eventual consistency is acceptable (most cache use cases tolerate stale data for a short window).

**Q: Constraints / Non-functional requirements?**

| Requirement       | Target                              |
|-------------------|-------------------------------------|
| Read latency      | < 1 ms (p99)                        |
| Throughput        | 1M+ QPS per cluster                 |
| Availability      | 99.99%                              |
| Data size         | 100 GB – a few TB in-memory         |
| Consistency       | Eventual (configurable TTL)         |
| Eviction          | LRU / LFU based                     |

---

## 2. Why Do We Need a Cache? (~5 min)

### The Fundamental Problem

```
Client → App Server → Database
                        ↑
              Slow (10-50ms per query)
              Limited connections
              Expensive to scale
```

Without cache:
- **Latency bottleneck** — Database reads are 10-50ms; cache reads are <1ms.
- **Database overload** — Millions of identical reads hit the DB repeatedly.
- **Cost** — Scaling databases (vertical or horizontal) is expensive.

### What Does a Cache Give Us?

```
Client → App Server → Cache (hit?) → ✅ Return data    (~0.5ms)
                         ↓ (miss)
                       Database                         (~20ms)
                         ↓
                      Populate cache
```

| Metric               | Without Cache | With Cache |
|----------------------|---------------|------------|
| Read latency (p50)  | 15 ms         | 0.5 ms     |
| DB load              | 100%          | 10-20%     |
| Cost at scale        | Very High     | Moderate   |

### Types of Caching

```
┌──────────────────────────────────────────────────────┐
│                   Caching Layers                     │
├──────────────────────────────────────────────────────┤
│                                                      │
│  1. Client-Side Cache (Browser, Mobile App)          │
│     └─ HTTP Cache-Control, ETag, LocalStorage        │
│                                                      │
│  2. CDN Cache (Edge)                                 │
│     └─ CloudFront, Akamai, Cloudflare                │
│                                                      │
│  3. API Gateway / Reverse Proxy Cache                │
│     └─ Nginx, Varnish, Kong                          │
│                                                      │
│  4. Application-Level Cache (In-process)             │
│     └─ Guava Cache, Caffeine, ConcurrentHashMap      │
│                                                      │
│  5. Distributed Cache (Out-of-process) ← OUR FOCUS  │
│     └─ Redis, Memcached, Hazelcast                   │
│                                                      │
│  6. Database-Level Cache                             │
│     └─ MySQL Query Cache, PG Buffer Cache            │
│                                                      │
└──────────────────────────────────────────────────────┘
```

---

## 3. High-Level Architecture (~10 min)

### System Architecture Diagram

```
                            ┌──────────────┐
                            │   Clients    │
                            │ (Web/Mobile) │
                            └──────┬───────┘
                                   │
                            ┌──────▼───────┐
                            │  CDN / Edge  │
                            │    Cache     │
                            └──────┬───────┘
                                   │
                            ┌──────▼───────┐
                            │ Load Balancer│
                            └──────┬───────┘
                                   │
                   ┌───────────────┼───────────────┐
                   │               │               │
            ┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼──────┐
            │ App Server 1│ │App Server 2│ │App Server 3│
            │             │ │            │ │             │
            │ ┌─────────┐ │ │┌─────────┐ │ │ ┌─────────┐│
            │ │Local/L1  │ │ ││Local/L1 │ │ │ │Local/L1 ││
            │ │Cache     │ │ ││Cache    │ │ │ │Cache    ││
            │ │(Caffeine)│ │ ││(Caffeine│ │ │ │(Caffeine││
            │ └─────────┘ │ │└─────────┘ │ │ └─────────┘│
            └──────┬──────┘ └─────┬──────┘ └──────┬──────┘
                   │              │               │
                   └──────────────┼───────────────┘
                                  │
                   ┌──────────────▼──────────────┐
                   │    DISTRIBUTED CACHE (L2)    │
                   │                              │
                   │  ┌────────┐  ┌────────┐     │
                   │  │Redis   │  │Redis   │     │
                   │  │Primary │──│Replica │     │
                   │  │Node 1  │  │Node 1' │     │
                   │  └────────┘  └────────┘     │
                   │  ┌────────┐  ┌────────┐     │
                   │  │Redis   │  │Redis   │     │
                   │  │Primary │──│Replica │     │
                   │  │Node 2  │  │Node 2' │     │
                   │  └────────┘  └────────┘     │
                   │  ┌────────┐  ┌────────┐     │
                   │  │Redis   │  │Redis   │     │
                   │  │Primary │──│Replica │     │
                   │  │Node 3  │  │Node 3' │     │
                   │  └────────┘  └────────┘     │
                   │                              │
                   │  Data partitioned via        │
                   │  Consistent Hashing          │
                   └──────────────┬───────────────┘
                                  │
                   ┌──────────────▼──────────────┐
                   │     PRIMARY DATABASE         │
                   │  (PostgreSQL / MySQL / Mongo)│
                   │                              │
                   │  Source of Truth              │
                   └─────────────────────────────┘
```

### Two-Tier Caching: L1 + L2

| Layer | Technology     | Scope              | Latency   | Size      |
|-------|---------------|--------------------|-----------|-----------|
| L1    | Caffeine/Guava| Per JVM (in-process)| ~nanosec | 100MB-1GB |
| L2    | Redis Cluster | Shared across JVMs | ~0.5ms   | 10GB-1TB  |

**Flow:**
```
Request → Check L1 (local) → Hit? Return.
                            → Miss? Check L2 (Redis) → Hit? Populate L1, Return.
                                                     → Miss? Query DB → Populate L2 & L1, Return.
```

---

## 4. Caching Strategies / Patterns (~15 min)

This is the **core** of the interview. There are 5 major caching strategies. Each has distinct read/write paths.

---

### 4.1 Cache-Aside (Lazy Loading) ⭐ Most Common

```
                    READ PATH
                    ─────────
   App ──── 1. GET(key) ────► Cache
   App ◄─── 2. Cache Hit ──── Cache    → Return data ✅
        OR
   App ◄─── 2. Cache Miss ─── Cache
   App ──── 3. Query ────────► Database
   App ◄─── 4. Result ──────── Database
   App ──── 5. SET(key,val) ─► Cache    → Populate cache

                    WRITE PATH
                    ──────────
   App ──── 1. Write ─────────► Database
   App ──── 2. DELETE(key) ───► Cache   → Invalidate cache
```

**How it works:**
- **Application** is responsible for reading from and writing to the cache.
- On a **cache miss**, the app fetches from DB and populates the cache.
- On a **write**, the app writes to DB first, then **invalidates** (deletes) the cache entry.

**Pseudocode:**
```java
public User getUser(String userId) {
    // Step 1: Check cache
    User user = cache.get("user:" + userId);
    if (user != null) {
        return user;  // Cache HIT
    }

    // Step 2: Cache MISS — query database
    user = database.findUserById(userId);

    // Step 3: Populate cache with TTL
    if (user != null) {
        cache.set("user:" + userId, user, Duration.ofMinutes(30));
    }

    return user;
}

public void updateUser(String userId, User updatedUser) {
    // Step 1: Write to database
    database.updateUser(userId, updatedUser);

    // Step 2: Invalidate cache (don't update — delete!)
    cache.delete("user:" + userId);
}
```

**Pros:**
- ✅ Only requested data is cached (no wasted memory).
- ✅ Simple to implement.
- ✅ Cache failure doesn't break the app (graceful degradation).
- ✅ Works well for read-heavy workloads.

**Cons:**
- ❌ Cache miss = 3 round-trips (cache + DB + cache write) → higher latency on miss.
- ❌ Data can become stale if DB is updated by another service.
- ❌ Thundering herd problem on cache miss for hot keys.

**When to use:**
- Read-heavy workloads (80%+ reads).
- Data that doesn't change too frequently.
- Examples: User profiles, product catalog, configuration data.

---

### 4.2 Read-Through Cache

```
                    READ PATH
                    ─────────
   App ──── 1. GET(key) ─────────► Cache
   Cache ── 2. (on miss) Query ──► Database    ← Cache itself loads data!
   Cache ◄─ 3. Result ──────────── Database
   App ◄─── 4. Return data ──────── Cache
```

**How it works:**
- The **cache library/provider** is responsible for loading data on a miss.
- Application only talks to the cache — never directly to the DB for reads.
- The cache has a configured **loader function** that knows how to fetch from DB.

**Pseudocode:**
```java
// Configuration — the cache knows how to load data
LoadingCache<String, User> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofMinutes(30))
    .build(userId -> database.findUserById(userId));  // Loader function

// Usage — application just calls cache.get()
public User getUser(String userId) {
    return cache.get(userId);  // Auto-loads from DB on miss
}
```

**Pros:**
- ✅ Simpler application code — no cache miss handling logic.
- ✅ Cache manages loading, avoids duplicate DB queries for same key.

**Cons:**
- ❌ Cache library must support it (Caffeine, Guava, Ehcache do; plain Redis doesn't natively).
- ❌ First request for each key always has higher latency.
- ❌ Stale data unless combined with TTL or invalidation.

**When to use:**
- When you want the cache layer to abstract away the data-loading logic.
- Works great as an **L1 in-process cache** with Caffeine/Guava.

---

### 4.3 Write-Through Cache

```
                    WRITE PATH
                    ──────────
   App ──── 1. Write(key,val) ──► Cache
   Cache ── 2. Write ───────────► Database    ← Cache writes to DB synchronously
   App ◄─── 3. ACK ──────────────  Cache

                    READ PATH (same as Read-Through)
                    ──────────
   App ──── 1. GET(key) ────────► Cache → Hit? Return.
                                       → Miss? Load from DB.
```

**How it works:**
- Application writes to the **cache first**.
- The cache **synchronously** writes the data to the database.
- Combined with Read-Through, the cache becomes the single interface for all data operations.

**Pseudocode:**
```java
public void updateUser(String userId, User user) {
    // Write to cache → cache synchronously writes to DB
    cache.put("user:" + userId, user);
    // Internally, the cache calls: database.updateUser(userId, user);
}
```

**Pros:**
- ✅ Cache and DB are always in sync (strong consistency).
- ✅ No stale data — every write updates both.
- ✅ Simple mental model for developers.

**Cons:**
- ❌ **Higher write latency** — every write goes to both cache + DB (synchronous).
- ❌ If you write data that is rarely read, you're wasting cache space.
- ❌ Cache becomes a critical path — failure blocks writes.

**When to use:**
- When data consistency between cache and DB is critical.
- Combined with Read-Through for a complete solution.
- Examples: Financial data, inventory counts, session stores.

---

### 4.4 Write-Behind (Write-Back) Cache

```
                    WRITE PATH
                    ──────────
   App ──── 1. Write(key,val) ──► Cache
   App ◄─── 2. ACK (immediate!) ── Cache

                    ASYNC FLUSH (background)
                    ──────────
   Cache ── 3. Batch write ─────► Database    ← Asynchronous!
            (after delay or
             batch threshold)
```

**How it works:**
- Application writes to the **cache only** — gets immediate acknowledgment.
- The cache **asynchronously** (in the background) flushes dirty entries to the database.
- Writes can be **batched and coalesced** for efficiency.

**Pseudocode:**
```java
// Conceptual — the cache layer handles async persistence
public void updateUser(String userId, User user) {
    cache.put("user:" + userId, user);  // Returns immediately
    // Behind the scenes:
    // - Entry is marked as "dirty"
    // - A background thread periodically flushes dirty entries to DB
    // - Multiple writes to the same key are coalesced (only last value persisted)
}
```

**Pros:**
- ✅ **Extremely fast writes** — application doesn't wait for DB.
- ✅ Batching reduces DB write pressure.
- ✅ Write coalescing — 10 updates to the same key = 1 DB write.
- ✅ Excellent for write-heavy workloads.

**Cons:**
- ❌ **Risk of data loss** — if cache node crashes before flushing, writes are lost.
- ❌ Eventual consistency with the database.
- ❌ Complex to implement reliably.
- ❌ Debugging issues is harder (data may be in cache but not yet in DB).

**When to use:**
- Write-heavy workloads where some data loss is acceptable.
- Examples: Page view counters, analytics events, activity logs, social media likes.

---

### 4.5 Write-Around Cache

```
                    WRITE PATH
                    ──────────
   App ──── 1. Write ─────────► Database (directly, bypasses cache)

                    READ PATH (Cache-Aside)
                    ──────────
   App ──── 2. GET(key) ──────► Cache → Miss? Load from DB → Populate cache
```

**How it works:**
- Writes go **directly to the database**, bypassing the cache entirely.
- Data enters the cache only when it's **read** (lazy loading via Cache-Aside).

**Pros:**
- ✅ Cache is not polluted with data that may never be read.
- ✅ Good for write-once, read-maybe scenarios.

**Cons:**
- ❌ Recently written data will always result in a cache miss on first read.
- ❌ Higher read latency for recently written data.

**When to use:**
- When written data is not immediately read.
- Examples: Log ingestion, archival writes, batch imports.

---

### Strategy Comparison Matrix

```
┌─────────────────────┬──────────────┬──────────────┬─────────────┬──────────────┬──────────────┐
│     Strategy        │  Read Speed  │ Write Speed  │ Consistency │ Data Loss    │ Complexity   │
│                     │              │              │             │ Risk         │              │
├─────────────────────┼──────────────┼──────────────┼─────────────┼──────────────┼──────────────┤
│ Cache-Aside         │ Fast (hit)   │ Normal       │ Eventual    │ None         │ Low          │
│                     │ Slow (miss)  │              │             │              │              │
├─────────────────────┼──────────────┼──────────────┼─────────────┼──────────────┼──────────────┤
│ Read-Through        │ Fast (hit)   │ N/A          │ Eventual    │ None         │ Medium       │
│                     │ Slow (miss)  │              │             │              │              │
├─────────────────────┼──────────────┼──────────────┼─────────────┼──────────────┼──────────────┤
│ Write-Through       │ Fast         │ Slow         │ Strong      │ None         │ Medium       │
│                     │              │ (sync to DB) │             │              │              │
├─────────────────────┼──────────────┼──────────────┼─────────────┼──────────────┼──────────────┤
│ Write-Behind        │ Fast         │ Very Fast    │ Eventual    │ YES (crash)  │ High         │
│                     │              │ (async)      │             │              │              │
├─────────────────────┼──────────────┼──────────────┼─────────────┼──────────────┼──────────────┤
│ Write-Around        │ Slow (new)   │ Normal       │ Eventual    │ None         │ Low          │
│                     │ Fast (cached)│              │             │              │              │
└─────────────────────┴──────────────┴──────────────┴─────────────┴──────────────┴──────────────┘
```

### Choosing the Right Strategy — Decision Flowchart

```
                        ┌──────────────────────┐
                        │   What's your         │
                        │   workload type?       │
                        └──────────┬─────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
               Read-Heavy    Balanced       Write-Heavy
                    │              │              │
                    ▼              ▼              ▼
             Cache-Aside     Write-Through   Write-Behind
                  +               +               +
            Read-Through     Read-Through    Read-Through
                    │              │              │
                    │              │              │
            ┌───────▼──────┐      │       ┌──────▼───────┐
            │Can tolerate  │      │       │Can tolerate  │
            │stale data?   │      │       │data loss?    │
            │              │      │       │              │
            │Yes: TTL-based│      │       │Yes: Write-   │
            │    Cache-    │      │       │     Behind   │
            │    Aside     │      │       │              │
            │              │      │       │No: Write-    │
            │No: Add event-│      │       │    Through   │
            │   based      │      │       │              │
            │   invalidation      │       └──────────────┘
            └──────────────┘      │
                                  │
                           Strong consistency
                           needed → Write-Through
```

---

## 5. Cache Eviction Policies (~5 min)

When cache is full, which entries do we remove?

### Common Eviction Policies

```
┌─────────────────────────────────────────────────────────────────┐
│                    EVICTION POLICIES                            │
├─────────────┬───────────────────────────────────────────────────┤
│ LRU         │ Least Recently Used — evict the entry that       │
│             │ hasn't been accessed for the longest time.        │
│             │ ⭐ Most commonly used. Default in Redis.          │
│             │ Good for: General-purpose, temporal locality.     │
├─────────────┼───────────────────────────────────────────────────┤
│ LFU         │ Least Frequently Used — evict the entry          │
│             │ accessed the fewest number of times.              │
│             │ Good for: Workloads with stable hot keys.         │
│             │ Risk: New items get evicted too quickly.          │
├─────────────┼───────────────────────────────────────────────────┤
│ FIFO        │ First In, First Out — evict the oldest entry.    │
│             │ Simple but not always optimal.                    │
├─────────────┼───────────────────────────────────────────────────┤
│ TTL-Based   │ Time-To-Live — entries expire after a set        │
│             │ duration regardless of usage.                     │
│             │ Good for: Session data, tokens, temporary data.  │
├─────────────┼───────────────────────────────────────────────────┤
│ Random      │ Evict a random entry. Surprisingly effective     │
│             │ in some workloads. Very low overhead.             │
├─────────────┼───────────────────────────────────────────────────┤
│ W-TinyLFU   │ Window Tiny LFU — used by Caffeine cache.        │
│             │ Combines recency + frequency. Near-optimal        │
│             │ hit rates. Best in-process eviction policy.       │
└─────────────┴───────────────────────────────────────────────────┘
```

### Redis Eviction Policies (maxmemory-policy)

```
┌───────────────────────┬──────────────────────────────────────┐
│ Policy                │ Behavior                             │
├───────────────────────┼──────────────────────────────────────┤
│ noeviction            │ Return error on writes when full     │
│ allkeys-lru           │ LRU across ALL keys (most common)    │
│ allkeys-lfu           │ LFU across ALL keys                  │
│ allkeys-random        │ Random eviction across ALL keys      │
│ volatile-lru          │ LRU only on keys WITH TTL set        │
│ volatile-lfu          │ LFU only on keys WITH TTL set        │
│ volatile-random       │ Random among keys WITH TTL set       │
│ volatile-ttl          │ Evict keys closest to expiration     │
└───────────────────────┴──────────────────────────────────────┘
```

**My recommendation for most workloads:** `allkeys-lru` with explicit TTLs on all keys.

---

## 6. Distributed Cache Deep Dive (~10 min)

### 6.1 Why Distributed? (vs Local Cache)

| Aspect          | Local (In-Process) Cache   | Distributed Cache         |
|-----------------|---------------------------|---------------------------|
| Scope           | Single JVM                | Shared across all nodes   |
| Consistency     | Per-node (inconsistent)   | Single source of truth    |
| Size            | Limited by JVM heap       | Can be TBs across cluster |
| Latency         | Nanoseconds               | Sub-millisecond (network) |
| Failure domain  | Dies with the process     | Independent lifecycle     |
| Example         | Caffeine, Guava           | Redis, Memcached          |

### 6.2 Data Partitioning — Consistent Hashing

**Problem:** How do we distribute cache keys across N nodes?

**Naive approach:** `node = hash(key) % N`
- ❌ Adding/removing a node re-maps almost ALL keys → massive cache invalidation storm.

**Consistent Hashing:**
```
                    Node A
                   ╱      ╲
                  ╱        ╲
           Node D            Node B
                  ╲        ╱
                   ╲      ╱
                    Node C

     Hash Ring (0 to 2^32 - 1)
     ─────────────────────────
     Keys are hashed to a position on the ring.
     Walk clockwise to find the first node → that node owns the key.
```

```
Step 1: Hash each node to a position on the ring.
Step 2: Hash each key to a position on the ring.
Step 3: Walk clockwise from the key's position → first node encountered owns the key.

Adding a new node:
  - Only keys between the new node and its predecessor get re-mapped.
  - ~1/N keys affected (instead of ~ALL keys).

Virtual Nodes (vnodes):
  - Each physical node maps to K virtual nodes on the ring (e.g., K=150).
  - Ensures uniform distribution even with few physical nodes.
  - Prevents hot spots.
```

### 6.3 Replication

```
┌─────────────────────────────────────────────────┐
│           Redis Cluster Replication              │
│                                                  │
│   ┌──────────┐     ┌──────────┐                 │
│   │Primary 1 │────►│Replica 1 │  (async)        │
│   │Slots 0-  │     │          │                  │
│   │5460      │     └──────────┘                  │
│   └──────────┘                                   │
│                                                  │
│   ┌──────────┐     ┌──────────┐                 │
│   │Primary 2 │────►│Replica 2 │  (async)        │
│   │Slots 5461│     │          │                  │
│   │-10922    │     └──────────┘                  │
│   └──────────┘                                   │
│                                                  │
│   ┌──────────┐     ┌──────────┐                 │
│   │Primary 3 │────►│Replica 3 │  (async)        │
│   │Slots     │     │          │                  │
│   │10923-    │     └──────────┘                  │
│   │16383     │                                   │
│   └──────────┘                                   │
│                                                  │
│   Total: 16384 hash slots distributed across     │
│   primary nodes. Each primary replicates to 1+   │
│   replicas for fault tolerance.                  │
└─────────────────────────────────────────────────┘
```

**Redis Cluster uses hash slots (not consistent hashing ring):**
- 16384 hash slots.
- `slot = CRC16(key) % 16384`
- Each primary node owns a range of slots.
- Automatic failover: if a primary dies, its replica is promoted.

### 6.4 High Availability Patterns

```
Pattern 1: Primary-Replica with Sentinel (Redis Sentinel)
─────────────────────────────────────────────────────────
   ┌──────────┐          ┌──────────┐
   │ Primary  │──async──►│ Replica  │
   └──────────┘          └──────────┘
        │
   ┌────▼─────┐
   │ Sentinel │  ← Monitors primary. Auto-promotes replica on failure.
   │ (quorum) │
   └──────────┘

Pattern 2: Redis Cluster (for scale + HA)
─────────────────────────────────────────
   Data is sharded across multiple primaries.
   Each primary has replicas.
   Gossip protocol for health checks.
   Automatic failover within the cluster.

Pattern 3: Multi-Region / Active-Active
─────────────────────────────────────────
   ┌─────────────┐          ┌─────────────┐
   │ Redis Cluster│◄─CRDT──►│ Redis Cluster│
   │ Region: US  │          │ Region: EU  │
   └─────────────┘          └─────────────┘
   (Redis Enterprise supports this with CRDTs for conflict resolution)
```

---

## 7. Cache Consistency, Invalidation & Thundering Herd (~5 min)

### 7.1 The Two Hardest Problems in CS

> "There are only two hard things in Computer Science: cache invalidation and naming things."
> — Phil Karlton

### 7.2 Cache Invalidation Strategies

```
┌───────────────────────────────────────────────────────────────────┐
│              CACHE INVALIDATION STRATEGIES                        │
├────────────────────┬──────────────────────────────────────────────┤
│                    │                                              │
│ 1. TTL-Based       │ Set expiration on every key.                │
│                    │ Simplest. Guaranteed staleness bound.        │
│                    │ SET user:123 {...} EX 1800  (30 min TTL)    │
│                    │                                              │
│ 2. Event-Driven    │ Publish invalidation events on write.       │
│    Invalidation    │ Services subscribe and delete cache keys.   │
│                    │                                              │
│                    │   Service A writes to DB                    │
│                    │         │                                    │
│                    │         ▼                                    │
│                    │   Publish event to Kafka/SNS                │
│                    │         │                                    │
│                    │         ▼                                    │
│                    │   Cache invalidation consumer               │
│                    │   deletes affected cache keys               │
│                    │                                              │
│ 3. Database CDC    │ Use Change Data Capture (Debezium)          │
│    (Change Data    │ to stream DB changes → invalidate cache.    │
│     Capture)       │ Most reliable for multi-service setups.     │
│                    │                                              │
│ 4. Version-Based   │ Include a version/hash in the cache key.   │
│                    │ e.g., "product:123:v7" — update version     │
│                    │ on write, old key naturally becomes stale.   │
│                    │                                              │
└────────────────────┴──────────────────────────────────────────────┘
```

### 7.3 Delete vs Update Cache on Write?

**Always prefer DELETE over UPDATE:**

```
Why DELETE (Cache-Aside Pattern)?
──────────────────────────────────

  Thread A: UPDATE user:123 in DB   (value = "Alice v2")
  Thread B: UPDATE user:123 in DB   (value = "Alice v3")

  Race condition with cache UPDATE:
    Thread A: SET cache user:123 = "Alice v2"
    Thread B: SET cache user:123 = "Alice v3"
    Thread A: (delayed) SET cache user:123 = "Alice v2"   ← STALE! 💀

  With cache DELETE:
    Thread A: DELETE cache user:123
    Thread B: DELETE cache user:123
    Next read: Cache miss → Load "Alice v3" from DB → Correct! ✅
```

### 7.4 Thundering Herd Problem

**Problem:** When a hot key expires, thousands of concurrent requests all get a cache miss and slam the database simultaneously.

```
                    Key "product:hot" expires
                            │
            ┌───────────────┼───────────────┐
            │               │               │
       Thread 1        Thread 2        Thread 3      ... Thread 1000
       Cache Miss      Cache Miss      Cache Miss
            │               │               │
            ▼               ▼               ▼
        ┌──────────────────────────────────────┐
        │          DATABASE                     │
        │    1000 identical queries!!! 💥       │
        └──────────────────────────────────────┘
```

**Solutions:**

```
1. Mutex / Distributed Lock (Singleflight)
───────────────────────────────────────────
   Thread 1: Cache miss → Acquire lock → Query DB → Set cache → Release lock
   Thread 2-1000: Cache miss → Lock exists → Wait/retry → Cache HIT ✅

   Implementation with Redis:
   SET lock:product:hot NX EX 5   ← NX = only if not exists, EX = 5 sec expiry

2. Background Refresh (Proactive Reloading)
───────────────────────────────────────────
   Don't wait for TTL expiry.
   Refresh cache in the background BEFORE it expires.
   e.g., TTL = 30 min, refresh at 25 min (5 min before expiry).

3. Stale-While-Revalidate
───────────────────────────────────────────
   Return stale data to users while refreshing in the background.
   Similar to HTTP "stale-while-revalidate" header.

4. Request Coalescing
───────────────────────────────────────────
   Deduplicate identical in-flight requests.
   Only one DB query per unique key, share result with all waiters.
   Libraries: Caffeine (built-in), Go singleflight.
```

### 7.5 Cache Stampede Prevention — Pseudocode

```java
public User getUser(String userId) {
    String cacheKey = "user:" + userId;
    String lockKey = "lock:" + cacheKey;

    // Step 1: Check cache
    User user = cache.get(cacheKey);
    if (user != null) {
        return user;
    }

    // Step 2: Try to acquire distributed lock
    boolean lockAcquired = redis.set(lockKey, "1", SetParams.nx().ex(5));

    if (lockAcquired) {
        try {
            // Step 3: Double-check cache (another thread might have populated it)
            user = cache.get(cacheKey);
            if (user != null) return user;

            // Step 4: Query DB and populate cache
            user = database.findUserById(userId);
            cache.set(cacheKey, user, Duration.ofMinutes(30));
            return user;
        } finally {
            redis.del(lockKey);
        }
    } else {
        // Step 5: Wait and retry — another thread is loading
        Thread.sleep(50);
        return getUser(userId);  // Retry — should be a cache hit now
    }
}
```

---

## 8. Technology Choices (~3 min)

### Redis vs Memcached

```
┌─────────────────────┬──────────────────────┬──────────────────────┐
│ Feature             │ Redis                │ Memcached            │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Data Structures     │ Strings, Hashes,     │ Strings only         │
│                     │ Lists, Sets, Sorted  │ (key-value)          │
│                     │ Sets, Streams, HLL   │                      │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Persistence         │ RDB + AOF            │ No (memory only)     │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Clustering          │ Redis Cluster        │ Client-side sharding │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Replication         │ Built-in Primary-    │ Not built-in         │
│                     │ Replica              │                      │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Pub/Sub             │ Yes                  │ No                   │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Lua Scripting       │ Yes                  │ No                   │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Multi-threading     │ Single-threaded      │ Multi-threaded       │
│                     │ (I/O threads in 6.0) │                      │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Max value size      │ 512 MB               │ 1 MB (default)       │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Transactions        │ MULTI/EXEC           │ CAS (check-and-set)  │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ Use case            │ General purpose,     │ Simple key-value     │
│                     │ session store,       │ caching, session     │
│                     │ leaderboards, queues │ caching              │
└─────────────────────┴──────────────────────┴──────────────────────┘
```

**My choice: Redis** (99% of the time) because:
- Richer data structures → fewer application-level workarounds.
- Built-in replication and clustering.
- Persistence options for crash recovery.
- Pub/Sub for cache invalidation events.

### Other Technologies

| Technology      | Best For                                          |
|-----------------|--------------------------------------------------|
| Redis           | General-purpose distributed cache, session store  |
| Memcached       | Simple, high-throughput key-value caching          |
| Hazelcast       | Embedded Java cache, distributed computing         |
| Apache Ignite   | In-memory computing, SQL cache, distributed joins  |
| Caffeine        | In-process L1 cache for JVM apps (best hit rates)  |
| Ehcache         | JVM cache with tiered storage (heap+offheap+disk)  |
| AWS ElastiCache | Managed Redis/Memcached on AWS                     |
| Azure Cache     | Managed Redis on Azure                             |

---

## 9. Wrap-up & Trade-offs (~2 min)

### Summary: What I Would Use in Production

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│   RECOMMENDED ARCHITECTURE                                     │
│                                                                │
│   L1: Caffeine (in-process)                                    │
│       ├─ TTL: 5 min                                            │
│       ├─ Max size: 10,000 entries                              │
│       └─ Eviction: W-TinyLFU (default in Caffeine)            │
│                                                                │
│   L2: Redis Cluster (distributed)                              │
│       ├─ TTL: 30 min                                           │
│       ├─ Eviction: allkeys-lru                                 │
│       ├─ 3 primaries + 3 replicas (minimum)                   │
│       ├─ Persistence: RDB snapshots every 15 min              │
│       └─ Monitoring: Redis Sentinel or Cluster auto-failover  │
│                                                                │
│   Strategy: Cache-Aside (primary)                              │
│       ├─ Read-Through via Caffeine loader for L1              │
│       ├─ TTL-based expiration for staleness bound             │
│       ├─ Event-driven invalidation via Kafka for consistency  │
│       └─ Distributed lock for thundering herd protection      │
│                                                                │
│   Invalidation:                                                │
│       ├─ TTL as safety net (guaranteed max staleness)          │
│       ├─ Explicit DELETE on write                              │
│       └─ CDC (Debezium) for cross-service invalidation        │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Key Trade-offs to Mention in Interview

| Trade-off                  | Decision Point                                     |
|---------------------------|----------------------------------------------------|
| **Consistency vs Speed**  | Write-Through = consistent but slow writes          |
|                           | Write-Behind = fast writes but risk of data loss    |
| **Memory vs Hit Rate**    | Bigger cache = better hit rate but higher cost       |
| **TTL Length**            | Short TTL = fresher data but more DB hits            |
|                           | Long TTL = fewer DB hits but staler data             |
| **L1 vs L2**             | L1 = faster but inconsistent across nodes            |
|                           | L2 = consistent but network hop                      |
| **Complexity vs Perf**   | Simple Cache-Aside vs complex Write-Behind           |

### Common Interview Follow-up Questions & Answers

**Q: How do you handle cache in a microservices architecture?**
> Each service owns its cache for its data. Cross-service data should be fetched via API, not shared cache. Use event-driven invalidation (Kafka) when Service A's data changes affect Service B's cache.

**Q: What if Redis goes down?**
> - L1 cache (Caffeine) still serves requests.
> - Application falls back to database (circuit breaker pattern).
> - Redis Sentinel or Cluster auto-promotes replica.
> - Graceful degradation, not total failure.

**Q: How do you monitor cache health?**
> - **Hit ratio** — should be >90%. If lower, review key design and TTL.
> - **Memory usage** — alert at 80% capacity.
> - **Eviction rate** — high evictions mean cache is undersized.
> - **Latency p99** — should stay <1ms for Redis.
> - **Connection pool** — monitor exhaustion.
> Tools: Redis INFO command, Prometheus + Grafana, Datadog.

**Q: How do you size the cache?**
> - Start with working set analysis: how many unique keys are accessed in a given time window?
> - Rule of thumb: cache should hold the "hot" 20% of data (Pareto principle — 80/20 rule).
> - Monitor hit rates and eviction rates, then adjust.

---

## Appendix: Quick Reference Card

```
┌────────────────────────────────────────────────────────────────┐
│                    CACHING CHEAT SHEET                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Cache-Aside    → App manages cache. Most common. Read-heavy.  │
│  Read-Through   → Cache auto-loads on miss. Cleaner app code.  │
│  Write-Through  → Cache writes to DB synchronously. Consistent.│
│  Write-Behind   → Cache writes to DB async. Fast. Risk of loss.│
│  Write-Around   → Bypass cache on write. Good for cold writes. │
│                                                                │
│  LRU     → Evict least recently used. General purpose.         │
│  LFU     → Evict least frequently used. Stable hot keys.       │
│  TTL     → Time-based expiration. Always set one!              │
│                                                                │
│  Thundering Herd → Use distributed lock / singleflight.        │
│  Invalidation    → Prefer DELETE over UPDATE.                  │
│  Consistency     → TTL + Event-driven + CDC.                   │
│                                                                │
│  Redis > Memcached for most use cases.                         │
│  Caffeine = best in-process cache for JVM.                     │
│  L1 (local) + L2 (distributed) = optimal architecture.        │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

*This document simulates a complete 1-hour HLD interview on Distributed Cache & Caching Strategies, covering architecture, strategies, eviction, consistency, and real-world trade-offs.*

