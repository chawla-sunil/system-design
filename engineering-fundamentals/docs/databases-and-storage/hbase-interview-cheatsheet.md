# ⚡ HBase in 5 Minutes — Interview Cheat Sheet

> Quick-fire HBase concepts. Know these for any big data / distributed systems interview.

---

## What is HBase?

**One line:** HBase is an **open-source, distributed, column-family NoSQL database** built on top of HDFS (Hadoop Distributed File System), modeled after Google's Bigtable.

**Think of it as:** A giant sorted map: `(RowKey, ColumnFamily:Qualifier, Timestamp) → Value`. Designed for **billions of rows × millions of columns** on commodity hardware.

---

## When to Use HBase

| ✅ Use When | ❌ Don't Use When |
|-------------|-------------------|
| Billions of rows | Small dataset (< millions of rows) |
| Random read/write at scale | Complex SQL queries (joins, aggregations) |
| Wide/sparse tables | Full ACID transactions |
| Write-heavy workloads | Low-latency reads (< 1ms consistently) |
| Time-series data | Strong schema requirements |
| Need horizontal scaling | Simple key-value is enough (use Redis) |

---

## Data Model — The Interview Core

```
┌─────────────────────────────────────────────────────────────────┐
│                          HBase Table                             │
│                                                                  │
│  RowKey    │ CF: personal          │ CF: address                 │
│            │ name    │ age │ email │ city     │ state │ zip      │
│────────────┼─────────┼─────┼───────┼──────────┼───────┼──────── │
│  user001   │ "Sunil" │ 28  │ s@e   │ "Delhi"  │ "DL"  │ 110001 │
│  user002   │ "Amit"  │ 30  │       │ "Mumbai" │       │ 400001 │
│  user003   │ "Priya" │     │ p@e   │          │ "KA"  │         │
└─────────────────────────────────────────────────────────────────┘

Key Concepts:
- RowKey:           Primary key (sorted lexicographically)
- Column Family:    Group of columns (stored together on disk)
- Column Qualifier: Column name within a family
- Cell:             (RowKey, CF:Qualifier, Timestamp) → Value
- Timestamp:        Each cell has multiple versions (automatic)
```

---

## Architecture — Quick Overview

```
┌─────────────────────────────────────────┐
│              HBase Cluster               │
│                                          │
│  ┌──────────┐    ┌──────────────────┐   │
│  │  Client   │──▶│   ZooKeeper      │   │  ← Coordination
│  └──────────┘    │   (ensemble)      │   │
│       │          └──────────────────┘   │
│       │                                  │
│       ▼                                  │
│  ┌──────────┐                            │
│  │  HMaster │                            │  ← Region assignment, DDL
│  └──────────┘                            │
│       │                                  │
│  ┌────▼─────┐  ┌──────────┐  ┌────────┐│
│  │RegionSvr │  │RegionSvr │  │RegionSvr││  ← Data serving
│  │ Region A │  │ Region B │  │ Region C││
│  └──────────┘  └──────────┘  └────────┘│
│       │             │            │       │
│  ┌────▼─────────────▼────────────▼──┐   │
│  │            HDFS                   │   │  ← Persistent storage
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

| Component | Role |
|-----------|------|
| **ZooKeeper** | Cluster coordination, master election, region server tracking |
| **HMaster** | DDL operations, region assignment, load balancing |
| **RegionServer** | Serves read/write requests for its regions |
| **Region** | Contiguous range of rows (auto-split when too large) |
| **HDFS** | Persistent distributed storage layer |

---

## Read/Write Path (Interview Must-Know)

### Write Path
```
Client → RegionServer → MemStore (in-memory) + WAL (Write-Ahead Log on HDFS)
                                │
                        MemStore full → Flush to HFile (on HDFS)
                                          │
                            Too many HFiles → Compaction
```

### Read Path
```
Client → RegionServer → MemStore (check memory first)
                           │ not found
                           ▼
                      BlockCache (check cache)
                           │ not found
                           ▼
                      HFiles (on HDFS) → Bloom Filter → Read block
