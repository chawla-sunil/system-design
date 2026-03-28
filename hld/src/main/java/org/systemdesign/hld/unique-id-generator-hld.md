# Design a Unique ID Generator in Distributed Systems (KGS) — High-Level Design

> **Simulated Interview Duration:** 1–1.5 hours  
> **Candidate Experience Level:** 5–6 years  
> **Interviewer Prompt:** "Design a Key Generation Service / Unique ID Generator for Distributed Systems."  
> **Context:** This is a natural follow-up from the URL Shortener design. The interviewer wants to go deeper into how we generate globally unique, non-colliding keys at scale.

---

## Table of Contents

1. [Step 1 — Clarify Requirements (5–7 min)](#step-1--clarify-requirements)
2. [Step 2 — Back-of-the-Envelope Estimation (5–7 min)](#step-2--back-of-the-envelope-estimation)
3. [Step 3 — Approach 1: UUID (Universally Unique Identifier)](#step-3--approach-1-uuid)
4. [Step 4 — Approach 2: Database Auto-Increment (Multi-Master)](#step-4--approach-2-database-auto-increment-multi-master)
5. [Step 5 — Approach 3: Ticket Server (Flickr's Approach)](#step-5--approach-3-ticket-server)
6. [Step 6 — Approach 4: Twitter Snowflake ⭐ Recommended](#step-6--approach-4-twitter-snowflake--recommended)
7. [Step 7 — Approach 5: Pre-Generated Key Service (KGS)](#step-7--approach-5-pre-generated-key-service-kgs)
8. [Step 8 — Approach 6: ULID & Other Modern Alternatives](#step-8--approach-6-ulid--other-modern-alternatives)
9. [Step 9 — Comparison Matrix — All Approaches](#step-9--comparison-matrix--all-approaches)
10. [Step 10 — Deep Dive: Twitter Snowflake Architecture](#step-10--deep-dive-twitter-snowflake-architecture)
11. [Step 11 — Deep Dive: KGS Architecture](#step-11--deep-dive-kgs-architecture)
12. [Step 12 — High-Level Architecture (Combined)](#step-12--high-level-architecture-combined)
13. [Step 13 — Scaling, Reliability & Fault Tolerance](#step-13--scaling-reliability--fault-tolerance)
14. [Step 14 — Summary & Trade-offs](#step-14--summary--trade-offs)
15. [Bonus — Interviewer Questions](#bonus--interviewer-questions)

---

## Step 1 — Clarify Requirements

> **"Before I start designing, I'd like to understand the exact use case and constraints."**

### Functional Requirements

| # | Requirement | Notes |
|---|-------------|-------|
| FR-1 | **Generate unique IDs** — Every ID must be globally unique across all nodes, all time. | Core — zero collisions |
| FR-2 | **Numerical IDs** — IDs should be numeric (fit in 64-bit integer). | Important for DB indexing, sorting |
| FR-3 | **IDs are sortable by time** — IDs generated later should be larger (roughly). | Critical for DB write performance (B+ tree friendly) |
| FR-4 | **IDs should be 64-bit** — Fixed length, compact. | Fits in a `BIGINT` column |
| FR-5 | **High throughput** — Able to generate 10,000+ IDs per second per node. | Distributed workloads |

### Non-Functional Requirements

| # | Requirement | Target |
|---|-------------|--------|
| NFR-1 | **High Availability** | 99.99% — ID generation cannot be a bottleneck |
| NFR-2 | **Low Latency** | < 1 ms per ID generation (p99) |
| NFR-3 | **Scalability** | Scale to 100+ nodes generating IDs concurrently |
| NFR-4 | **No Coordination (ideally)** | Nodes should generate IDs independently without a central lock |
| NFR-5 | **Monotonically increasing (per node)** | IDs from the same node are strictly increasing |
| NFR-6 | **Roughly time-ordered (globally)** | IDs across nodes are approximately ordered by time |

### Clarifying Questions to Ask the Interviewer

| Question | Why It Matters |
|----------|---------------|
| "Do IDs need to be strictly sequential (1, 2, 3, ...)?" | If yes → very different design (centralized counter). Usually NO. |
| "Is 64-bit sufficient, or do we need 128-bit?" | 64-bit is standard for most systems (MySQL BIGINT, Java long). |
| "Do IDs need to be sortable by creation time?" | If yes → eliminates UUID. Snowflake-style is ideal. |
| "What's the expected ID generation rate?" | 10K/sec vs 1M/sec changes the design significantly. |
| "Is it okay if IDs have small gaps?" | Usually yes. No system guarantees gap-free at scale. |
| "Do we need to extract metadata from the ID (timestamp, machine)?" | If yes → Snowflake gives this for free. |

### Use Cases That Need Distributed ID Generation

| System | Why |
|--------|-----|
| URL Shortener (TinyURL) | Need unique short keys, non-guessable |
| Twitter / Social Media | Tweet IDs, post IDs — time-ordered |
| E-commerce (Amazon) | Order IDs, transaction IDs |
| Databases (Sharded) | Primary keys across shards |
| Messaging (WhatsApp) | Message IDs — ordered per conversation |
| Logging / Tracing | Trace IDs, span IDs — correlation across services |

---

## Step 2 — Back-of-the-Envelope Estimation

> **"Let me do some quick math to size the system."**

### Throughput

| Metric | Value |
|--------|-------|
| Target ID generation rate | **10,000 IDs/sec** (per node) |
| Number of nodes | **100 nodes** |
| Total system-wide rate | **1,000,000 IDs/sec** |
| IDs per day | 1M × 86,400 = **~86 billion IDs/day** |
| IDs per year | **~31.5 trillion IDs/year** |

### ID Space — How Many Bits Do We Need?

| Bits | Max Value | Enough? |
|------|-----------|---------|
| 32-bit | 4.29 billion | ❌ Exhausted in minutes at 1M/sec |
| 64-bit | 9.2 × 10^18 (9.2 quintillion) | ✅ Lasts for millennia |
| 128-bit (UUID) | 3.4 × 10^38 | ✅ Overkill but works |

✅ **64-bit is the sweet spot** — fits in a single database column, efficient for indexing, and provides more than enough ID space.

### Storage Per ID

| Metric | Value |
|--------|-------|
| 64-bit ID | **8 bytes** |
| At 1M IDs/sec for 1 year | 31.5T × 8B = **~252 TB** (just for IDs) |

> IDs themselves are tiny. The data they identify (rows, documents) is what consumes storage.

---

## Step 3 — Approach 1: UUID (Universally Unique Identifier)

> **"The simplest approach is UUIDs. Let me discuss why it works and where it falls short."**

### How It Works

- Each node generates a **128-bit UUID** independently — no coordination needed.
- UUID v4 (random) example: `550e8400-e29b-41d4-a716-446655440000`
- UUID v1 (time-based): includes timestamp + MAC address.
- UUID v7 (newest, RFC 9562): time-ordered + random — best of both worlds.

```
Node 1 → UUID.randomUUID() → "550e8400-e29b-41d4-a716-446655440000"
Node 2 → UUID.randomUUID() → "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
Node 3 → UUID.randomUUID() → "f47ac10b-58cc-4372-a567-0e02b2c3d479"
```

### UUID Versions Comparison

| Version | Based On | Time-Ordered? | Uniqueness | Notes |
|---------|----------|---------------|------------|-------|
| v1 | Timestamp + MAC | ✅ Yes | ✅ Very high | Leaks MAC address (privacy concern) |
| v4 | Random | ❌ No | ✅ Very high | Most commonly used |
| v6 | Reordered v1 | ✅ Yes | ✅ Very high | Better DB indexing than v1 |
| v7 | Unix timestamp + random | ✅ Yes | ✅ Very high | **Best for databases** (RFC 9562) |

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Zero coordination — each node generates independently | ❌ **128-bit (16 bytes)** — doesn't fit in 64-bit |
| ✅ Virtually zero collision probability | ❌ **Not sortable** (v4) — horrible for B+ tree indexes |
| ✅ Simple to implement | ❌ **Long** — 36 chars as string, bad for URLs |
| ✅ Built into every language/framework | ❌ **Not human-readable** |
| | ❌ **Poor DB performance** — random inserts cause page splits in B+ trees |

### Collision Probability

For UUID v4 (122 random bits):
```
To have a 50% chance of collision, you'd need to generate:
  ~2.71 × 10^18 UUIDs (2.71 quintillion)
  
At 1 billion UUIDs/sec → takes ~86 years to reach 50% collision probability.
```

### Verdict

> ❌ **Not suitable** if we need 64-bit, sortable IDs.  
> ✅ **Good** for distributed tracing, correlation IDs, or when sortability doesn't matter.  
> ✅ **UUID v7** is much better for DB indexing (time-ordered), but still 128-bit.

---

## Step 4 — Approach 2: Database Auto-Increment (Multi-Master)

> **"What if we use the database's native auto-increment but across multiple masters?"**

### How It Works — Single Master

```
INSERT INTO orders (data) VALUES ('...');
-- DB assigns id = 1, 2, 3, 4, 5, ...
```

**Problem:** Single point of failure. Single DB becomes a bottleneck.

### Multi-Master Replication

Use **N database servers**, each auto-incrementing with a different **offset and step**.

```
Server 1: starts at 1, increments by 3 → 1, 4, 7, 10, 13, ...
Server 2: starts at 2, increments by 3 → 2, 5, 8, 11, 14, ...
Server 3: starts at 3, increments by 3 → 3, 6, 9, 12, 15, ...
```

**MySQL configuration:**
```sql
-- Server 1
SET @@auto_increment_offset = 1;
SET @@auto_increment_increment = 3;

-- Server 2
SET @@auto_increment_offset = 2;
SET @@auto_increment_increment = 3;

-- Server 3
SET @@auto_increment_offset = 3;
SET @@auto_increment_increment = 3;
```

```
         ┌──────────────────────────────────────────┐
         │           Load Balancer / Router          │
         └───────┬──────────────┬───────────────┬───┘
                 │              │               │
                 ▼              ▼               ▼
          ┌──────────┐  ┌──────────┐    ┌──────────┐
          │  MySQL 1 │  │  MySQL 2 │    │  MySQL 3 │
          │ offset=1 │  │ offset=2 │    │ offset=3 │
          │ step=3   │  │ step=3   │    │ step=3   │
          │          │  │          │    │          │
          │ 1,4,7,10 │  │ 2,5,8,11│    │ 3,6,9,12│
          └──────────┘  └──────────┘    └──────────┘
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Simple to understand and implement | ❌ **Hard to scale** — adding a new server requires changing step on ALL servers |
| ✅ Numeric, 64-bit IDs | ❌ **Not time-sorted across servers** — ID 7 (server 1) might be created after ID 8 (server 2) |
| ✅ No single point of failure (multi-master) | ❌ **Tight coupling** to DB — DB is the bottleneck |
| ✅ IDs are unique within the cluster | ❌ **Clock/ordering issues** if servers process at different rates |
| | ❌ **Doesn't work well across data centers** |

### Verdict

> ⚠️ **Works for small-medium scale** (< 10 DB servers), but doesn't scale elegantly. Adding/removing nodes is painful. Not suitable for dynamic, cloud-native environments.

---

## Step 5 — Approach 3: Ticket Server (Flickr's Approach)

> **"Flickr solved this elegantly with a dedicated Ticket Server. Let me explain."**

### How It Works

A **centralized Ticket Server** is a single-purpose database that ONLY generates IDs.

```sql
-- Ticket Server (MySQL)
CREATE TABLE tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stub CHAR(1) NOT NULL,
    UNIQUE KEY (stub)
);

-- To generate a new ID:
REPLACE INTO tickets (stub) VALUES ('a');
SELECT LAST_INSERT_ID();  -- Returns the new unique ID
```

**Why `REPLACE INTO`?**
- `REPLACE` deletes the existing row with `stub = 'a'` and inserts a new one.
- This always increments the auto-increment counter.
- The table never has more than 1 row → tiny footprint.

```
   ┌──────────────┐         ┌──────────────────────┐
   │  App Server 1 │ ──────→ │    Ticket Server 1   │
   │              │         │  (MySQL, offset=1,    │
   │              │         │   step=2)             │
   ├──────────────┤         │  → 1, 3, 5, 7, 9 ... │
   │  App Server 2 │ ──────→ └──────────────────────┘
   │              │
   │              │         ┌──────────────────────┐
   │  App Server 3 │ ──────→ │    Ticket Server 2   │
   │              │         │  (MySQL, offset=2,    │
   └──────────────┘         │   step=2)             │
                            │  → 2, 4, 6, 8, 10 ...│
                            └──────────────────────┘
```

### Flickr's Dual Ticket Server Setup

Flickr runs **two ticket servers** for high availability:
- Ticket Server 1: odd IDs (1, 3, 5, 7, ...)
- Ticket Server 2: even IDs (2, 4, 6, 8, ...)
- App servers round-robin between the two.
- If one goes down, the other continues (with small gaps — acceptable).

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Simple to implement | ❌ **Single point of failure** (mitigated with dual servers) |
| ✅ Numeric, 64-bit, unique IDs | ❌ **Not time-ordered across ticket servers** |
| ✅ Easy to understand and debug | ❌ **Network round trip** for every ID (can batch to mitigate) |
| ✅ Works well for medium scale | ❌ **Ticket server is a bottleneck** at very high throughput |
| ✅ Battle-tested (Flickr used this for years) | ❌ **Doesn't scale to 100+ nodes** independently |

### Optimization: Batch ID Allocation

Instead of requesting 1 ID at a time:
```
App Server → "Give me 1000 IDs"
Ticket Server → "Here: 5001 to 6000"
App Server caches locally and dispenses from the batch.
```

This reduces network calls by **1000x** and makes the ticket server far less of a bottleneck.

### Verdict

> ✅ **Good for medium scale.** Simple, proven. But has a ceiling due to centralized nature. Batching helps a lot. Not ideal for globally distributed systems.

---

## Step 6 — Approach 4: Twitter Snowflake ⭐ Recommended

> **"This is the industry-standard approach for generating unique, time-sorted, distributed IDs. Twitter created Snowflake for this exact problem."**

### Core Idea

Divide a **64-bit ID** into sections, each encoding different information:

```
┌──────────────────────────────────────────────────────────────────┐
│                        64-bit Snowflake ID                       │
├─────┬──────────────────────────┬────────────┬────────────────────┤
│  1  │         41 bits          │  10 bits   │     12 bits        │
│ bit │       Timestamp          │ Machine ID │   Sequence Number  │
│     │     (milliseconds)       │ (Node ID)  │   (per ms counter) │
│  0  │  since custom epoch      │ datacenter │                    │
│     │                          │ + worker   │                    │
├─────┼──────────────────────────┼────────────┼────────────────────┤
│ Sign│      Time Component      │   Where    │     Which one      │
│ bit │      (WHEN)              │  (WHO)     │    (ORDER)         │
└─────┴──────────────────────────┴────────────┴────────────────────┘
```

### Bit Allocation Breakdown

| Section | Bits | Purpose | Range |
|---------|------|---------|-------|
| **Sign bit** | 1 | Always 0 (positive number) | — |
| **Timestamp** | 41 | Milliseconds since custom epoch | 2^41 ms ≈ **69.7 years** |
| **Datacenter ID** | 5 | Identifies the datacenter | 2^5 = **32 datacenters** |
| **Machine/Worker ID** | 5 | Identifies the machine within a datacenter | 2^5 = **32 machines per DC** |
| **Sequence Number** | 12 | Counter within the same millisecond | 2^12 = **4,096 IDs per ms per machine** |

### Capacity

| Metric | Value |
|--------|-------|
| Max IDs per millisecond per machine | **4,096** |
| Max IDs per second per machine | **4,096,000** (~4M/sec) |
| Max machines | 32 DCs × 32 machines = **1,024 nodes** |
| Max total IDs per second (all nodes) | 1,024 × 4,096,000 = **~4 billion/sec** |
| Lifespan (41-bit timestamp) | **~69.7 years** from the custom epoch |

### ID Generation Algorithm (Pseudocode)

```
class SnowflakeIDGenerator:
    
    EPOCH = 1640995200000  // Custom epoch: Jan 1, 2022 00:00:00 UTC (in ms)
    
    TIMESTAMP_BITS  = 41
    DATACENTER_BITS = 5
    MACHINE_BITS    = 5
    SEQUENCE_BITS   = 12
    
    MAX_SEQUENCE = (1 << 12) - 1  // 4095
    MAX_MACHINE  = (1 << 5) - 1   // 31
    MAX_DC       = (1 << 5) - 1   // 31
    
    // Bit shift amounts
    TIMESTAMP_SHIFT  = DATACENTER_BITS + MACHINE_BITS + SEQUENCE_BITS  // 22
    DATACENTER_SHIFT = MACHINE_BITS + SEQUENCE_BITS                    // 17
    MACHINE_SHIFT    = SEQUENCE_BITS                                   // 12
    
    state:
        datacenterId: int
        machineId: int
        sequence: int = 0
        lastTimestamp: long = -1
    
    function generateId():
        currentTimestamp = currentTimeMillis()
        
        // ❌ Clock moved backwards — CRITICAL ERROR
        if currentTimestamp < lastTimestamp:
            throw ClockMovedBackwardsException(
                "Clock moved backwards by ${lastTimestamp - currentTimestamp} ms"
            )
        
        // Same millisecond — increment sequence
        if currentTimestamp == lastTimestamp:
            sequence = (sequence + 1) & MAX_SEQUENCE  // Wrap around at 4096
            if sequence == 0:
                // Sequence exhausted for this ms — wait for next millisecond
                currentTimestamp = waitForNextMillis(lastTimestamp)
        else:
            // New millisecond — reset sequence
            sequence = 0
        
        lastTimestamp = currentTimestamp
        
        // Compose the 64-bit ID
        id = ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
           | (datacenterId << DATACENTER_SHIFT)
           | (machineId << MACHINE_SHIFT)
           | sequence
        
        return id
    
    function waitForNextMillis(lastTs):
        ts = currentTimeMillis()
        while ts <= lastTs:
            ts = currentTimeMillis()
        return ts
```

### Example ID Generation

```
Timestamp:     1711612200000 ms (March 28, 2026 10:30:00 UTC)
Custom Epoch:  1640995200000 ms (Jan 1, 2022)
Elapsed:       70617000000 ms

Datacenter ID: 5  (binary: 00101)
Machine ID:    12 (binary: 01100)
Sequence:      0  (binary: 000000000000)

ID (binary): 0 | 00000001000001110100011110110011000000000 | 00101 | 01100 | 000000000000
ID (decimal): ~296,207,011,160,064
```

### Extracting Metadata from a Snowflake ID

```
Given ID = 296207011160064:

timestamp    = (id >> 22) + EPOCH       → March 28, 2026 10:30:00 UTC
datacenterId = (id >> 17) & 0x1F        → 5
machineId    = (id >> 12) & 0x1F        → 12
sequence     = id & 0xFFF               → 0
```

> 💡 **This is a huge advantage** — you can extract the creation time directly from the ID without a DB lookup!

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ **64-bit** — fits in BIGINT | ❌ **Clock dependency** — relies on NTP synchronized clocks |
| ✅ **Time-sorted** — great for DB indexing | ❌ **Clock backward** — if clock goes backwards, IDs break |
| ✅ **No coordination** — each node generates independently | ❌ **Not truly random** — somewhat predictable (machine ID visible) |
| ✅ **High throughput** — 4M IDs/sec per node | ❌ **Machine ID assignment** — need a mechanism to assign unique worker IDs |
| ✅ **Metadata embedded** — extract timestamp from ID | ❌ **IDs are NOT globally sequential** — only per-node sequential |
| ✅ **Battle-tested** — Twitter, Discord, Instagram use variants | |

### Verdict

> ⭐ **Best general-purpose approach** for distributed ID generation. Handles the URL shortener use case (after Base62 encoding) and virtually every other distributed system need. The clock-backwards issue is manageable with proper NTP configuration and monitoring.

---

## Step 7 — Approach 5: Pre-Generated Key Service (KGS)

> **"For use cases like URL shorteners where we need random, non-guessable keys (not numeric IDs), a KGS is ideal."**

### How It Works

1. A dedicated **Key Generation Service** pre-generates millions of unique keys offline.
2. Keys are stored in a database with two logical pools: **unused** and **used**.
3. When an application needs a key, it requests one (or a batch) from KGS.
4. KGS marks the key as used and returns it.

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Key Generation Service                         │
│                                                                     │
│   ┌────────────────────────┐    ┌────────────────────────┐         │
│   │    UNUSED KEYS POOL    │    │     USED KEYS POOL     │         │
│   │                        │    │                        │         │
│   │  Ab3xK9p               │    │  Zk8mN2q  (assigned)  │         │
│   │  Qw7rT4s               │    │  Lp5vB8d  (assigned)  │         │
│   │  Mn2pF6j               │    │  Hy9cR3w  (assigned)  │         │
│   │  Xc4dG1k               │    │  ...                  │         │
│   │  Rz9bH5m               │    │                        │         │
│   │  ...                   │    │                        │         │
│   │  (millions of keys)    │    │                        │         │
│   └────────────────────────┘    └────────────────────────┘         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Generation Strategies

#### Strategy A: Exhaustive Permutation

Pre-generate ALL possible 7-character Base62 keys:
```
Total keys = 62^7 = 3,521,614,606,208 (~3.5 trillion)
Storage: 3.5T × 7 bytes = ~24.6 TB
```
> ❌ Too many to store. We only need ~2 billion keys for 5 years. 

#### Strategy B: Random Generation + Dedup

```
function generateKeys(batchSize):
    keys = []
    while keys.size < batchSize:
        key = randomBase62String(length=7)
        if not existsInDB(key):
            keys.add(key)
            insertIntoDB(key, status='unused')
    return keys
```

> ✅ More practical. Generate a few hundred million keys ahead of time.

#### Strategy C: Hash-Based Seeded Generation

```
function generateKeys(startSeed, count):
    for i in range(startSeed, startSeed + count):
        hash = SHA256(toString(i))
        key = base62Encode(hash[:42bits])  // first 42 bits → 7 chars
        insertIntoDB(key, status='unused')
```

> ✅ Deterministic and reproducible. Each seed maps to one key.

### KGS Database Schema

```sql
CREATE TABLE key_pool (
    key_value   VARCHAR(7) PRIMARY KEY,
    status      ENUM('unused', 'used') DEFAULT 'unused',
    assigned_to VARCHAR(64) NULL,      -- which app server claimed it
    assigned_at TIMESTAMP NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient batch fetching of unused keys
CREATE INDEX idx_status ON key_pool(status);
```

### Batch Allocation Protocol

Instead of one key at a time, app servers request **batches**:

```
┌──────────────┐                          ┌──────────────┐
│  App Server  │  ── "Give me 1000 keys" → │     KGS      │
│              │                           │              │
│              │  ← [Qw7r, Ab3x, Mn2p...] │              │
│              │    (1000 keys returned)    │              │
│              │                           │              │
│  Local Key   │                           │  DB: mark    │
│  Buffer:     │                           │  1000 keys   │
│  [Qw7r,     │                           │  as 'used'   │
│   Ab3x,     │                           │              │
│   Mn2p,     │                           │              │
│   ...]      │                           │              │
└──────────────┘                          └──────────────┘
```

**Atomic Batch Claim (MySQL example):**
```sql
-- Claim a batch of 1000 unused keys atomically
START TRANSACTION;

SELECT key_value FROM key_pool 
WHERE status = 'unused' 
LIMIT 1000 
FOR UPDATE SKIP LOCKED;     -- Crucial: skip rows locked by other transactions

UPDATE key_pool 
SET status = 'used', assigned_to = 'app-server-3', assigned_at = NOW()
WHERE key_value IN (...fetched keys...);

COMMIT;
```

> 💡 **`FOR UPDATE SKIP LOCKED`** is critical — it prevents two app servers from claiming the same batch. Available in MySQL 8.0+ and PostgreSQL 9.5+.

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ **Zero collisions** — keys are pre-validated | ❌ **Extra infrastructure** — need to maintain KGS |
| ✅ **Non-guessable** — randomly generated | ❌ **Pre-generation overhead** — need to generate millions of keys upfront |
| ✅ **Fast** — just a memory lookup (from local batch) | ❌ **Wasted keys** — if app server crashes, its buffered keys are lost |
| ✅ **No runtime computation** — no hashing, no collision check | ❌ **Finite pool** — need to monitor and replenish |
| ✅ **Decoupled** — key generation is separate from the business logic | ❌ **Not time-sortable** — keys are random strings |

### Verdict

> ✅ **Ideal for URL shorteners** and any system needing random, non-numeric, non-guessable keys. Not suitable when you need time-ordered numeric IDs.

---

## Step 8 — Approach 6: ULID & Other Modern Alternatives

> **"Let me briefly mention some modern alternatives that are gaining traction."**

### ULID (Universally Unique Lexicographically Sortable Identifier)

```
Format: 01ARZ3NDEKTSV4RRFFQ69G5FAV  (26 chars, Crockford Base32)

Structure:
┌──────────────────────┬───────────────────────────────┐
│   48 bits: Timestamp │     80 bits: Randomness       │
│  (ms since epoch)    │  (cryptographically random)   │
└──────────────────────┴───────────────────────────────┘
Total: 128 bits
```

| Feature | ULID | UUID v4 | UUID v7 | Snowflake |
|---------|------|---------|---------|-----------|
| Size | 128-bit | 128-bit | 128-bit | 64-bit |
| Time-sorted | ✅ Yes | ❌ No | ✅ Yes | ✅ Yes |
| Randomness | ✅ 80-bit | ✅ 122-bit | ✅ 62-bit | ⚠️ 12-bit seq |
| String format | 26 chars | 36 chars | 36 chars | N/A (numeric) |
| DB friendly | ✅ Good | ❌ Bad | ✅ Good | ✅ Best (64-bit) |

### MongoDB ObjectId

```
┌────────────┬──────────┬────────────┬──────────┐
│ 4 bytes    │ 5 bytes  │ 3 bytes    │ = 12 bytes (96-bit) │
│ Timestamp  │ Random   │ Counter    │
└────────────┴──────────┴────────────┘
```
- 96-bit, time-sorted, includes randomness.
- Used internally by MongoDB for `_id` fields.

### Instagram's ID Generation

Instagram uses a modified Snowflake approach:

```
┌──────────────────────────┬──────────┬───────────────┐
│    41 bits: Timestamp    │ 13 bits  │   10 bits     │
│  (ms since custom epoch) │ Shard ID │  Sequence     │
└──────────────────────────┴──────────┴───────────────┘
```
- Generated inside PostgreSQL using PL/pgSQL functions.
- Each shard runs its own ID generation — no external service needed.

### Discord's Snowflake

Discord uses a near-identical layout to Twitter Snowflake:
```
┌────────────────────────┬──────────────┬──────────────┬───────────────┐
│   42 bits: Timestamp   │  5 bits: WID │ 5 bits: PID  │ 12 bits: Seq  │
│ (ms since Discord epoch)│ Worker ID   │ Process ID   │  Increment    │
└────────────────────────┴──────────────┴──────────────┴───────────────┘
Discord Epoch: January 1, 2015
```

---

## Step 9 — Comparison Matrix — All Approaches

> **"Let me lay out all approaches side by side for the interviewer."**

| Criteria | UUID v4 | Multi-Master DB | Ticket Server | Snowflake ⭐ | KGS | ULID |
|----------|---------|-----------------|---------------|-------------|-----|------|
| **Uniqueness** | ✅ Practically guaranteed | ✅ Guaranteed | ✅ Guaranteed | ✅ Guaranteed | ✅ Guaranteed | ✅ Practically guaranteed |
| **Size** | 128-bit | 64-bit | 64-bit | **64-bit** | Variable (string) | 128-bit |
| **Time-sorted** | ❌ No | ❌ Not across servers | ❌ Not across servers | **✅ Yes** | ❌ No | ✅ Yes |
| **Coordination needed** | ❌ None | ⚠️ Low | ⚠️ Medium | **❌ None** | ⚠️ Medium (batch fetch) | ❌ None |
| **Throughput** | ✅ Very high | ⚠️ Medium | ⚠️ Medium | **✅ Very high (4M/s)** | ✅ High | ✅ Very high |
| **Scalability** | ✅ Infinite | ❌ Limited | ⚠️ Moderate | **✅ 1024 nodes** | ✅ Good | ✅ Infinite |
| **Clock dependency** | ❌ No | ❌ No | ❌ No | **⚠️ Yes (NTP)** | ❌ No | ⚠️ Yes |
| **DB index friendly** | ❌ Terrible | ✅ Good | ✅ Good | **✅ Excellent** | N/A | ✅ Good |
| **Non-guessable** | ✅ Yes | ❌ No | ❌ No | ⚠️ Partial | **✅ Yes** | ✅ Yes |
| **Best use case** | Tracing, logs | Small-medium apps | Medium scale | **General purpose** | URL shorteners | Modern apps |

---

## Step 10 — Deep Dive: Twitter Snowflake Architecture

> **"Let me go deeper into how Snowflake works in production."**

### Deployment Architecture

```
                    ┌─────────────────────────────────┐
                    │         ZooKeeper Cluster        │
                    │  (Machine ID coordination &      │
                    │   configuration management)      │
                    └───────┬──────────┬───────────────┘
                            │          │
              ┌─────────────┘          └─────────────┐
              │                                      │
              ▼                                      ▼
    ┌─────────────────┐                    ┌─────────────────┐
    │  Datacenter 1   │                    │  Datacenter 2   │
    │                 │                    │                 │
    │  ┌───────────┐  │                    │  ┌───────────┐  │
    │  │ Worker 1  │  │                    │  │ Worker 1  │  │
    │  │ DC=1,M=1  │  │                    │  │ DC=2,M=1  │  │
    │  └───────────┘  │                    │  └───────────┘  │
    │  ┌───────────┐  │                    │  ┌───────────┐  │
    │  │ Worker 2  │  │                    │  │ Worker 2  │  │
    │  │ DC=1,M=2  │  │                    │  │ DC=2,M=2  │  │
    │  └───────────┘  │                    │  └───────────┘  │
    │  ┌───────────┐  │                    │  ┌───────────┐  │
    │  │ Worker N  │  │                    │  │ Worker N  │  │
    │  │ DC=1,M=N  │  │                    │  │ DC=2,M=N  │  │
    │  └───────────┘  │                    │  └───────────┘  │
    └─────────────────┘                    └─────────────────┘
```

### Machine ID Assignment Strategies

**Problem:** Each Snowflake worker needs a unique (datacenter_id, machine_id) pair. How do we assign them?

| Strategy | How | Pros | Cons |
|----------|-----|------|------|
| **ZooKeeper** | Workers register in ZK → get unique sequential ID | ✅ Dynamic, automatic | ❌ Dependency on ZooKeeper |
| **Config file** | Hardcode in environment variable / config | ✅ Simple | ❌ Manual, error-prone |
| **Database** | Insert into `workers` table → use auto-increment ID | ✅ Simple | ❌ Centralized |
| **IP-based** | Hash of IP:Port → derive machine ID | ✅ No external dependency | ❌ Collision risk (must validate) |
| **Kubernetes** | Use StatefulSet ordinal index (pod-0 → 0, pod-1 → 1) | ✅ Cloud-native | ❌ Only works in K8s |

**Recommended:** ZooKeeper or etcd for production. Kubernetes ordinal for cloud-native deployments.

### Handling Clock Backwards

This is the **#1 concern** with Snowflake. NTP corrections or leap seconds can move the clock backwards.

```
Timeline:
  ... → 10:30:00.100 → 10:30:00.101 → 10:30:00.102 → ⚡ NTP correction → 10:30:00.098
                                                        ↑
                                                  CLOCK MOVED BACKWARDS!
```

**Mitigation strategies:**

| Strategy | Description |
|----------|-------------|
| **Refuse to generate** | Throw exception, alert on-call. Wait until clock catches up. (Twitter's default) |
| **Wait it out** | If the backward jump is small (< 5 ms), `Thread.sleep()` until the clock catches up. |
| **Use logical clock** | Maintain a local monotonic counter. If system clock < last timestamp, use last timestamp + 1. |
| **NTP configuration** | Configure NTP to **slew** instead of **step** — gradually adjust time instead of jumping. |
| **Amazon Time Sync** | Use AWS Time Sync Service with leap-smearing — avoids sudden jumps. |

**Best practice:**
```
if currentTimestamp < lastTimestamp:
    diff = lastTimestamp - currentTimestamp
    if diff < 5:           // Small jump (< 5ms)
        sleep(diff)        // Wait it out
        currentTimestamp = currentTimeMillis()
    else:
        throw ClockMovedBackwardsException(diff)
        // Alert the on-call engineer
        // This node stops generating IDs until resolved
```

### Handling Sequence Exhaustion

If a single machine generates more than **4,096 IDs in 1 millisecond**:

```
Millisecond 100: seq 0, 1, 2, ... 4095 → ALL USED UP!
What now? → WAIT for millisecond 101 (spin-wait or Thread.sleep(1))
```

```
function waitForNextMillis(lastTs):
    ts = currentTimeMillis()
    while ts <= lastTs:
        ts = currentTimeMillis()   // Busy-wait (spin)
    return ts
```

> At 4,096 IDs/ms = 4M IDs/sec per machine, this is rarely hit in practice. If it is, you need more machines, not a bigger sequence space.

---

## Step 11 — Deep Dive: KGS Architecture

> **"Let me architect the KGS as a proper production service."**

### KGS System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│                    ┌──────────────────────┐                         │
│                    │   Key Generator Job  │  (Offline / CRON)       │
│                    │  Generates millions  │                         │
│                    │  of random keys and  │                         │
│                    │  inserts into DB     │                         │
│                    └──────────┬───────────┘                         │
│                               │                                     │
│                               ▼                                     │
│                    ┌──────────────────────┐                         │
│                    │    Key Pool DB       │                         │
│                    │  ┌───────────────┐   │                         │
│                    │  │ unused_keys   │   │                         │
│                    │  │ (200M rows)   │   │                         │
│                    │  └───────────────┘   │                         │
│                    │  ┌───────────────┐   │                         │
│                    │  │ used_keys     │   │                         │
│                    │  │ (tracking)    │   │                         │
│                    │  └───────────────┘   │                         │
│                    └──────────┬───────────┘                         │
│                               │                                     │
│              ┌────────────────┼────────────────┐                    │
│              │                │                │                    │
│              ▼                ▼                ▼                    │
│     ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │
│     │  KGS Node 1  │ │  KGS Node 2  │ │  KGS Node 3  │             │
│     │              │ │              │ │              │             │
│     │ In-Memory    │ │ In-Memory    │ │ In-Memory    │             │
│     │ Buffer:      │ │ Buffer:      │ │ Buffer:      │             │
│     │ [10K keys]   │ │ [10K keys]   │ │ [10K keys]   │             │
│     └──────┬───────┘ └──────┬───────┘ └──────┬───────┘             │
│            │                │                │                      │
│            └────────────────┼────────────────┘                      │
│                             │                                       │
│                     ┌───────────────┐                               │
│                     │ Load Balancer │                               │
│                     └───────┬───────┘                               │
│                             │                                       │
└─────────────────────────────┼───────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
       ┌──────────┐   ┌──────────┐   ┌──────────┐
       │ App Srv 1│   │ App Srv 2│   │ App Srv N│
       │          │   │          │   │          │
       │ Local    │   │ Local    │   │ Local    │
       │ Buffer:  │   │ Buffer:  │   │ Buffer:  │
       │ [1K keys]│   │ [1K keys]│   │ [1K keys]│
       └──────────┘   └──────────┘   └──────────┘
```

### Two-Level Buffering Strategy

```
┌─────────────────┐         ┌──────────────────┐         ┌──────────┐
│  Key Pool DB    │ ──────→ │    KGS Node      │ ──────→ │ App Srv  │
│ (200M+ keys)    │  10K    │  (In-Memory Buf)  │  1K     │ (Local   │
│                 │  keys   │  10,000 keys      │  keys   │  Buffer) │
│  Source of      │  batch  │                   │  batch  │ 1K keys  │
│  Truth          │         │  Serves batches   │         │          │
│                 │         │  to App Servers   │         │  Dispenses│
│                 │         │                   │         │  1 at a  │
│                 │         │                   │         │  time    │
└─────────────────┘         └──────────────────┘         └──────────┘
    Persistent               Fast (in-memory)              Fastest
    Millions of keys         Thousands                     (no network)
```

**Why two levels?**
1. **App Server local buffer** → Eliminates network call for each key request. Super fast.
2. **KGS in-memory buffer** → Reduces DB queries. KGS fetches 10K at once from DB.
3. **DB** → Source of truth. Durable storage for all pre-generated keys.

### KGS API

```
// Internal API — only called by App Servers
GET /api/v1/keys?count=1000

Response: 200 OK
{
  "keys": ["Ab3xK9p", "Qw7rT4s", "Mn2pF6j", ...],  // 1000 keys
  "remaining": 198500000     // keys left in pool (for monitoring)
}
```

### Key Replenishment Pipeline

```
┌──────────────────────────────────────────────────────────────┐
│                    Key Replenishment Flow                     │
│                                                              │
│  Monitor ─── "Pool below 100M keys" ───→ Alert              │
│                                            │                 │
│                                            ▼                 │
│                                   ┌──────────────────┐       │
│                                   │  Key Generator    │       │
│                                   │  Batch Job        │       │
│                                   │                  │       │
│                                   │  Generates 50M   │       │
│                                   │  new random keys │       │
│                                   │  Checks for dups │       │
│                                   │  Inserts into DB │       │
│                                   └──────────────────┘       │
│                                                              │
│  Trigger: CRON (weekly) OR threshold-based (pool < 100M)     │
└──────────────────────────────────────────────────────────────┘
```

### Handling KGS Node Failures

| Scenario | Impact | Mitigation |
|----------|--------|------------|
| KGS node crashes | In-memory keys (10K) are lost | 10K keys wasted — negligible out of 3.5T possible |
| App server crashes | Local buffer (1K keys) are lost | 1K keys wasted — negligible |
| All KGS nodes down | App servers can still serve from local buffer | Local buffer buys time (minutes) |
| DB goes down | KGS can still serve from in-memory buffer | In-memory buffer buys time (minutes to hours) |

### Key Uniqueness Guarantee

```
How do we ensure no two keys are ever the same?

1. Pre-generation: Random key → check DB for existence → insert only if unique.
2. DB constraint: PRIMARY KEY on key_value → duplicate insert fails.
3. KGS atomic batch: FOR UPDATE SKIP LOCKED → no two nodes get the same key.
4. App server local buffer: Keys are exclusively assigned — no sharing.

Result: ZERO collision guarantee (unlike hash-based approaches).
```

---

## Step 12 — High-Level Architecture (Combined)

> **"Here's how both Snowflake and KGS fit into a production system."**

### When to Use Which?

```
┌─────────────────────────────────────────────────────────────────┐
│                     ID Generation Decision Tree                  │
│                                                                  │
│  Need numeric, 64-bit, time-sorted IDs?                         │
│   ├── YES → Use Snowflake                                       │
│   │         (DB primary keys, tweet IDs, order IDs)              │
│   │                                                              │
│   └── NO → Need random, non-guessable string keys?              │
│             ├── YES → Use KGS                                    │
│             │         (URL shortener, invite codes, API keys)    │
│             │                                                    │
│             └── NO → Need 128-bit with no coordination?          │
│                       ├── YES → Use UUID v7 or ULID             │
│                       │         (distributed logs, tracing)      │
│                       │                                          │
│                       └── Just need simple unique IDs?           │
│                             → Use DB auto-increment or Ticket   │
│                               Server                            │
└─────────────────────────────────────────────────────────────────┘
```

### Combined Architecture for a Large Platform

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Application Layer                            │
│                                                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                │
│  │ URL Shortener│  │ Order Svc   │  │ Message Svc │                │
│  │             │  │             │  │             │                │
│  │ Uses: KGS   │  │ Uses:       │  │ Uses:       │                │
│  │ (random     │  │ Snowflake   │  │ Snowflake   │                │
│  │  string keys)│  │ (numeric    │  │ (numeric    │                │
│  │             │  │  order IDs) │  │  msg IDs)   │                │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                │
│         │                │                │                        │
│         ▼                ▼                ▼                        │
│  ┌─────────────┐  ┌─────────────────────────────┐                  │
│  │     KGS     │  │    Snowflake ID Generator   │                  │
│  │  Cluster    │  │                             │                  │
│  │             │  │  ┌───────┐ ┌───────┐        │                  │
│  │  ┌───────┐  │  │  │Node 1│ │Node 2│ ...    │                  │
│  │  │Node 1│  │  │  │DC=1  │ │DC=1  │        │                  │
│  │  │Node 2│  │  │  │M=1   │ │M=2   │        │                  │
│  │  └───────┘  │  │  └───────┘ └───────┘        │                  │
│  │             │  │                             │                  │
│  │  ┌───────┐  │  │  Embedded library in each   │                  │
│  │  │Key DB │  │  │  app server (no network     │                  │
│  │  └───────┘  │  │  call needed!)              │                  │
│  └─────────────┘  └─────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────┘
```

> 💡 **Key insight:** Snowflake is typically an **embedded library** (runs in-process with the app server — no network call). KGS is a **separate service** (requires network calls, but batch-fetching mitigates this).

---

## Step 13 — Scaling, Reliability & Fault Tolerance

### 13.1 Snowflake Scaling

| Concern | Solution |
|---------|----------|
| Need more than 1,024 nodes | Reduce datacenter bits (3) + increase machine bits (7) → 8 DCs × 128 machines = 1,024. Or use 15 bits for machine ID → 32,768 nodes (sacrifice 3 bits from sequence). |
| Clock sync across DCs | Use NTP with AWS Time Sync, Google TrueTime, or Chrony. Monitor clock drift. |
| Adding new nodes | Assign a new unique machine ID via ZooKeeper. Instant — no migration needed. |
| Removing nodes | Decommission the machine ID. Can be reused after a cooldown period. |

### 13.2 KGS Scaling

| Concern | Solution |
|---------|----------|
| KGS pool running low | Monitor pool size. Alert at 50%. Auto-trigger batch generation at 25%. |
| KGS latency | Two-level buffering (KGS memory + app server local cache). Most requests are local (0 network). |
| KGS DB bottleneck | Partition key pool DB by key prefix (a-m on shard 1, n-z on shard 2). |
| Cross-region deployment | Deploy KGS per region. Each region has its own key pool (no cross-region coordination). |

### 13.3 Monitoring & Alerting

| Metric | Alert Threshold | Action |
|--------|----------------|--------|
| KGS unused key count | < 50 million | Trigger key generation job |
| KGS unused key count | < 10 million | 🔴 CRITICAL — immediate replenishment |
| Snowflake clock drift | > 100 ms | ⚠️ Warning — investigate NTP |
| Snowflake sequence exhaustion rate | > 50% of capacity per ms | Scale out — add more nodes |
| App server local key buffer | < 100 keys | Fetch new batch from KGS |
| ID generation latency (p99) | > 5 ms | Investigate bottleneck |
| Duplicate ID rate | > 0 | 🔴 CRITICAL — impossible if designed correctly |

### 13.4 Disaster Recovery

| Scenario | Snowflake | KGS |
|----------|-----------|-----|
| Single node failure | Other nodes continue. Replace failed node, assign new machine ID. | Other KGS nodes continue. App servers use local buffer. |
| Datacenter failure | Other DCs continue generating IDs (different DC ID). | Deploy KGS in each region with its own pool. |
| DB corruption | N/A (Snowflake is stateless, no DB) | Restore from backup. Generate new keys for the lost range. |
| Total key pool exhaustion | N/A | Increase key length from 7 to 8 chars → 62^8 = 218 trillion new keys. |

---

## Step 14 — Summary & Trade-offs

> **"Let me wrap up with the key decisions."**

### Summary Matrix

| Decision | Snowflake | KGS |
|----------|-----------|-----|
| **Output** | 64-bit numeric ID | Random string (e.g., 7-char Base62) |
| **Time-sorted** | ✅ Yes | ❌ No |
| **Non-guessable** | ⚠️ Partially (timestamp visible) | ✅ Fully random |
| **Coordination** | ❌ None (after machine ID assignment) | ⚠️ Batch-fetch from KGS |
| **Network call** | ❌ None (in-process) | ⚠️ Occasional (batch-fetch) |
| **State** | Stateless (just clock + counter) | Stateful (key pool DB) |
| **Failure mode** | Clock drift → refuse to generate | KGS down → use local buffer |
| **Best for** | DB PKs, feeds, timelines | URL shorteners, invite codes, tokens |

### Architecture Decision Record

```
ADR-001: Use Twitter Snowflake for all numeric primary keys
  - Status: Accepted
  - Context: Need time-sorted, 64-bit, unique IDs across 100+ services
  - Decision: Embed Snowflake library in each service
  - Consequences: Must maintain NTP sync, assign machine IDs via ZooKeeper

ADR-002: Use KGS for URL shortener keys
  - Status: Accepted
  - Context: Need random, non-guessable, 7-char string keys
  - Decision: Deploy dedicated KGS cluster with pre-generated key pool
  - Consequences: Must monitor key pool, handle key replenishment
```

### What I'd Build in Phases

| Phase | Snowflake | KGS |
|-------|-----------|-----|
| **MVP** | Use DB auto-increment (single master) | Use counter + Base62 (simple) |
| **V1** | Implement Snowflake with static machine IDs | Deploy KGS with single node + DB |
| **V2** | Add ZooKeeper for dynamic machine ID assignment | Add KGS cluster + two-level buffering |
| **V3** | Multi-DC deployment, clock monitoring, NTP hardening | Multi-region KGS, automated key replenishment |

---

## Bonus — Interviewer Questions

### Q: "What if two Snowflake nodes get the same machine ID?"

**A:** This would be catastrophic — duplicate IDs! Prevention strategies:
1. **ZooKeeper ephemeral nodes** — when a worker dies, its ZK node disappears. New workers always get unique IDs.
2. **Fencing tokens** — each machine ID assignment has a version. If two claim the same ID, the older version is rejected.
3. **Startup validation** — on boot, each node generates a test ID and checks the central registry for conflicts.

> In practice, this is an operational concern, not an algorithmic one. Proper coordination (ZK/etcd) makes it near-impossible.

### Q: "What if the Snowflake clock jumps forward by a lot?"

**A:** If the clock jumps **forward** (e.g., by 1 hour), the IDs will have timestamps from the "future." When the clock corrects back:
1. We hit the clock-backwards case → refuse to generate.
2. The system is stuck for up to 1 hour.

**Prevention:** Configure NTP to slew (gradual adjustment), never step (instant jump). Set `tinker panic 0` in NTP config to prevent large jumps.

### Q: "Can we combine Snowflake and KGS?"

**A:** Yes! Use Snowflake to generate the **seed** for KGS:
1. Use Snowflake to generate a unique 64-bit ID.
2. Base62-encode it to get a 7-11 character string.
3. Use this as the short URL key.

This gives you: unique (Snowflake guarantee) + time-sorted + compact string. But it's **not non-guessable** (sequential Snowflake IDs produce sequential Base62 strings). For URL shorteners, you'd still want to scramble or shuffle.

### Q: "How does Google's Spanner generate unique IDs?"

**A:** Google Spanner uses **TrueTime** — GPS + atomic clocks in every datacenter to achieve **globally synchronized time** with bounded uncertainty (usually < 7 ms). This allows Spanner to:
- Assign globally unique, time-ordered transaction IDs.
- Wait out the uncertainty window before committing — guarantees linearizability.

> TrueTime is not available outside Google. AWS has **Time Sync Service** (NTP-based), which is less precise but good enough for Snowflake.

### Q: "What if we need strictly sequential IDs (1, 2, 3, ...) with no gaps?"

**A:** This is **extremely hard** in distributed systems. Options:
1. **Single-node counter** — simple but SPOF and bottleneck.
2. **Consensus-based counter** (Raft/Paxos) — every ID requires a majority agreement. Slow (~10K IDs/sec max).
3. **Accept near-sequential** — use Snowflake (per-node sequential, gaps across nodes).

> In most real systems, strictly sequential is **not required**. The requirement is usually "unique + roughly ordered," which Snowflake handles perfectly.

### Q: "How do you test a distributed ID generator?"

**A:** Testing strategy:

| Test Type | What to Test |
|-----------|-------------|
| **Unit test** | Bit layout correctness, sequence overflow, clock backwards handling |
| **Load test** | Generate 1M IDs → check all unique (use a HashSet or Bloom filter) |
| **Concurrency test** | 100 threads on same node → no duplicates |
| **Multi-node test** | 10 nodes generating simultaneously → collect all IDs → assert uniqueness |
| **Clock manipulation** | Manually set clock backward → verify exception or graceful handling |
| **Chaos test** | Kill nodes mid-generation → verify no duplicates after recovery |

### Q: "What's the difference between KGS for URL shortener vs API key generation?"

**A:**

| Aspect | URL Shortener Keys | API Keys |
|--------|-------------------|----------|
| Length | 7 chars (short as possible) | 32-64 chars (security over brevity) |
| Character set | Base62 (a-z, A-Z, 0-9) | Base64 or hex |
| Security | Non-guessable (nice to have) | Cryptographically secure (MUST have) |
| Revocation | Optional (expiry) | Required (immediate revocation) |
| Generation | Random or pre-generated | `crypto.randomBytes()` (CSPRNG) |
| Storage | Plain text | Hashed (bcrypt/SHA-256) — never store in plain text |

---

## Final Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DISTRIBUTED ID GENERATION PLATFORM                │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                     Application Services                      │  │
│  │                                                               │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │  │
│  │  │ URL Svc  │  │ Order Svc│  │ Chat Svc │  │ Feed Svc │    │  │
│  │  └─────┬────┘  └─────┬────┘  └─────┬────┘  └─────┬────┘    │  │
│  │        │              │              │              │         │  │
│  └────────┼──────────────┼──────────────┼──────────────┼────────┘  │
│           │              │              │              │            │
│           │    ┌─────────┴──────────────┴──────────────┘            │
│           │    │         Snowflake (Embedded Library)               │
│           │    │         ┌────────────────────────────┐             │
│           │    │         │ 41b:Time|5b:DC|5b:M|12b:Seq│             │
│           │    │         └────────────────────────────┘             │
│           │    │                                                    │
│           ▼    │                                                    │
│  ┌─────────────────┐        ┌──────────────────────────┐           │
│  │   KGS Cluster   │        │  ZooKeeper / etcd        │           │
│  │                 │        │  (Machine ID Registry)   │           │
│  │  ┌───┐ ┌───┐   │        └──────────────────────────┘           │
│  │  │N1 │ │N2 │   │                                               │
│  │  └─┬─┘ └─┬─┘   │        ┌──────────────────────────┐           │
│  │    │     │     │        │  NTP / Time Sync Service  │           │
│  │    ▼     ▼     │        │  (Clock Synchronization)  │           │
│  │  ┌──────────┐  │        └──────────────────────────┘           │
│  │  │ Key Pool │  │                                               │
│  │  │   DB     │  │        ┌──────────────────────────┐           │
│  │  │ (200M    │  │        │  Monitoring (Prometheus)  │           │
│  │  │  keys)   │  │        │  - Key pool size          │           │
│  │  └──────────┘  │        │  - Clock drift            │           │
│  │                 │        │  - ID generation rate     │           │
│  │  ┌──────────┐  │        │  - Duplicate detection    │           │
│  │  │ Key Gen  │  │        └──────────────────────────┘           │
│  │  │ Batch Job│  │                                               │
│  │  └──────────┘  │                                               │
│  └─────────────────┘                                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

> **Final Note:** In an actual interview, pick **one or two approaches** and go deep. The interviewer wants to see:
> 1. **Structured thinking** — clarify requirements first.
> 2. **Knowledge of trade-offs** — no solution is perfect, articulate why you chose one over another.
> 3. **Depth** — show you understand the internals (bit manipulation, clock issues, concurrency).
> 4. **Production awareness** — monitoring, failure modes, scaling path.
>
> If asked "Design a Unique ID Generator," **Snowflake is the default answer.**  
> If asked "Design a KGS for URL shortener," **pre-generated key pool with two-level buffering.**  
> Know both — they complement each other.

