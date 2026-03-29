# ⚡ Aerospike in 5 Minutes — Interview Cheat Sheet

> Quick-fire Aerospike concepts. Know these for distributed systems and high-performance database interviews.

---

## What is Aerospike?

**One line:** Aerospike is a **high-performance, distributed NoSQL key-value/document database** optimized for **flash (SSD) storage** with **sub-millisecond latency** at scale.

**Think of it as:** Redis performance + disk persistence + auto-clustering. Designed for use cases needing **millions of operations per second** with strong consistency and high availability.

---

## When to Use Aerospike

| ✅ Use When | ❌ Don't Use When |
|-------------|-------------------|
| Need sub-millisecond latency at scale | Complex relational queries (SQL joins) |
| Millions of TPS required | Small-scale, simple applications |
| Session stores, user profiles | Full-text search (use Elasticsearch) |
| Real-time recommendation engines | Analytics/OLAP workloads |
| Ad tech (user targeting, frequency capping) | When Redis is sufficient (small dataset) |
| Fraud detection, real-time scoring | Graph traversals |

---

## Data Model

```
Namespace → Set → Record → Bins

┌──────────────────────────────────────────────┐
│ Namespace: "users"  (like a database)         │
│                                               │
│  ┌─────────────────────────────────────────┐ │
│  │ Set: "profiles"  (like a table)          │ │
│  │                                          │ │
│  │  Record (Key: "user001"):                │ │
│  │  ┌──────────┬──────────┬──────────────┐ │ │
│  │  │Bin: name │Bin: age  │Bin: email    │ │ │
│  │  │"Sunil"   │ 28       │"s@email.com" │ │ │
│  │  └──────────┴──────────┴──────────────┘ │ │
│  │                                          │ │
│  │  Record (Key: "user002"):                │ │
│  │  ┌──────────┬──────────┐                │ │
│  │  │Bin: name │Bin: city │                │ │
│  │  │"Amit"    │"Mumbai"  │                │ │
│  │  └──────────┴──────────┘                │ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘

Namespace = Database    (storage config, replication, TTL)
Set       = Table       (logical grouping)
Record    = Row         (identified by primary key)
Bin       = Column      (name-value pair, schema-less)
```

---

## Architecture — Quick Overview

```
┌─────────────────────────────────────────┐
│          Aerospike Cluster               │
│                                          │
│  ┌────────┐  ┌────────┐  ┌────────┐    │
│  │ Node 1 │◀─▶│ Node 2 │◀─▶│ Node 3 │  │
│  └────────┘  └────────┘  └────────┘    │
│                                          │
│  Shared-nothing architecture             │
│  No master node (all nodes are peers)    │
│  Data partitioned into 4096 partitions   │
│  Each partition has a master + replicas  │
└─────────────────────────────────────────┘

Key features:
- No single point of failure
- Auto-sharding (4096 partitions)
- Auto-rebalancing on node add/remove
- Sub-millisecond reads from SSD (optimized I/O)
```

---

## Storage Architecture — Hybrid Memory

```
┌─────────────────────────────────────────┐
│           Storage Options                │
│                                          │
│  1. In-Memory: Data in RAM only (fast!)  │
│                                          │
│  2. SSD/Flash: Index in RAM,             │
│                Data on SSD               │  ← Most common
│                (sub-ms reads!)           │
│                                          │
│  3. Persistent Memory (PMem):            │
│     Intel Optane, ultra-fast             │
└─────────────────────────────────────────┘

Primary Index: ALWAYS in RAM (~64 bytes per record)
Data: RAM or SSD (configurable per namespace)

Why SSD is fast in Aerospike:
- Bypasses filesystem, writes directly to raw device
- Large block writes optimized for SSD characteristics
- Reads are single I/O operation (index points to exact SSD location)
```

---

## Key Features

| Feature | What It Does |
|---------|-------------|
| **Sub-ms latency** | Optimized SSD access + in-memory index |
| **Auto-sharding** | 4096 partitions distributed across nodes |
| **Auto-rebalancing** | Add/remove nodes seamlessly |
| **Strong Consistency mode** | Linearizable reads (Aerospike 4.0+) |
| **Cross-Datacenter Replication (XDR)** | Async replication across data centers |
| **Secondary Indexes** | Query by non-primary key fields |
| **UDFs (User Defined Functions)** | Server-side Lua scripting |
| **TTL** | Records auto-expire |
| **Batch operations** | Multi-key reads/writes in one call |

---

## Aerospike vs Others

| Feature | Aerospike | Redis | Cassandra | DynamoDB |
|---------|-----------|-------|-----------|----------|
| **Latency** | < 1ms | < 1ms (RAM) | 2-10ms | 5-10ms |
| **Storage** | SSD + RAM | RAM (+ persistence) | SSD | Managed |
| **Scaling** | Auto-shard | Manual | Auto | Auto |
| **Consistency** | Strong or AP | Strong (single) | Tunable | Strong or eventual |
| **Data size** | TBs (SSD) | 100s GB (RAM) | PBs | Unlimited |
| **Cost** | SSD-priced | RAM-priced ($$) | SSD-priced | Pay per request |
| **Best for** | High TPS + SSD | Cache, small data | Write-heavy | Serverless |

---

## 🔥 Top 10 Interview Questions (Quick Answers)

| # | Question | Key Answer |
|---|----------|-----------|
| 1 | What is Aerospike? | High-performance, distributed NoSQL KV database optimized for SSD with sub-ms latency. |
| 2 | Data model? | Namespace → Set → Record → Bins. Like Database → Table → Row → Columns. |
| 3 | Why is Aerospike fast on SSD? | Bypasses filesystem, direct SSD I/O, index always in RAM, optimized block writes. |
| 4 | How is data distributed? | 4096 partitions. Hash of key → partition → node. Auto-rebalanced. |
| 5 | Single point of failure? | No. Shared-nothing, peer-to-peer. Any node can serve any request. |
| 6 | Consistency model? | Available-Partition tolerant (AP) by default. Strong Consistency mode available. |
| 7 | Aerospike vs Redis? | Aerospike = SSD-based (cheaper for large data). Redis = RAM-only (expensive at scale). |
| 8 | What is XDR? | Cross-Datacenter Replication. Async replication for disaster recovery. |
| 9 | What is a namespace? | Top-level storage unit. Defines storage engine, replication, TTL policies. |
| 10 | Use cases? | Ad tech, session store, user profiles, fraud detection, real-time recommendations. |

---

## Quick Reference

```
Namespace  = Database (storage config)
Set        = Table (logical grouping)
Record     = Row (identified by key, has digest)
Bin        = Column (name-value, schema-less)
Digest     = 20-byte hash of (namespace + set + key)
Partition  = Data shard (4096 total, distributed)
XDR        = Cross-datacenter replication
TTL        = Time-to-live (auto-expiry)
UDF        = User-defined function (Lua scripts)
```

