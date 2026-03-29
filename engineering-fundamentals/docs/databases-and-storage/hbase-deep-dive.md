# 🗄️ HBase Deep Dive — Senior Engineer's Complete Reference

> Everything a senior engineer should know about HBase.  
> From data model to compaction, region splitting, RowKey design, and production tuning.

---

## Table of Contents

1. [What is HBase — Really?](#1-what-is-hbase--really)
2. [Data Model — Tables, Column Families, Cells](#2-data-model--tables-column-families-cells)
3. [Architecture — Masters, RegionServers, ZooKeeper](#3-architecture--masters-regionservers-zookeeper)
4. [Regions — Splitting and Balancing](#4-regions--splitting-and-balancing)
5. [Write Path — WAL, MemStore, Flush](#5-write-path--wal-memstore-flush)
6. [Read Path — MemStore, BlockCache, HFiles](#6-read-path--memstore-blockcache-hfiles)
7. [HFile Format — Sorted, Immutable Storage](#7-hfile-format--sorted-immutable-storage)
8. [Compaction — Minor and Major](#8-compaction--minor-and-major)
9. [RowKey Design — The Make-or-Break Decision](#9-rowkey-design--the-make-or-break-decision)
10. [Column Family Design](#10-column-family-design)
11. [Bloom Filters — Avoiding Unnecessary Reads](#11-bloom-filters--avoiding-unnecessary-reads)
12. [Consistency Model & CAP Theorem](#12-consistency-model--cap-theorem)
13. [Coprocessors — Triggers & Endpoints](#13-coprocessors--triggers--endpoints)
14. [Apache Phoenix — SQL on HBase](#14-apache-phoenix--sql-on-hbase)
15. [HBase with MapReduce / Spark](#15-hbase-with-mapreduce--spark)
16. [Performance Tuning & Configuration](#16-performance-tuning--configuration)
17. [Monitoring & Troubleshooting](#17-monitoring--troubleshooting)
18. [HBase vs Cassandra vs BigTable](#18-hbase-vs-cassandra-vs-bigtable)
19. [Production Best Practices](#19-production-best-practices)
20. [Interview Q&A — 30 Questions](#20-interview-qa--30-questions)

---

## 1. What is HBase — Really?

HBase is a **distributed, versioned, column-family store** built on HDFS, modeled after Google's Bigtable paper (2006).

### The Bigtable Paper's Definition

> "A Bigtable is a sparse, distributed, persistent multi-dimensional sorted map."

```
map[(RowKey, Column, Timestamp)] → Value

- Sparse:       Not every row has every column
- Distributed:  Data spread across many machines
- Persistent:   Stored on HDFS (durable)
- Sorted:       Rows sorted by RowKey (lexicographic)
- Versioned:    Each cell has multiple timestamp versions
```

### Where HBase Fits

```
                    ┌──────────────────┐
                    │   Application     │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │      HBase       │  ← Random R/W, real-time access
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │      HDFS        │  ← Distributed storage
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │    Hadoop/YARN   │  ← Batch processing
                    └──────────────────┘
```

### When to Use HBase

| Scenario | Why HBase? |
|----------|-----------|
| **Facebook Messages** | Billions of messages, random access by user |
| **Time-series data** | IoT sensors, metrics, logs |
| **User profiles** | Sparse, wide rows with many attributes |
| **Clickstream data** | High write throughput, scan by user/time |
| **Graph adjacency lists** | Row per vertex, columns for edges |

---

## 2. Data Model — Tables, Column Families, Cells

### Logical View

```
Table: users
┌─────────────────────────────────────────────────────────────────┐
│ RowKey    │ CF: info              │ CF: activity                 │
│           │ name   │ email │ age  │ last_login   │ login_count  │
│───────────┼────────┼───────┼──────┼──────────────┼──────────────│
│ user001   │ Sunil  │ s@e   │ 28   │ 2024-01-15   │ 156          │
│ user002   │ Amit   │       │ 30   │ 2024-01-14   │ 89           │
│ user003   │ Priya  │ p@e   │      │              │ 12           │
└─────────────────────────────────────────────────────────────────┘
```

### Physical Storage (How HBase Actually Stores It)

```
Each Column Family is stored separately (different HFiles):

CF: info
┌──────────────────────────────────────────────────────────┐
│ RowKey   │ Column          │ Timestamp      │ Value      │
│──────────┼─────────────────┼────────────────┼────────────│
│ user001  │ info:name       │ 1705305600000  │ "Sunil"    │
│ user001  │ info:email      │ 1705305600000  │ "s@e"      │
│ user001  │ info:age        │ 1705305600000  │ "28"       │
│ user002  │ info:name       │ 1705219200000  │ "Amit"     │
│ user002  │ info:age        │ 1705219200000  │ "30"       │
│ user003  │ info:name       │ 1705132800000  │ "Priya"    │
│ user003  │ info:email      │ 1705132800000  │ "p@e"      │
└──────────────────────────────────────────────────────────┘

Key-Value format:
(RowKey, ColumnFamily:Qualifier, Timestamp) → Value

Everything is stored as bytes!
```

### Versioning

```
Cell: (user001, info:email, T3) → "sunil@new.com"
Cell: (user001, info:email, T2) → "sunil@old.com"
Cell: (user001, info:email, T1) → "sunil@first.com"

Default: Keep 1 version (configurable per column family)
Latest timestamp wins on read.

// Read specific version
Get get = new Get(Bytes.toBytes("user001"));
get.setMaxVersions(3);  // Get last 3 versions
```

### Important Rules

| Rule | Implication |
|------|------------|
| Rows sorted by RowKey (lexicographic) | "1" < "10" < "2" (string sort, not numeric!) |
| Column Families defined at table creation | Can't add easily (table alteration = downtime) |
| Column Qualifiers can be added anytime | Schema-less within a CF |
| Everything stored as byte arrays | Client serializes/deserializes |
| Atomic operations per row only | No multi-row transactions (unless via Phoenix) |

---

## 3. Architecture — Masters, RegionServers, ZooKeeper

### Cluster Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        HBase Cluster                             │
│                                                                  │
│  ┌──────────────────┐                                           │
│  │    ZooKeeper      │  ← Cluster coordination                  │
│  │    Ensemble       │  ← Master election                       │
│  │  (3 or 5 nodes)   │  ← RegionServer membership              │
│  └────────┬─────────┘  ← META table location                   │
│           │                                                      │
│  ┌────────▼─────────┐   ┌─────────────────┐                    │
│  │   Active HMaster  │   │  Backup HMaster │                    │
│  │                   │   │  (standby)       │                    │
│  └────────┬─────────┘   └─────────────────┘                    │
│           │                                                      │
│  ┌────────▼───────┐ ┌───────────────┐ ┌───────────────┐        │
│  │ RegionServer 1 │ │ RegionServer 2│ │ RegionServer 3│        │
│  │ ┌───┐ ┌───┐   │ │ ┌───┐ ┌───┐  │ │ ┌───┐ ┌───┐  │        │
│  │ │R-A│ │R-B│   │ │ │R-C│ │R-D│  │ │ │R-E│ │R-F│  │        │
│  │ └───┘ └───┘   │ │ └───┘ └───┘  │ │ └───┘ └───┘  │        │
│  └───────┬───────┘ └──────┬────────┘ └──────┬────────┘        │
│          │                │                  │                   │
│  ┌───────▼────────────────▼──────────────────▼───────┐          │
│  │                     HDFS                           │          │
│  │  DataNode 1    DataNode 2    DataNode 3            │          │
│  └───────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Role |
|-----------|------|
| **ZooKeeper** | Master election, RegionServer liveness tracking, META table location, cluster configuration |
| **HMaster** | DDL operations (create/alter/drop table), region assignment to RegionServers, load balancing, crash recovery |
| **RegionServer** | Hosts regions, serves read/write requests, manages MemStore/WAL/BlockCache, handles compaction & flush |
| **Region** | Contiguous range of rows. Unit of distribution. Auto-splits when too large. |

### META Table — The Directory

```
hbase:meta table maps:
  Table + RowKey range → RegionServer location

Client lookup flow:
1. Client asks ZooKeeper: "Where is META table?"
2. ZooKeeper: "RegionServer 2 hosts META"
3. Client asks RegionServer 2: "Where is row 'user001' in table 'users'?"
4. META: "Region R-C on RegionServer 2"
5. Client caches this and talks directly to RegionServer 2

After first lookup → client caches region locations → no ZK needed!
```

---

## 4. Regions — Splitting and Balancing

### What is a Region?

```
Table: users (1 billion rows)

Region 1: [A, D)     ← Rows starting with A-C
Region 2: [D, K)     ← Rows starting with D-J
Region 3: [K, P)     ← Rows starting with K-O
Region 4: [P, Z]     ← Rows starting with P-Z

Each region served by ONE RegionServer.
Multiple regions can be on the same RegionServer.
```

### Region Splitting

```
Region (10GB) → Split into 2 regions (5GB each)

Before: Region A [aaa, zzz]  on RS-1
After:  Region A [aaa, mmm]  on RS-1
        Region B [mmm, zzz]  on RS-2 (moved by load balancer)

Split point = median RowKey

Triggers:
- Region size exceeds threshold (default: 10GB)
- IncreasingToUpperBoundRegionSplitPolicy (default in HBase 2.x)
  - Small table: smaller split size → more regions
  - Large table: split at configured max
```

### Region Balancing

HMaster ensures regions are evenly distributed across RegionServers.

```
Before balancing:
RS-1: [R1, R2, R3, R4, R5]   (5 regions — overloaded)
RS-2: [R6]                     (1 region — underutilized)
RS-3: [R7]                     (1 region — underutilized)

After balancing:
RS-1: [R1, R2, R3]            (3 regions)
RS-2: [R4, R5]                (2 regions)
RS-3: [R6, R7]                (2 regions)
```

---

## 5. Write Path — WAL, MemStore, Flush

### Write Path in Detail

```
Client.put(row) 
    │
    ▼
RegionServer receives write
    │
    ├──1──▶ Write to WAL (Write-Ahead Log)
    │       - Sequential write to HDFS
    │       - Durability guarantee (if RS crashes, replay WAL)
    │
    └──2──▶ Write to MemStore
            - In-memory sorted buffer (ConcurrentSkipListMap)
            - One MemStore per Region per Column Family
            - Very fast (memory operation)
            │
            │ MemStore reaches threshold (128MB default)
            ▼
    ────3──▶ Flush to HFile
            - MemStore sorted data written to HDFS as HFile
            - HFile is immutable (write-once, read-many)
            - WAL entries for flushed data can be removed
            │
            │ Too many HFiles
            ▼
    ────4──▶ Compaction
            - Merge multiple HFiles into fewer
            - Remove deleted cells (tombstones)
            - Remove expired versions
```

### WAL (Write-Ahead Log)

```
Purpose: Durability. If RegionServer crashes before MemStore flush,
         WAL is replayed to recover data.

One WAL per RegionServer (not per region).
Sequential writes → very fast.

On RS crash:
1. HMaster detects via ZooKeeper
2. WAL split: entries sorted by region
3. Each region's WAL entries replayed on new RS
```

### MemStore

```
- In-memory write buffer
- One per (Region, Column Family)
- Sorted by (RowKey, Column, Timestamp)
- Flushed to HFile when:
  a) MemStore size > 128MB (hbase.hregion.memstore.flush.size)
  b) Total MemStore > 40% of heap (hbase.regionserver.global.memstore.size)
  c) WAL too large
```

---

## 6. Read Path — MemStore, BlockCache, HFiles

### Read Path in Detail

```
Client.get(row)
    │
    ▼
RegionServer receives read
    │
    ├──1──▶ Check MemStore (in-memory, latest writes)
    │       - May have the most recent data
    │
    ├──2──▶ Check BlockCache (LRU cache of HFile blocks)
    │       - Recently read data cached here
    │       - ~40% of RegionServer heap
    │
    └──3──▶ Read HFiles (on HDFS)
            - Check Bloom filter first (skip files that don't have the key)
            - Read index block → find data block → read data
            - Cache the block in BlockCache for future reads
            │
            ▼
    Merge all results (MemStore + BlockCache + HFiles)
    Return latest version to client
```

### BlockCache

```
Two implementations:
1. LRUBlockCache (default): On-heap LRU cache
2. BucketCache: Off-heap cache (recommended for large caches)

Cache levels:
- SINGLE: First read → goes here
- MULTI:  Second read → promoted here
- IN_MEMORY: Configured CFs always cached here

Sizing: ~40% of RS heap for BlockCache
        ~40% for MemStore
        ~20% for other
```

---

## 7. HFile Format — Sorted, Immutable Storage

```
HFile Structure:
┌──────────────────────────────┐
│       Data Blocks            │  ← Actual KV data (sorted)
│   Block 1: [KV, KV, KV]     │     Default 64KB per block
│   Block 2: [KV, KV, KV]     │
│   Block N: [KV, KV, KV]     │
├──────────────────────────────┤
│       Meta Blocks            │  ← Bloom filter data
├──────────────────────────────┤
│       Index Blocks           │  ← Index for data blocks
│   (multi-level for large     │
│    files)                    │
├──────────────────────────────┤
│       Trailer                │  ← Offsets to index, meta, etc.
└──────────────────────────────┘

- Key-value pairs sorted by (RowKey, CF:Qualifier, Timestamp desc)
- Immutable: never modified after creation
- Supports compression: Snappy, LZ4, GZIP, ZSTD
```

---

## 8. Compaction — Minor and Major

### Why Compaction?

Each MemStore flush creates a new HFile. Too many HFiles = slow reads (must check each one).

### Minor Compaction

```
Merges a few small HFiles into one larger HFile.
- Triggered automatically when file count exceeds threshold
- Does NOT delete expired cells or tombstones
- Fast, minimal I/O
```

### Major Compaction

```
Merges ALL HFiles for a region into one HFile.
- Deletes expired versions and tombstone markers
- Reclaims disk space
- Very I/O intensive
- Typically scheduled during off-peak hours

Default: runs every 7 days (hbase.hregion.majorcompaction)
Production: DISABLE auto and run manually during maintenance window
```

```
Before compaction:
HFile1: [a:1, b:2, c:3]
HFile2: [a:4, d:5]        (a updated to 4)
HFile3: [b:DEL, e:6]      (b deleted)

After major compaction:
HFile:  [a:4, c:3, d:5, e:6]  (merged, b removed, a latest version)
```

---

## 9. RowKey Design — The Make-or-Break Decision

### The Problem: Hotspotting

```
Sequential RowKeys (e.g., timestamps):
  20240101_001
  20240101_002
  20240101_003
  ...

All writes go to the SAME region → ONE RegionServer overloaded
Other RegionServers idle → cluster underutilized
This is called a HOTSPOT.
```

### Solutions

#### 1. Salting (Prepend Random Prefix)

```
Original:  "20240101_event_1"
Salted:    "3_20240101_event_1" (prefix = hash(key) % num_regions)

Distribution: 
Bucket 0: [0_20240101_event_2, 0_20240101_event_5, ...]
Bucket 1: [1_20240101_event_3, 1_20240101_event_7, ...]
Bucket 2: [2_20240101_event_1, 2_20240101_event_4, ...]

Pro: Even distribution
Con: Scan requires N parallel scans (one per salt bucket)
```

#### 2. Hashing

```
Original:  "user_12345"
Hashed:    "a1b2c3d4" (MD5 of key, first 8 chars)

Pro: Uniform distribution
Con: Range scans impossible (hash destroys ordering)
```

#### 3. Reverse Key

```
Original:  "com.google.www"
Reversed:  "www.google.com"

Useful for: domain names, phone numbers
Pro: Distributes similar keys across regions
Con: Prefix scans need reversal logic
```

#### 4. Composite Key

```
Design: userId + reverseTimestamp
Key:    "user001_" + (Long.MAX_VALUE - timestamp)

Benefit: Scan all events for a user, most recent first
         scan.withStartRow("user001_")
         scan.withStopRow("user002_")
```

### RowKey Design Checklist

```
□ Will my RowKey cause hotspots? (sequential, monotonic)
□ What are my access patterns? (point lookup, range scan, prefix scan)
□ Do I need to scan by a secondary attribute? (→ composite key or secondary index)
□ Are my RowKeys uniformly distributed?
□ Is the RowKey length reasonable? (shorter = less storage overhead)
□ Am I using the right encoding? (string vs long for numbers)
```

---

## 10. Column Family Design

### Rules

```
1. Keep the number of Column Families LOW (1-3)
   - Each CF = separate MemStore + HFiles
   - More CFs = more memory, more flushes, more compactions

2. Group columns by ACCESS PATTERN
   - CF "info": name, email, age (read together)
   - CF "activity": last_login, login_count (updated frequently)

3. Don't mix frequently and rarely accessed columns in same CF
   - Separate read cache behavior
   - Different compaction patterns

4. CF names should be SHORT (1-2 chars in production)
   - "i" instead of "info" (stored with EVERY cell)
   - Saves significant space at scale
```

---

## 11. Bloom Filters — Avoiding Unnecessary Reads

```
Bloom Filter = probabilistic data structure
  - "Is key X in this HFile?"
  - Answer: "Definitely NO" or "Maybe YES"
  - No false negatives, possible false positives

Without Bloom filter:
  Read checks ALL HFiles → slow

With Bloom filter:
  Read checks only HFiles that MIGHT have the key → fast

Types:
- ROW: "Does this HFile contain this RowKey?"
- ROWCOL: "Does this HFile contain this RowKey + Column?"

Enabling:
  alter 'users', {NAME => 'info', BLOOMFILTER => 'ROW'}
```

---

## 12. Consistency Model & CAP Theorem

### HBase and CAP

```
HBase = CP (Consistent + Partition-tolerant)

- Strong consistency: Read always returns latest write (for same row)
- Each row owned by exactly ONE region on ONE RegionServer
- No read replicas (by default) → no eventual consistency
- During partition: unavailable regions can't be read/written

Compare:
  Cassandra = AP (available, eventually consistent)
  HBase     = CP (consistent, may be temporarily unavailable)
```

### Atomicity

```
- Single row operations are ATOMIC
  - Put, Delete, CheckAndPut, CheckAndDelete, Increment
- Multi-row operations are NOT atomic
  - batch puts may partially succeed
  
- No multi-row transactions (use Phoenix for limited support)
```

---

## 13. Coprocessors — Triggers & Endpoints

### Observer (Trigger-like)

```java
// Runs on RegionServer — like a database trigger
public class AuditObserver implements RegionObserver {
    @Override
    public void postPut(ObserverContext<RegionCoprocessorEnvironment> ctx,
                        Put put, WALEdit edit, Durability durability) {
        // Log every put operation
        LOG.info("Row written: " + Bytes.toString(put.getRow()));
    }
}
```

### Endpoint (Stored Procedure-like)

```
Execute code on RegionServer instead of fetching data to client.
Example: Compute SUM on server side → return only result.
```

---

## 14. Apache Phoenix — SQL on HBase

```sql
-- Create table (maps to HBase table)
CREATE TABLE users (
    user_id VARCHAR PRIMARY KEY,
    name VARCHAR,
    email VARCHAR,
    age INTEGER
);

-- Insert (maps to HBase Put)
UPSERT INTO users VALUES ('user001', 'Sunil', 'sunil@email.com', 28);

-- Query (uses HBase Scan + server-side filtering)
SELECT * FROM users WHERE age > 25 ORDER BY name LIMIT 10;

-- Secondary index (creates separate HBase table)
CREATE INDEX idx_email ON users (email);
```

---

## 15. HBase with MapReduce / Spark

### Spark + HBase

```scala
// Read HBase table as DataFrame
val df = spark.read
  .format("org.apache.hadoop.hbase.spark")
  .option("hbase.table", "users")
  .option("hbase.columns.mapping", 
    "user_id STRING :key, name STRING info:name, age INT info:age")
  .load()

df.filter($"age" > 25).show()
```

---

## 16. Performance Tuning & Configuration

### Key Configuration Parameters

| Parameter | Default | Recommendation |
|-----------|---------|---------------|
| `hbase.regionserver.handler.count` | 30 | 100-200 for heavy workloads |
| `hbase.hregion.memstore.flush.size` | 128MB | 256MB for write-heavy |
| `hbase.hregion.max.filesize` | 10GB | Depends on region count needed |
| `hbase.regionserver.global.memstore.size` | 0.4 | 40% of heap |
| `hfile.block.cache.size` | 0.4 | 40% of heap (adjust with memstore) |
| `hbase.hstore.compactionThreshold` | 3 | Tune based on write rate |

### Memory Layout

```
RegionServer JVM Heap (e.g., 32GB):
├── MemStore:    40% = 12.8GB (write buffer)
├── BlockCache:  40% = 12.8GB (read cache)
└── Other:       20% = 6.4GB  (JVM overhead, threads, etc.)

Write-heavy: increase MemStore, decrease BlockCache
Read-heavy: increase BlockCache, decrease MemStore
```

---

## 17. Monitoring & Troubleshooting

### Key Metrics

| Metric | What It Tells You |
|--------|------------------|
| Request latency (p99) | Slow reads/writes |
| MemStore size | Close to flush threshold? |
| BlockCache hit ratio | > 80% good. Low = cache too small or scan-heavy |
| Compaction queue length | Compaction falling behind? |
| Region count per RS | Too many = overhead. Too few = underutilized |
| WAL size | Large WAL = slow flushes |

### Common Issues

| Problem | Cause | Fix |
|---------|-------|-----|
| Hotspot | Sequential RowKeys | Salt, hash, or reverse keys |
| Slow reads | Too many HFiles | Tune compaction, increase cache |
| GC pauses | Large heap, bad GC config | Use G1GC, tune heap, off-heap cache |
| Region too large | Missing splits | Pre-split table, check split policy |
| OOM | MemStore + cache too large | Adjust memory ratios |

---

## 18. HBase vs Cassandra vs BigTable

| Feature | HBase | Cassandra | Cloud Bigtable |
|---------|-------|-----------|---------------|
| **Based on** | Google Bigtable paper | Amazon Dynamo paper | Bigtable (managed) |
| **Architecture** | Master-slave | Peer-to-peer (masterless) | Managed service |
| **Consistency** | Strong (CP) | Tunable (AP by default) | Strong (CP) |
| **Write path** | WAL + MemStore | Commit log + Memtable | Similar to HBase |
| **Storage** | HDFS | Local SSTable | Colossus (Google FS) |
| **Scaling** | Add nodes + rebalance | Add nodes (easy) | Auto-scale |
| **Operations** | Complex (ZK + HDFS + HBase) | Simpler | Zero ops (managed) |
| **SQL** | Phoenix | CQL | None (client libs) |
| **Best for** | Hadoop ecosystem, strong consistency | Multi-DC, high availability | Google Cloud, zero ops |

---

## 19. Production Best Practices

```
□ Design RowKey carefully (avoid hotspots, consider access patterns)
□ Pre-split tables (don't start with 1 region)
□ Limit Column Families to 1-3
□ Use short CF names (1-2 chars)
□ Enable Bloom filters (ROW for point lookups)
□ Enable compression (Snappy or LZ4)
□ Disable auto major compaction, schedule manually
□ Set MemStore:BlockCache ratio based on workload
□ Use BucketCache for large read caches (off-heap)
□ Monitor compaction queue, GC pauses, request latency
□ Use batch operations for bulk writes
□ Set appropriate versions (usually 1-3)
□ Use TTL for time-series data (auto-cleanup)
□ Pre-split with expected key distribution
□ Keep RegionServer heap 16-32GB (avoid large GC pauses)
```

---

## 20. Interview Q&A — 30 Questions

| # | Question | Answer |
|---|----------|--------|
| 1 | What is HBase? | Distributed column-family NoSQL DB on HDFS, modeled after Google Bigtable |
| 2 | Data model? | (RowKey, CF:Qualifier, Timestamp) → Value. Sparse, sorted, versioned. |
| 3 | What is a Region? | Contiguous range of rows. Unit of distribution. Served by one RS. |
| 4 | Write path? | Client → WAL (durability) + MemStore (speed) → Flush to HFile → Compaction |
| 5 | Read path? | MemStore → BlockCache → HFiles (Bloom filter → Index → Data block) |
| 6 | What is WAL? | Write-Ahead Log. Sequential write to HDFS. Replayed on RS crash. |
| 7 | What is MemStore? | In-memory write buffer. Sorted. Flushed to HFile when full. |
| 8 | What is an HFile? | On-disk sorted, immutable storage format. Contains data + index + bloom. |
| 9 | Minor vs Major compaction? | Minor: merge few HFiles. Major: merge ALL, remove deletes/expired. |
| 10 | RowKey design importance? | Most critical decision. Bad RowKey = hotspots, bad scan performance. |
| 11 | How to avoid hotspots? | Salt, hash, reverse, composite keys. Avoid sequential/monotonic keys. |
| 12 | Role of ZooKeeper? | Master election, RS liveness, META location, cluster coordination. |
| 13 | Role of HMaster? | DDL, region assignment, load balancing. Not in data path. |
| 14 | What if HMaster dies? | Data operations continue (RS serve directly). DDL unavailable. |
| 15 | What is BlockCache? | Read cache. LRU in-heap or BucketCache off-heap. |
| 16 | What are Bloom filters? | Probabilistic filter to skip HFiles that don't contain the key. |
| 17 | HBase consistency model? | CP (strong consistency). One region = one RS. Atomic per row. |
| 18 | HBase vs Cassandra? | HBase = CP, master-slave, HDFS. Cassandra = AP, peer-to-peer, local storage. |
| 19 | What is a Column Family? | Group of columns stored together. Defined at table creation. |
| 20 | Why limit Column Families? | Each CF = MemStore + HFiles. More CFs = more memory + I/O. |
| 21 | What is region splitting? | Region grows beyond threshold → split into two. Automatic. |
| 22 | What is pre-splitting? | Create table with predefined split points → avoid initial single-region bottleneck |
| 23 | What is Apache Phoenix? | SQL layer on HBase. Supports secondary indexes, joins, transactions. |
| 24 | How does versioning work? | Each cell has timestamp. Multiple versions stored. Latest returned by default. |
| 25 | What is TTL? | Time-To-Live. Cells auto-expire. Good for time-series. |
| 26 | What are coprocessors? | Server-side code: Observer (triggers) and Endpoint (stored procedures). |
| 27 | TLAB vs MemStore? | TLAB = JVM thread allocation. MemStore = HBase write buffer. |
| 28 | How to tune for writes? | Increase MemStore size, batch writes, async WAL, compression. |
| 29 | How to tune for reads? | Increase BlockCache, enable Bloom filters, compression, proper RowKey. |
| 30 | When NOT to use HBase? | Small data, complex SQL, strong multi-row ACID, simple key-value (use Redis). |

---

> **Pro Tip for Interviews:** HBase interviews always come back to RowKey design. Practice designing RowKeys for: user timelines, IoT sensor data, chat messages, web clickstream. Show you understand trade-offs between write distribution and read patterns.