```

---

## Key Operations

```java
// Put (write)
Put put = new Put(Bytes.toBytes("user001"));
put.addColumn(Bytes.toBytes("personal"), Bytes.toBytes("name"), Bytes.toBytes("Sunil"));
table.put(put);

// Get (read by row key)
Get get = new Get(Bytes.toBytes("user001"));
Result result = table.get(get);

// Scan (range query)
Scan scan = new Scan();
scan.withStartRow(Bytes.toBytes("user001"));
scan.withStopRow(Bytes.toBytes("user100"));
ResultScanner scanner = table.getScanner(scan);

// Delete
Delete delete = new Delete(Bytes.toBytes("user001"));
table.delete(delete);
```

---

## RowKey Design — Most Critical Decision

| Principle | Why |
|-----------|-----|
| **Avoid hotspotting** | Sequential keys (timestamps) → all writes hit one region |
| **Salting** | Prepend random prefix → distribute writes across regions |
| **Hashing** | MD5/SHA of key → uniform distribution |
| **Reverse timestamp** | `Long.MAX - timestamp` → recent data first |
| **Composite keys** | `userId_timestamp` → efficient scans per user |

```
❌ Bad:  "20240101_event_1" → Sequential → hotspot on one region
✅ Good: "3_20240101_event_1" → Salted → distributed across regions
✅ Good: "a1b2c3_event_1" → Hashed → uniform distribution
```

---

## HBase vs Other Databases

| Feature | HBase | Cassandra | MongoDB | RDBMS |
|---------|-------|-----------|---------|-------|
| Data Model | Column-family | Column-family | Document | Table/Row |
| Scale | PB+ | PB+ | TB+ | TB |
| Consistency | Strong (per row) | Tunable | Tunable | Strong (ACID) |
| Write speed | Very fast | Very fast | Fast | Medium |
| Random reads | Fast (with cache) | Fast | Fast | Fast |
| Scans | Fast (sorted) | Slow | Slow | Depends |
| Schema | Schema-less | Schema-less | Schema-less | Fixed schema |
| SQL Support | No (use Phoenix) | CQL | MQL | Full SQL |

---

## 🔥 Top 10 Interview Questions (Quick Answers)

| # | Question | Key Answer |
|---|----------|-----------|
| 1 | What is HBase? | Distributed column-family NoSQL DB on HDFS, modeled after Google Bigtable. |
| 2 | Data model? | (RowKey, ColumnFamily:Qualifier, Timestamp) → Value. Sorted by RowKey. |
| 3 | Write path? | Client → WAL + MemStore → Flush to HFile → Compaction |
| 4 | Read path? | MemStore → BlockCache → HFiles (with Bloom filters) |
| 5 | What is a Region? | Contiguous range of rows, served by one RegionServer. Auto-splits. |
| 6 | Role of ZooKeeper? | Master election, region server tracking, cluster coordination. |
| 7 | RowKey design? | Most critical. Avoid hotspots: salt, hash, or reverse timestamps. |
| 8 | What is compaction? | Merges HFiles. Minor = merge small files. Major = merge all + delete tombstones. |
| 9 | HBase vs Cassandra? | HBase = strong consistency, HDFS-based. Cassandra = eventual, peer-to-peer. |
| 10 | When to use HBase? | Billions of rows, random R/W at scale, write-heavy, time-series data. |

---

## Quick Reference

```
RowKey         = Primary key (sorted lexicographically)
Column Family  = Group of columns (stored together)
Region         = Horizontal partition of a table (range of rows)
RegionServer   = Serves regions (handles R/W)
HMaster        = DDL, region assignment, load balancing
MemStore       = In-memory write buffer (per region per CF)
WAL            = Write-Ahead Log (durability on write)
HFile          = On-disk storage format (sorted, immutable)
Compaction     = Merge HFiles (minor: small files, major: all files)
BlockCache     = Read cache (LRU, in-memory)
```

