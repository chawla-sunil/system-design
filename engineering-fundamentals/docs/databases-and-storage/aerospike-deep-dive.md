# 🚀 Aerospike Deep Dive — Senior Engineer's Complete Reference

> Everything a senior engineer should know about Aerospike.  
> From data model to cluster management, SSD optimization, and production best practices.

---

## Table of Contents

1. [What is Aerospike — Really?](#1-what-is-aerospike--really)
2. [Data Model — Namespaces, Sets, Records, Bins](#2-data-model--namespaces-sets-records-bins)
3. [Architecture — Shared-Nothing, Peer-to-Peer](#3-architecture--shared-nothing-peer-to-peer)
4. [Data Distribution — Smart Partitioning](#4-data-distribution--smart-partitioning)
5. [Storage Engine — Why Aerospike is Fast](#5-storage-engine--why-aerospike-is-fast)
6. [Write Path — How Writes Work](#6-write-path--how-writes-work)
7. [Read Path — How Reads Work](#7-read-path--how-reads-work)
8. [Consistency Models — AP vs SC](#8-consistency-models--ap-vs-sc)
9. [Cross-Datacenter Replication (XDR)](#9-cross-datacenter-replication-xdr)
10. [Secondary Indexes & Queries](#10-secondary-indexes--queries)
11. [UDFs — Server-Side Processing](#11-udfs--server-side-processing)
12. [TTL and Eviction — Data Lifecycle](#12-ttl-and-eviction--data-lifecycle)
13. [Client Architecture — Smart Client](#13-client-architecture--smart-client)
14. [Batch Operations & Scan/Query](#14-batch-operations--scanquery)
15. [Java Client — Code Examples](#15-java-client--code-examples)
16. [Monitoring & Management — aql, asadm](#16-monitoring--management--aql-asadm)
17. [Performance Tuning](#17-performance-tuning)
18. [Aerospike vs Redis vs Cassandra vs DynamoDB](#18-aerospike-vs-redis-vs-cassandra-vs-dynamodb)
19. [Production Best Practices](#19-production-best-practices)
20. [Interview Q&A — 30 Questions](#20-interview-qa--30-questions)

---

## 1. What is Aerospike — Really?

Aerospike is a **distributed, scalable NoSQL database** designed for applications that need:
- **Sub-millisecond latency** at scale
- **Millions of transactions per second**
- **Terabytes of data** on flash/SSD (not just RAM)
- **Strong consistency** or **high availability** (configurable)
- **Auto-scaling** with zero-downtime rebalancing

### Origin Story

Built by former engineers from large ad-tech companies. The name comes from "**aero**" (fast) + "**spike**" (sharp peak performance). Originally designed for the **real-time bidding (RTB)** market where you have **<10ms to decide** which ad to show.

### Where Aerospike Is Used in Production

| Company | Use Case |
|---------|----------|
| **PayPal** | Fraud detection, risk scoring |
| **Flipkart** | Session store, user profiles |
| **Airtel** | Real-time subscriber data |
| **Adobe** | Audience segmentation |
| **The Trade Desk** | Real-time ad bidding |
| **Snap** | Messaging metadata |
| **DBS Bank** | Real-time fraud detection |

---

## 2. Data Model — Namespaces, Sets, Records, Bins

### Hierarchy

```
Cluster
└── Namespace (like a database)
    └── Set (like a table — optional)
        └── Record (like a row)
            ├── Key (primary key — any type)
            ├── Digest (20-byte RIPEMD-160 hash of key)
            ├── Metadata (generation, TTL, last-update-time)
            └── Bins (like columns — name:value pairs)
                ├── Bin "name" : "Sunil"
                ├── Bin "age"  : 28
                └── Bin "tags" : ["java", "kafka"]
```

### Namespace

```
- Top-level container
- Defines storage policy (RAM, SSD, or hybrid)
- Defines replication factor
- Defines default TTL
- Defined in aerospike.conf (not dynamic)

namespace users {
    replication-factor 2
    memory-size 4G
    default-ttl 30d
    storage-engine device {
        device /dev/sdb
        write-block-size 1M
    }
}
```

### Record

```
Record = Key + Metadata + Bins

Key Types:
- Integer (long)
- String
- Bytes (byte array)

Digest:
- RIPEMD-160 hash of (namespace + set + key)
- 20 bytes
- Used for partition assignment
- Stored in primary index (in RAM)

Metadata:
- Generation: Version counter (incremented on each write)
- TTL: Time-to-live (0 = never expire, -1 = namespace default)
- Last Update Time (LUT)

Bins:
- Schema-less (no predefined schema)
- Different records in same set can have different bins
- Supported types: Integer, String, Double, Bytes, List, Map, GeoJSON, Boolean
```

### Data Types

| Type | Description | Example |
|------|-------------|---------|
| Integer | 64-bit signed | `42` |
| String | UTF-8 string | `"Sunil"` |
| Double | 64-bit IEEE 754 | `3.14` |
| Bytes | Byte array | Binary data |
| List | Ordered collection | `["a", "b", "c"]` |
| Map | Key-value pairs | `{"city": "Delhi", "zip": 110001}` |
| GeoJSON | Geographic data | `{"type": "Point", "coordinates": [77.2, 28.6]}` |
| Boolean | True/False | `true` |
| HyperLogLog | Probabilistic cardinality | Unique user counts |

---

## 3. Architecture — Shared-Nothing, Peer-to-Peer

### Cluster Topology

```
┌───────────────────────────────────────────────────────┐
│                   Aerospike Cluster                     │
│                                                        │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐        │
│  │  Node 1  │◀──▶│  Node 2  │◀──▶│  Node 3  │        │
│  │          │    │          │    │          │        │
│  │Index(RAM)│    │Index(RAM)│    │Index(RAM)│        │
│  │Data(SSD) │    │Data(SSD) │    │Data(SSD) │        │
│  └──────────┘    └──────────┘    └──────────┘        │
│       ▲               ▲               ▲               │
│       └───────────────┼───────────────┘               │
│              Gossip Protocol                           │
│         (heartbeat, cluster state)                     │
└───────────────────────────────────────────────────────┘
```

### Key Architecture Properties

| Property | Detail |
|----------|--------|
| **Shared-nothing** | Each node has its own storage. No shared disk. |
| **Peer-to-peer** | No master node. All nodes are equal. |
| **Auto-discovery** | Nodes find each other via gossip (heartbeat/mesh) |
| **Auto-rebalancing** | Add/remove nodes → data automatically redistributed |
| **No SPOF** | Any node can go down, cluster continues |

### Cluster Discovery

```
Two modes:
1. Multicast: Nodes broadcast on network (simple, LAN only)
2. Mesh: Nodes configured with seed addresses (recommended, works across networks)

heartbeat {
    mode mesh
    address 10.0.0.1
    mesh-seed-address-port 10.0.0.2 3002
    mesh-seed-address-port 10.0.0.3 3002
}
```

---

## 4. Data Distribution — Smart Partitioning

### 4096 Partitions

```
Every namespace has exactly 4096 partitions.
Each record mapped to a partition by: hash(digest) % 4096

Example with 3 nodes:
Partitions 0-1365    → Node 1 (master)
Partitions 1366-2730 → Node 2 (master)
Partitions 2731-4095 → Node 3 (master)

With replication factor 2:
Partition 0: Master on Node 1, Replica on Node 2
Partition 1: Master on Node 1, Replica on Node 3
...

On node add/remove: partitions redistributed automatically!
```

### Rebalancing (Zero Downtime)

```
3-node cluster → Add Node 4:

Before:
Node 1: P0-P1365
Node 2: P1366-P2730
Node 3: P2731-P4095

After (automatic):
Node 1: P0-P1023
Node 2: P1024-P2047
Node 3: P2048-P3071
Node 4: P3072-P4095

Migration happens in background.
Reads/writes continue during migration.
Client transparently redirected to new partition owners.
```

---

## 5. Storage Engine — Why Aerospike is Fast

### Three Storage Modes

#### 1. In-Memory (RAM)

```
- All data in RAM
- Optional persistence to disk (for restart recovery)
- Fastest mode
- Limited by RAM size ($$$)

storage-engine memory
```

#### 2. SSD/Flash (Hybrid)

```
- Primary index: Always in RAM (~64 bytes/record)
- Data: On SSD/NVMe
- Sub-millisecond reads (single I/O)
- Most common production mode
- Cost-effective for large datasets

storage-engine device {
    device /dev/nvme0n1    # Raw device (no filesystem!)
    write-block-size 1M
}
```

#### 3. Persistent Memory (PMem)

```
- Intel Optane / persistent memory
- Byte-addressable like RAM
- Persistent like SSD
- Ultra-low latency

storage-engine pmem {
    file /mnt/pmem/aerospike.dat
    filesize 256G
}
```

### Why SSD Performance is Exceptional

```
Traditional DB on SSD:
App → Filesystem → Block Layer → SSD
     (overhead)   (overhead)

Aerospike on SSD:
App → Direct SSD I/O (bypass filesystem!)
     - Uses raw device (/dev/sdb, not /dev/sdb1 with ext4)
     - Knows SSD read/write characteristics
     - Large sequential writes (write-block-size)
     - Single random read per record (index has exact offset)
     - No filesystem fragmentation issues
     - Concurrent I/O across multiple SSD devices
```

### Primary Index — Always in RAM

```
Every record has a 64-byte index entry in RAM:

┌────────────────────────────────────────────┐
│ Primary Index Entry (64 bytes)              │
│                                             │
│ ├── Digest (20 bytes)      ← RIPEMD-160   │
│ ├── Partition ID           ← Which partition│
│ ├── Generation             ← Version count  │
│ ├── TTL/Void Time          ← Expiration    │
│ ├── Storage address        ← SSD offset    │
│ └── Set name pointer                        │
└────────────────────────────────────────────┘

100 million records × 64 bytes = ~6.4 GB RAM for index
→ This is why reads are fast: one RAM lookup → one SSD read
```

---

## 6. Write Path — How Writes Work

```
Client.put(key, bins)
    │
    ▼
Smart Client: hash(key) → partition → determine master node
    │
    ▼
Master Node receives write
    │
    ├──1──▶ Write to in-memory buffer (write-block buffer)
    │
    ├──2──▶ Update primary index (in RAM)
    │
    ├──3──▶ When buffer full (write-block-size) → write entire block to SSD
    │       - Sequential write (SSD-friendly)
    │       - Multiple records per block
    │
    └──4──▶ Replicate to replica node(s)
            - Synchronous by default (AP mode: after master write)
            - In SC mode: write confirmed only after replica ACK

Generation counter incremented on each write.
```

### Write Block

```
┌─────────────────────────────────────────┐
│        Write Block (default 1 MB)        │
│                                          │
│  [Record A][Record B][Record C][...]     │
│                                          │
│  Written to SSD as one sequential I/O    │
│  Records from different keys batched     │
└─────────────────────────────────────────┘
```

### Defragmentation

```
Problem: After updates/deletes, old data blocks have "holes"
         (records that have been updated, so old copies are stale)

Solution: Background defragmenter
1. Reads blocks with many stale records
2. Copies live records to new write buffer
3. Reclaims old blocks

Config:
defrag-lwm-pct 50   # Defragment blocks that are <50% live data
```

---

## 7. Read Path — How Reads Work

```
Client.get(key)
    │
    ▼
Smart Client: hash(key) → partition → determine master node
    │
    ▼
Node receives read
    │
    ├──1──▶ Lookup primary index (in RAM)
    │       - Hash digest → find index entry → get SSD address
    │       - O(1) lookup
    │
    └──2──▶ Read record from SSD at exact address
            - Single random read (4KB-8KB typically)
            - Sub-millisecond on NVMe
            │
            ▼
    Return record to client

Total: ~1ms for SSD read (including network)
       ~0.1ms for in-memory read
```

### Read Policies

| Policy | Behavior |
|--------|----------|
| `MASTER` | Read from master only (default) |
| `MASTER_PROLES` | Read from master or any replica (lower latency) |
| `SEQUENCE` | Try master first, then replicas on timeout |
| `PREFER_RACK` | Read from same rack/AZ first |

---

## 8. Consistency Models — AP vs SC

### AP Mode (Available-Partition tolerant) — Default

```
- High availability
- Eventual consistency
- On network partition: both sides continue serving reads/writes
- After partition heals: conflict resolution by generation count (last write wins)

Best for: Session stores, caching, user profiles
```

### SC Mode (Strong Consistency) — Aerospike 4.0+

```
- Linearizable reads and writes
- On network partition: minority partition stops serving writes
- Uses Raft-like protocol for leader election per partition
- Slightly higher latency (~0.5ms more)

Best for: Financial transactions, inventory counts, fraud detection
```

```
// Configure in aerospike.conf
namespace finance {
    strong-consistency true
    ...
}
```

### Conflict Resolution (AP Mode)

```
Network partition heals:
- Node A has Record X (generation 5)
- Node B has Record X (generation 7)
- Resolution: generation 7 wins (latest write)

If same generation: last-update-time wins
```

---

## 9. Cross-Datacenter Replication (XDR)

```
Datacenter 1 (Active)          Datacenter 2 (Passive/Active)
┌──────────────┐               ┌──────────────┐
│ Aerospike    │──── XDR ────▶│ Aerospike    │
│ Cluster A    │  (async)      │ Cluster B    │
└──────────────┘               └──────────────┘

Modes:
1. Active-Passive: DC1 writes, DC2 reads (disaster recovery)
2. Active-Active: Both DCs write, conflicts resolved by generation/LUT
3. Star topology: Hub DC replicates to spoke DCs

XDR ships write transactions:
- Asynchronous (doesn't slow down writes)
- At-least-once delivery
- Configurable: which namespaces/sets to replicate
- Configurable: ship only creates, updates, or all operations
```

---

## 10. Secondary Indexes & Queries

### Secondary Index

```
Primary key lookup: O(1) → always fast
Secondary index: Query by non-key field

// Create index
aql> CREATE INDEX idx_city ON users.profiles (city) STRING

// Query using index
aql> SELECT * FROM users.profiles WHERE city = 'Delhi'
```

### How Secondary Indexes Work

```
- Built on each node for its local data
- Stored in RAM (sindex)
- Not replicated (rebuilt on each node)
- Support: equality, range (numeric), geo (within radius)

Types:
- STRING: Equality queries
- NUMERIC: Range queries  
- GEO2DSPHERE: Geospatial queries (points within region)
```

### Limitations

```
- Exact match or range only (no LIKE, no regex)
- High cardinality fields may use too much RAM
- Not suitable for analytics (use Spark connector instead)
- No composite indexes (single bin only)
```

---

## 11. UDFs — Server-Side Processing

### Record UDFs

```lua
-- Lua UDF: increment bin by 1
function increment(rec, bin_name)
    if aerospike:exists(rec) then
        rec[bin_name] = (rec[bin_name] or 0) + 1
        aerospike:update(rec)
    end
    return rec[bin_name]
end
```

```java
// Call from Java
client.execute(null, key, "mymodule", "increment", Value.get("login_count"));
```

### Stream UDFs (Aggregation)

```lua
-- Aggregate: count records where age > 25
function age_filter(stream)
    local function filter_fn(rec)
        return rec.age > 25
    end
    local function count_fn(count, rec)
        return count + 1
    end
    return stream : filter(filter_fn) : reduce(count_fn, 0)
end
```

---

## 12. TTL and Eviction — Data Lifecycle

### TTL (Time-To-Live)

```
- Set per record or per namespace (default)
- Records automatically expired by background thread (nsup)
- Expired records removed during defrag or nsup cycle

// Set TTL when writing
WritePolicy policy = new WritePolicy();
policy.expiration = 86400;  // 24 hours in seconds
client.put(policy, key, bins);

// Special values:
// 0         = never expire
// -1        = use namespace default-ttl
// -2        = don't change TTL on update
// positive  = seconds until expiry
```

### Eviction

```
When memory/disk is full:
1. Expired records removed first
2. If still full → eviction kicks in
3. Records evicted based on TTL (closest to expiry removed first)
4. stop-writes-pct: stop accepting writes when disk is 90% full

evict-tenths-pct 5  # Evict ~0.5% of records per cycle
stop-writes-pct 90  # Stop writes at 90% disk usage
```

---

## 13. Client Architecture — Smart Client

### How the Smart Client Works

```
Application ──── Aerospike Smart Client ──── Cluster

The client:
1. Connects to any seed node
2. Discovers full cluster topology (all nodes, partition map)
3. Knows which node owns which partitions
4. Routes requests DIRECTLY to the right node
5. Refreshes partition map on cluster changes

No proxy / coordinator needed!
Client does: hash(key) → partition → node → send request
```

### Connection Pooling

```java
// Client maintains connection pool to every node
ClientPolicy clientPolicy = new ClientPolicy();
clientPolicy.maxConnsPerNode = 300;       // Max connections per node
clientPolicy.connPoolsPerNode = 1;        // Connection pools per node
clientPolicy.timeout = 1000;              // Connection timeout (ms)
clientPolicy.tendInterval = 1000;         // Cluster tend interval (ms)
clientPolicy.failIfNotConnected = true;

AerospikeClient client = new AerospikeClient(clientPolicy, "seed-host", 3000);
```

---

## 14. Batch Operations & Scan/Query

### Batch Read

```java
// Read multiple keys in one call
Key[] keys = new Key[] {
    new Key("users", "profiles", "user001"),
    new Key("users", "profiles", "user002"),
    new Key("users", "profiles", "user003")
};

Record[] records = client.get(null, keys);
```

### Scan (Full Namespace/Set Scan)

```java
// Scan all records in a set
ScanPolicy scanPolicy = new ScanPolicy();
scanPolicy.concurrentNodes = true;
scanPolicy.includeBinData = true;

client.scanAll(scanPolicy, "users", "profiles", (key, record) -> {
    System.out.println(record.getString("name"));
});
```

### Query (With Secondary Index)

```java
Statement stmt = new Statement();
stmt.setNamespace("users");
stmt.setSetName("profiles");
stmt.setFilter(Filter.range("age", 25, 35));  // Requires secondary index on "age"

RecordSet rs = client.query(null, stmt);
while (rs.next()) {
    Record record = rs.getRecord();
    System.out.println(record.getString("name"));
}
```

---

## 15. Java Client — Code Examples

### Setup

```xml
<dependency>
    <groupId>com.aerospike</groupId>
    <artifactId>aerospike-client-jdk8</artifactId>
    <version>7.2.1</version>
</dependency>
```

### CRUD Operations

```java
// Connect
AerospikeClient client = new AerospikeClient("localhost", 3000);

// Write
Key key = new Key("users", "profiles", "user001");
Bin name = new Bin("name", "Sunil");
Bin age = new Bin("age", 28);
Bin tags = new Bin("tags", Arrays.asList("java", "kafka", "aerospike"));
client.put(null, key, name, age, tags);

// Read
Record record = client.get(null, key);
String userName = record.getString("name");
int userAge = record.getInt("age");

// Update specific bin
client.put(null, key, new Bin("age", 29));  // Only updates "age" bin

// Delete
client.delete(null, key);

// Check existence
boolean exists = client.exists(null, key);

// Atomic increment
client.add(null, key, new Bin("login_count", 1));  // Thread-safe increment

// Atomic read-modify-write (CAS)
WritePolicy wp = new WritePolicy();
wp.generationPolicy = GenerationPolicy.EXPECT_GEN_EQUAL;
wp.generation = record.generation;  // Only write if generation matches
client.put(wp, key, new Bin("balance", 500));

// Close
client.close();
```

### Operations API (Atomic Multi-Bin Operations)

```java
// Atomic: increment login_count AND update last_login in one call
Record result = client.operate(null, key,
    Operation.add(new Bin("login_count", 1)),
    Operation.put(new Bin("last_login", System.currentTimeMillis())),
    Operation.get("login_count")
);
```

### List & Map Operations

```java
// Append to list
client.operate(null, key,
    ListOperation.append("tags", Value.get("docker"))
);

// Map operations
client.operate(null, key,
    MapOperation.put(MapPolicy.Default, "preferences",
        Value.get("theme"), Value.get("dark"))
);
```

---

## 16. Monitoring & Management — aql, asadm

### aql (Aerospike Query Language)

```sql
-- Connect
aql

-- Show namespaces
aql> SHOW NAMESPACES

-- Insert record
aql> INSERT INTO users.profiles (PK, name, age) VALUES ('user001', 'Sunil', 28)

-- Select record
aql> SELECT * FROM users.profiles WHERE PK = 'user001'

-- Create secondary index
aql> CREATE INDEX idx_age ON users.profiles (age) NUMERIC

-- Query with index
aql> SELECT * FROM users.profiles WHERE age BETWEEN 25 AND 35
```

### asadm (Aerospike Admin)

```bash
asadm
Admin> info
Admin> info namespace
Admin> show statistics like writes
Admin> show distribution object_size
Admin> show config like replication
```

### Key Metrics to Monitor

| Metric | What It Tells You |
|--------|------------------|
| `client_read_success` | Successful read rate |
| `client_write_success` | Successful write rate |
| `client_read_timeout` | Read timeouts (network/overload) |
| `migrate_progress_recv` | Data migration in progress |
| `device_free_pct` | Free SSD space |
| `memory_free_pct` | Free RAM |
| `objects` | Total record count |
| `stop_writes` | True = node stopped accepting writes |

---

## 17. Performance Tuning

| Technique | Impact |
|-----------|--------|
| **Use NVMe SSDs** | 3-5x faster than SATA SSDs |
| **Tune write-block-size** | 1MB for SSD (match device characteristics) |
| **Batch operations** | Reduce round trips for multi-key ops |
| **Read policy MASTER_PROLES** | Lower latency (read from nearest replica) |
| **Connection pooling** | Reuse connections, don't create per-request |
| **Proper TTL** | Avoid disk full situations |
| **Index in RAM sizing** | 64 bytes × record_count = RAM needed |
| **Multiple SSD devices** | Spread I/O across devices |
| **Rack-aware** | Reduce cross-rack traffic |
| **Compression** | Reduce SSD I/O (LZ4 recommended) |

### Capacity Planning

```
Records: 1 billion
Average record size: 1 KB

RAM needed (primary index): 1B × 64 bytes = 64 GB
SSD needed (data): 1B × 1 KB = 1 TB
With replication factor 2: 2 TB total SSD, 128 GB total RAM

For 3-node cluster:
  Per node: ~43 GB RAM, ~670 GB SSD
  
Rule of thumb: Keep device utilization < 60% for defrag headroom
```

---

## 18. Aerospike vs Redis vs Cassandra vs DynamoDB

| Feature | Aerospike | Redis | Cassandra | DynamoDB |
|---------|-----------|-------|-----------|----------|
| **Primary storage** | SSD + RAM index | RAM | SSD | Managed |
| **Latency** | < 1ms (SSD) | < 0.5ms (RAM) | 2-10ms | 5-10ms |
| **Throughput** | 1M+ TPS | 500K+ TPS | 100K+ TPS | Auto-scales |
| **Data size** | TBs (SSD) | 100s GB (RAM) | PBs | Unlimited |
| **Consistency** | AP or Strong | Strong (single-node) | Tunable | Strong or eventual |
| **Clustering** | Auto (shared-nothing) | Cluster mode | Peer-to-peer | Managed |
| **Replication** | Sync + XDR | Async | Tunable | Managed |
| **Cost** | SSD-priced ($) | RAM-priced ($$$) | SSD-priced ($) | Pay-per-use |
| **Schema** | Schema-less (bins) | Data structures | Schema (CQL) | Schema (key + sort) |
| **Best for** | High TPS at scale | Cache, small data | Write-heavy, wide-column | Serverless, AWS |

### When to Choose What

```
Need < 1ms at 1M+ TPS with TBs of data?          → Aerospike
Need sub-ms cache for < 100GB?                     → Redis
Need to write massive amounts of time-series data? → Cassandra
Want zero-ops managed database on AWS?             → DynamoDB
Need complex queries and transactions?             → PostgreSQL
```

---

## 19. Production Best Practices

```
□ Use raw devices (not filesystem) for SSD storage
□ Use NVMe SSDs for best performance
□ Set replication-factor to 2 (3 for critical data)
□ Configure rack-aware for multi-AZ deployments
□ Monitor device_free_pct (keep > 40%)
□ Set stop-writes-pct appropriately (default 90%)
□ Use TTL for transient data (sessions, cache)
□ Plan RAM for primary index: 64 bytes × records
□ Use batch operations for multi-key access
□ Use Smart Client (don't proxy)
□ Set appropriate connection pool size
□ Monitor and alert on stop_writes
□ Test XDR failover regularly
□ Use compression (LZ4) for large records
□ Keep record size < 1MB (ideally < 8KB)
□ Pre-size namespace to avoid runtime resize
```

---

## 20. Interview Q&A — 30 Questions

| # | Question | Answer |
|---|----------|--------|
| 1 | What is Aerospike? | High-performance distributed NoSQL DB optimized for SSD with sub-ms latency |
| 2 | Data model? | Namespace → Set → Record → Bins. Like Database → Table → Row → Columns. |
| 3 | What is a digest? | 20-byte RIPEMD-160 hash of (namespace + set + key). Used for partitioning. |
| 4 | How many partitions? | 4096 fixed. Distributed across nodes. |
| 5 | Why is Aerospike fast on SSD? | Direct device I/O (no filesystem), index in RAM, single I/O per read. |
| 6 | What's in RAM vs SSD? | Primary index (64 bytes/record) in RAM. Data on SSD. |
| 7 | Master-slave or peer-to-peer? | Peer-to-peer. No master node. Shared-nothing. |
| 8 | How do writes work? | Buffer in memory → batch write to SSD (write-block) → replicate to replica. |
| 9 | How do reads work? | Index lookup (RAM) → single SSD read at exact address. Sub-ms. |
| 10 | AP vs SC mode? | AP = high availability, eventual consistency. SC = strong consistency, Raft-like. |
| 11 | What is XDR? | Cross-Datacenter Replication. Async. Active-passive or active-active. |
| 12 | Aerospike vs Redis? | Aerospike = SSD, TBs, cheaper. Redis = RAM, 100s GB, faster but expensive. |
| 13 | Aerospike vs Cassandra? | Aerospike = sub-ms, peer-to-peer, SSD-optimized. Cassandra = higher latency, write-optimized. |
| 14 | What is Smart Client? | Client knows partition map, routes directly to correct node. No proxy. |
| 15 | How does rebalancing work? | Add node → partitions redistributed automatically. Zero downtime. |
| 16 | What is defragmentation? | Background process that reclaims SSD blocks with stale records. |
| 17 | What are secondary indexes? | In-memory indexes on non-key bins. For equality and range queries. |
| 18 | Secondary index limitations? | RAM-only, single bin, no composite, high cardinality expensive. |
| 19 | What is TTL? | Time-to-live. Records auto-expire. Background thread (nsup) removes them. |
| 20 | What is eviction? | When storage is full, records closest to expiry are removed. |
| 21 | What is generation? | Version counter per record. Incremented on each write. For CAS. |
| 22 | How to do CAS? | Read generation → write with EXPECT_GEN_EQUAL → fails if changed. |
| 23 | What is a UDF? | User-Defined Function. Lua scripts run on server. Record-level or stream. |
| 24 | What is write-block-size? | Size of I/O block written to SSD. Records buffered until block full. |
| 25 | How to handle conflicts in AP? | Higher generation wins. Same generation → last-update-time wins. |
| 26 | Rack-aware configuration? | Replicas placed in different racks/AZs for fault tolerance. |
| 27 | How to monitor? | asadm, aql, Prometheus exporter, management console. |
| 28 | Capacity planning formula? | RAM: 64B × records. SSD: avg_record_size × records × replication_factor. |
| 29 | Use cases? | Ad tech, fraud detection, session store, recommendation, user profiles. |
| 30 | Namespace vs Set? | Namespace = storage config, replication (static). Set = logical grouping (dynamic). |

---

> **Pro Tip for Interviews:** Aerospike's secret sauce is "index in RAM, data on SSD." Explain: "One RAM lookup gives the exact SSD address, so it's always a single I/O — that's why we get sub-millisecond reads even from disk. This gives us Redis-like performance at SSD-like cost."

