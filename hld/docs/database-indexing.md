# Database Indexing — HLD Interview Simulation (≈ 60 min)

> **Scenario:** *"You are a software engineer with 6-7 years of experience. Walk us through how DBMS indexing works and how you would design / choose indexes to improve query performance for a high-traffic OLTP service."*

This doc simulates a real 1-hour HLD interview. Each section has a time-box, the **interviewer's question**, my **thought process / approach**, and the **answer** with diagrams, examples, and trade-offs.

---

## 0. Table of Contents

1. [Clarify the problem (5 min)](#1-clarify-the-problem-5-min)
2. [Why indexing? The first-principles view (5 min)](#2-why-indexing-the-first-principles-view-5-min)
3. [Storage internals: pages, heap, I/O cost model (5 min)](#3-storage-internals-pages-heap-io-cost-model-5-min)
4. [B-Tree / B+Tree — the workhorse (10 min)](#4-b-tree--btree--the-workhorse-10-min)
5. [Hash, Bitmap, GiST, GIN, BRIN, R-Tree, LSM (8 min)](#5-hash-bitmap-gist-gin-brin-r-tree-lsm-8-min)
6. [Clustered vs Non-Clustered, Covering, Composite (8 min)](#6-clustered-vs-non-clustered-covering-composite-8-min)
7. [Query planner, EXPLAIN, cardinality, selectivity (5 min)](#7-query-planner-explain-cardinality-selectivity-5-min)
8. [Designing indexes for an e-commerce schema (8 min)](#8-designing-indexes-for-an-ecommerce-schema-8-min)
9. [Anti-patterns, write amplification, maintenance (3 min)](#9-anti-patterns-write-amplification-maintenance-3-min)
10. [Wrap-up, cheat sheet, follow-up Q&A (3 min)](#10-wrap-up-cheat-sheet-followup-qa-3-min)

---

## 1. Clarify the problem (5 min)

### Interviewer
> "How would you approach a query that's running slow? And in general, how does indexing speed things up?"

### My approach (think out loud)
Before answering "how indexes work", I always **clarify the workload**, because the right index strategy is workload-dependent:

**Questions I ask the interviewer:**
1. **Workload type?** — OLTP (point lookups, short txns) vs OLAP (scans, aggregations) vs hybrid (HTAP)?
2. **Read/Write ratio?** — Indexes help reads but **hurt writes**.
3. **Data size & cardinality?** — Millions of rows? Billions? Distinct values per column?
4. **Query patterns?** — Equality? Range? Prefix? Full-text? Geo? JSON?
5. **DBMS?** — Postgres, MySQL/InnoDB, Oracle, SQL Server, Cassandra, MongoDB, DynamoDB? (Each has different index types.)
6. **Latency / Throughput SLOs?** — p99 < 50ms? 10K QPS?
7. **Storage engine?** — Row store (B-Tree) vs LSM (Cassandra/RocksDB) vs columnar (Parquet/Redshift)?

**For this interview I'll assume:**
- **Postgres / MySQL InnoDB** (most common in interviews).
- **OLTP e-commerce** workload — 80% reads, 20% writes, ~500M `orders`, p99 < 100 ms.

---

## 2. Why indexing? The first-principles view (5 min)

### Interviewer
> "Explain the intuition. Why does an index speed up a query?"

### Answer

A table is just a **heap of rows on disk**. Without an index:

```
SELECT * FROM users WHERE email = 'sunil@x.com';
                                  │
                                  ▼
   ┌──────────────────────────────────────┐
   │ Full Table Scan: read every page,    │  O(N) I/O
   │ filter rows in memory                │  ➜ 500M rows ≈ minutes
   └──────────────────────────────────────┘
```

An **index** is a separate, **sorted** data structure (usually a B+Tree) that maps a **key → row location (TID / RID / ROWID / primary key)**:

```
   Index on email                              Heap
   ┌─────────────┐                       ┌──────────────────┐
   │ ali@x.com   │──▶ ptr (page 17, off 4) ─▶│ row {id, name…} │
   │ bob@x.com   │──▶ ptr (page 3,  off 1) ─▶│ row …           │
   │ sunil@x.com │──▶ ptr (page 9,  off 8) ─▶│ row …           │
   └─────────────┘                       └──────────────────┘
```

**Result:** A point lookup goes from **O(N)** → **O(log N)**.
- 500M rows: **~28 page reads** vs **millions of page reads**.
- That's the difference between **2 ms** and **20 seconds**.

### The trade-off (always mention this!)

| Aspect       | Without Index | With Index |
|--------------|---------------|------------|
| Read (point) | O(N)          | O(log N)   |
| Write (insert/update/delete) | 1 page write | 1 + N\_indexes page writes |
| Disk space   | Just the table | + ~10–30% per index |
| Memory (buffer pool) | — | Index pages compete for cache |

> **"Indexes are not free. Every index adds latency to every write and consumes RAM. The art is picking the smallest set of indexes that satisfy your hot queries."**

---

## 3. Storage internals: pages, heap, I/O cost model (5 min)

### Interviewer
> "Before we get to B-Trees, what's the unit of I/O the database deals with?"

### Answer

DBs read/write in **fixed-size pages (blocks)** — typically **8 KB (Postgres), 16 KB (InnoDB)**.

```
Heap file (orders table)
┌──────────┬──────────┬──────────┬──────────┐
│ Page 0   │ Page 1   │ Page 2   │  …       │   8 KB each
│ row,row  │ row,row  │ row,row  │          │
└──────────┴──────────┴──────────┴──────────┘
```

**Cost model (simplified):**
- **Sequential I/O** ≈ 1 unit per page (cheap, prefetched).
- **Random I/O** ≈ 4–10× more expensive (HDD: ~10 ms seek, SSD: ~100 µs).
- **In buffer pool (RAM)** ≈ ~free.

**Why this matters for indexing:**
- An index lookup is a **few random reads** (tree traversal) + **1 random read** for the heap row.
- A scan is **many sequential reads**.
- **Crossover point:** If a query touches >5–10% of the table, the planner often **prefers a full scan** over an index — random I/O cost dominates.

This is why "having an index" doesn't guarantee it's used.

---

## 4. B-Tree / B+Tree — the workhorse (10 min)

### Interviewer
> "Walk through a B+Tree. Why is it the default index?"

### Answer

A **B+Tree** is a self-balancing N-ary search tree where:
- All keys live in **leaves**, internal nodes are pure routing.
- Leaves are linked in a **doubly linked list** → range scans are sequential.
- Each node = **one disk page** (~8 KB).
- Fanout is huge (hundreds of children per node) → **height is tiny**.

```
                          ┌───────────────┐
                          │   [50 | 100]  │      ← root (1 page)
                          └──┬──────┬──┬──┘
                             │      │  │
              ┌──────────────┘      │  └──────────────┐
              ▼                     ▼                 ▼
        ┌─────────┐           ┌──────────┐      ┌──────────┐
        │ [20|35] │           │ [70|85]  │      │[120|180] │   ← internal
        └─┬──┬──┬─┘           └─┬──┬──┬──┘      └─┬──┬──┬──┘
          ▼  ▼  ▼               ▼  ▼  ▼           ▼  ▼  ▼
        Leaves (sorted, linked):
        [10,15,18] ⇄ [20,25,30] ⇄ [35,40,45] ⇄ [50,…] ⇄ … ⇄ [180,…]
                                 each leaf = (key, row pointer)
```

### Math: why height ≈ 3-4 for billions of rows

- Page = 8 KB, key+pointer ≈ 16 B → **~500 keys/page** (fanout ≈ 500).
- Height 3 ⇒ 500³ = **125 million** entries.
- Height 4 ⇒ 500⁴ = **62 billion** entries.

> **A B+Tree of any practical size is ≤ 4 levels deep.** Top 2-3 levels almost always sit in RAM (buffer pool). So a lookup = **~1 disk read for the leaf + 1 for the heap row**.

### Operations

| Op            | Complexity | Notes |
|---------------|------------|-------|
| Equality `=`  | O(log N)   | Walk to leaf |
| Range `BETWEEN`, `<`, `>` | O(log N + k) | Walk to start, then traverse linked leaves |
| `ORDER BY` (matching index) | O(N) but no sort step | Use index ordering directly |
| Prefix match `LIKE 'abc%'` | O(log N + k) | ✅ works |
| Suffix `LIKE '%abc'` | ❌ Full scan | Index useless |
| `IS NULL` | Postgres ✅, MySQL depends | |

### Insert / Split

- Insert into the right leaf.
- If leaf is full → **split** into two, push median up.
- Splits cascade upward (rare). **Cost ≈ O(log N) writes.**

```
Insert 60:                                  After split:
[50, 55, 58, 59]  ← full                    [50, 55] - [58, 59, 60]
                                                      ↑
                                                   median 58 pushed up
```

### Why B+Tree (not plain BST or binary search)
- Disks read in **pages**, not bytes. High fanout = fewer disk seeks.
- Self-balancing → guaranteed O(log N) worst case.
- Linked leaves → **range queries are cheap**.
- All RDBMS use it: Postgres, MySQL, Oracle, SQL Server, SQLite.

---

## 5. Hash, Bitmap, GiST, GIN, BRIN, R-Tree, LSM (8 min)

### Interviewer
> "When would B-Tree NOT be the right choice?"

### Quick comparison

| Type         | Best for                            | Pros | Cons |
|--------------|-------------------------------------|------|------|
| **B+Tree**   | Equality + range, ordering          | All-rounder | None for general OLTP |
| **Hash**     | Equality only (`=`, `IN`)           | O(1) lookup | No range, no sort, hash collisions |
| **Bitmap**   | Low-cardinality cols (gender, status) in OLAP | Tiny, fast `AND/OR` across columns | Bad for high-write OLTP |
| **GiST / SP-GiST** (Postgres) | Geometric, ranges, custom types | Generic framework | Slower than B-Tree for scalars |
| **GIN** (Postgres) | Full-text (`tsvector`), JSONB, arrays | Fast `@>`, `?`, FTS | Slow writes |
| **BRIN**     | Huge, naturally-ordered tables (time-series) | Tiny (KBs for TBs) | Only useful when data is physically sorted |
| **R-Tree**   | 2D / spatial (`POINT`, `POLYGON`)   | Spatial joins | Specialized |
| **LSM-Tree** | Write-heavy (Cassandra, Rocks, HBase) | Crazy write throughput | Read amplification, compaction |
| **Inverted index** | Search engines (Elastic, Lucene) | Token → docs | Not transactional |

### LSM-Tree vs B-Tree (commonly asked)

```
B-Tree:                            LSM-Tree:
- Update in place                  - Append-only memtable → flush to SSTable
- Read: 1-2 page reads             - Read: check memtable + N SSTables (bloom filters)
- Write: 1 random write            - Write: 1 sequential write (super fast)
- Read-optimized                   - Write-optimized
- Compaction: none                 - Compaction: background merge of SSTables
```

> **Rule of thumb:** Write-heavy + scale-out → LSM (Cassandra, DynamoDB). Read-heavy + transactions → B-Tree (Postgres, MySQL).

### When to use specialized indexes — examples

```sql
-- Postgres GIN for full-text + JSONB
CREATE INDEX idx_products_search ON products USING GIN (to_tsvector('english', name));
CREATE INDEX idx_products_attrs  ON products USING GIN (attributes jsonb_path_ops);

-- Postgres BRIN for huge time-series (1000× smaller than B-Tree)
CREATE INDEX idx_events_ts ON events USING BRIN (created_at);

-- PostGIS R-Tree (GiST) for geo
CREATE INDEX idx_stores_geo ON stores USING GIST (location);
```

---

## 6. Clustered vs Non-Clustered, Covering, Composite (8 min)

### Interviewer
> "What's the difference between a clustered and a non-clustered index? And what's a covering index?"

### Clustered Index
The **table itself is the B+Tree**, ordered by the clustered key. There can be **only one** per table.

- **InnoDB**: PK = clustered index. If no PK, it picks first unique non-null, else hidden rowid.
- **SQL Server**: explicit `CREATE CLUSTERED INDEX`.
- **Postgres**: ❌ no true clustered index — heap is unordered (`CLUSTER` command is one-shot, not maintained).

```
Clustered (InnoDB) PK on order_id
        Internal pages route by order_id
                  │
                  ▼
   Leaf pages = actual rows in PK order:
   [order_id=1, full row]  [order_id=2, full row] …
```

### Non-Clustered (Secondary) Index
A separate B+Tree where leaves store **(indexed_key → row pointer)**.

- **InnoDB secondary index leaf** stores the **PK value**, not a heap pointer.
  - ⇒ Lookup = traverse secondary index to PK, then traverse clustered index to row.
  - ⇒ This is why **fat PKs (UUIDs) bloat every secondary index**.
- **Postgres** stores `ctid` (heap tuple id). HOT updates and `VACUUM` complicate this.

```
Secondary index on email (InnoDB):
   email='sunil@x.com' → PK=42
                          │
                          ▼
   Clustered index lookup PK=42 → full row
```

### Covering Index (the magic trick)
If **all columns the query needs are in the index**, the DB returns the result **without touching the heap**. Called an "index-only scan".

```sql
SELECT user_id, status FROM orders WHERE user_id = 42 AND status = 'PAID';

-- Without covering: index on (user_id) → heap lookup for status
-- With covering:    index on (user_id, status)  ← satisfies the query alone
CREATE INDEX idx_orders_user_status ON orders (user_id, status);

-- Postgres: include non-key columns
CREATE INDEX idx_orders_user_inc ON orders (user_id) INCLUDE (status, total);
```

### Composite (multi-column) Index — the leftmost prefix rule

```sql
CREATE INDEX idx ON orders (user_id, status, created_at);
```

| Query                                                         | Uses index?    |
|---------------------------------------------------------------|----------------|
| `WHERE user_id = 1`                                           | ✅ full        |
| `WHERE user_id = 1 AND status = 'PAID'`                       | ✅ full        |
| `WHERE user_id = 1 AND status = 'PAID' AND created_at > ...`  | ✅ full        |
| `WHERE status = 'PAID'`                                       | ❌ skips prefix|
| `WHERE user_id = 1 AND created_at > ...`                      | ⚠️ partial (user_id only, status gap) |
| `ORDER BY user_id, status`                                    | ✅ uses ordering|

> **Order columns by:** (1) equality predicates first, (2) most selective next, (3) range/sort last.

### Partial / Filtered Index
Index only the rows you care about → much smaller, faster.

```sql
CREATE INDEX idx_open_orders ON orders (user_id)
    WHERE status IN ('PENDING','PAID');   -- 5% of rows
```

### Functional / Expression Index

```sql
CREATE INDEX idx_lower_email ON users (LOWER(email));
-- now WHERE LOWER(email)='x' uses the index
```

### Unique Index
Both index AND constraint. Used by the optimizer to know **at most 1 row matches**, enabling massive plan optimizations.

---

## 7. Query planner, EXPLAIN, cardinality, selectivity (5 min)

### Interviewer
> "You created an index but the query is still slow. What do you do?"

### Step 1 — Always start with `EXPLAIN ANALYZE`

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orders WHERE user_id = 42 AND status='PAID';
```

Look for:
- **Seq Scan** vs **Index Scan** vs **Index Only Scan** vs **Bitmap Heap Scan**.
- **Estimated rows vs Actual rows** — if off by 10×+, your statistics are stale → `ANALYZE`.
- **Buffers: shared hit / read** — measures actual page I/O.
- **Filter: …** removed rows after the index → consider extending the index.

### Why the optimizer might ignore your index

1. **Low selectivity** — Predicate matches >10% of table → seq scan is cheaper.
2. **Stale statistics** — Run `ANALYZE` (Postgres) / `ANALYZE TABLE` (MySQL).
3. **Implicit type cast** — `WHERE user_id = '42'` (string vs int) disables the index.
4. **Function on column** — `WHERE LOWER(email) = 'x'` without functional index.
5. **`OR` across columns** — Often defeats single-column indexes; use `UNION` or composite.
6. **Leading wildcard** — `LIKE '%abc'`.
7. **Skewed data** — Statistics histogram doesn't capture skew → wrong row estimate.

### Selectivity & Cardinality intuition

```
Selectivity = matching rows / total rows
- High selectivity (1 / 1,000,000)  → index is great
- Low selectivity (500K / 1M = 50%) → seq scan wins
```

> Don't index `gender` or `is_active` on a 100M-row OLTP table. Use a **partial index** instead.

### Bitmap Index Scan (Postgres)
When two indexes each have moderate selectivity, Postgres can build in-memory bitmaps from each, AND/OR them, then fetch heap pages **in physical order** (cheaper random I/O).

---

## 8. Designing indexes for an e-commerce schema (8 min)

### Interviewer
> "Here's a schema. What indexes would you create?"

```sql
CREATE TABLE orders (
  order_id      BIGSERIAL PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  status        VARCHAR(16)  NOT NULL,
  total_cents   BIGINT       NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL,
  updated_at    TIMESTAMPTZ  NOT NULL,
  shipping_zip  VARCHAR(10),
  payment_id    BIGINT
);
```

### Step 1 — Enumerate hot queries (always ask the interviewer!)

| # | Query | Frequency |
|---|-------|-----------|
| Q1 | `WHERE order_id = ?` (PK lookup) | Very high |
| Q2 | `WHERE user_id = ? ORDER BY created_at DESC LIMIT 20` (user's recent orders) | High |
| Q3 | `WHERE user_id = ? AND status IN ('PAID','SHIPPED')` (active orders) | High |
| Q4 | `WHERE created_at BETWEEN ? AND ?` (analytics by day) | Medium |
| Q5 | `WHERE payment_id = ?` (reconciliation) | Low |
| Q6 | `WHERE shipping_zip = ? AND status='PENDING'` (warehouse picking) | Medium |

### Step 2 — Index recommendations

```sql
-- Q1 covered by PK (clustered).

-- Q2 + Q3 → composite, leftmost = user_id, range/sort col last
CREATE INDEX idx_orders_user_created
    ON orders (user_id, created_at DESC);

-- Q3 specifically → partial index on hot states (smaller, hotter in cache)
CREATE INDEX idx_orders_user_active
    ON orders (user_id)
    WHERE status IN ('PAID','SHIPPED','PENDING');

-- Q4 → range on created_at; if the table is huge and append-only, BRIN is great
CREATE INDEX idx_orders_created_brin
    ON orders USING BRIN (created_at);

-- Q5 → unique (payment_id is 1:1 with orders)
CREATE UNIQUE INDEX idx_orders_payment ON orders (payment_id) WHERE payment_id IS NOT NULL;

-- Q6 → composite + partial
CREATE INDEX idx_orders_zip_pending
    ON orders (shipping_zip)
    WHERE status = 'PENDING';
```

### Step 3 — Justify trade-offs aloud
- **5 indexes** on a write-hot table = each INSERT does 6 B+Tree inserts. Acceptable for our 80/20 R/W ratio.
- We **didn't** index `status` alone (low cardinality, ~5 values).
- We **didn't** index `total_cents` (no query needs it).
- For `payment_id` we used a **partial unique** index (NULLs excluded, smaller).
- For analytics on `created_at`, **BRIN** is 1000× smaller than B-Tree and works because rows are inserted in time order.

### Step 4 — Don't forget operational concerns
- **`CREATE INDEX CONCURRENTLY`** in Postgres → no exclusive lock.
- **`pg_stat_user_indexes` / `sys.dm_db_index_usage_stats`** → drop unused indexes.
- **Reindex** after bulk loads to combat fragmentation.
- **Foreign keys**: always index the FK column on the child table (else cascading deletes do full scans).

---

## 9. Anti-patterns, write amplification, maintenance (3 min)

### Common anti-patterns
1. **Indexing every column** — "just in case". Massive write amp + bloated cache.
2. **UUID v4 primary keys** in InnoDB clustered tables → random insert pattern → **page splits everywhere**, fragmentation. ✅ Use **UUID v7** (time-ordered) or BIGSERIAL.
3. **Indexing very low-cardinality columns** alone (gender, boolean, status). Use **partial indexes**.
4. **Wide composite indexes** (>4 columns). Lookups are fine, but every write touches all of them.
5. **Redundant indexes** — `(a)` and `(a, b)` together. Drop `(a)`.
6. **Functions on indexed columns** in queries. Use **functional indexes** instead.
7. **Not running `ANALYZE`** after big data changes → bad plans.
8. **Indexing nullable cols heavily** without partial index.

### Write amplification math
- Table with **6 indexes**, single-row INSERT → ~7 B+Tree writes + WAL.
- p99 commit latency goes from 2 ms → 8 ms.
- Trade reads vs writes consciously.

### Maintenance
- **Bloat** (Postgres): MVCC leaves dead tuples → indexes grow. Use `VACUUM`, `pg_repack`, or `REINDEX CONCURRENTLY`.
- **Fragmentation** (InnoDB): `OPTIMIZE TABLE` rebuilds.
- **Hot spots** on monotonic keys: shard or use UUID v7.

---

## 10. Wrap-up, cheat sheet, follow-up Q&A (3 min)

### One-page cheat sheet

```
WHEN TO INDEX
  ✅ High-cardinality column used in WHERE, JOIN, ORDER BY, GROUP BY
  ✅ Foreign keys
  ✅ Frequent equality + range predicates → composite (eq cols first, range last)
  ✅ Make it covering (INCLUDE) when query is hot
  ✅ Use partial index for skewed predicates ("status='PENDING'")

WHEN NOT TO INDEX
  ❌ Low-cardinality columns alone (gender, boolean)
  ❌ Tiny tables (< few thousand rows — seq scan is cheaper)
  ❌ Columns rarely queried but frequently updated
  ❌ Heavily-written tables: keep index count low

INDEX TYPE BY USE CASE
  Equality + range + sort  → B+Tree (default)
  Equality only, in-memory → Hash (Postgres unlogged, MySQL Memory)
  Time-series, append-only → BRIN
  Full-text                → GIN (tsvector) / Elastic
  JSONB                    → GIN (jsonb_path_ops)
  Geo                      → GiST / R-Tree (PostGIS)
  Write-heavy at scale     → LSM (Cassandra, Rocks)

DEBUGGING SLOW QUERIES
  1. EXPLAIN ANALYZE → check Seq vs Index, est vs actual rows
  2. ANALYZE table → refresh stats
  3. Check for casts / functions on column
  4. Check selectivity — would seq scan actually be cheaper?
  5. Look at pg_stat_user_indexes / sys.dm_db_index_usage_stats
```

### Likely follow-up questions

**Q1. How does Postgres index-only scan really avoid the heap?**
> It uses the **visibility map**. If a heap page is "all-visible" (no dead/uncommitted rows), the planner trusts the index entry without checking MVCC visibility on the heap. Hence index-only scans need `VACUUM` to be effective.

**Q2. Why do UUID PKs hurt InnoDB?**
> InnoDB's clustered index orders rows by PK. Random UUIDs cause inserts at random leaf pages → page splits, fragmentation, lower buffer pool hit rate. Solutions: UUID v7 (time-ordered), ULID, or `BIGINT` surrogate PK with UUID as a unique secondary.

**Q3. Difference between `Index Scan` and `Bitmap Index Scan`?**
> Index Scan walks the B+Tree and immediately fetches each heap row (random I/O). Bitmap Index Scan collects all matching TIDs into a bitmap, sorts by physical page, then does **sequential heap I/O**. Better for medium-selectivity predicates and `OR` of two indexes.

**Q4. How does Cassandra do indexing differently?**
> Primary key = (partition key, clustering keys). Data is **physically sorted** by clustering key within a partition (essentially an LSM-Tree clustered index). Secondary indexes exist but are **per-node** and slow for high-cardinality — most teams use **denormalized tables** or **materialized views** instead.

**Q5. What about MongoDB?**
> B-Tree indexes (default `_id`), compound, multikey (arrays), text, 2dsphere, hashed (for sharding), wildcard, partial. Same leftmost-prefix rule for compounds. Sharding key choice is the most impactful "index" decision.

**Q6. How would you scale beyond a single-node index?**
> Partition / shard. Each shard has its own B+Tree. Global secondary indexes (DynamoDB GSI) or cross-shard indexing (Vitess, CockroachDB) introduce eventual consistency and 2PC trade-offs.

**Q7. When does the optimizer choose a scan over an index?**
> When **estimated cost(seq scan)** < cost(index scan). Driven by selectivity, `random_page_cost / seq_page_cost` ratio, table size, and statistics.

**Q8. How would you find unused indexes?**
> Postgres: `SELECT * FROM pg_stat_user_indexes WHERE idx_scan = 0;`
> MySQL: `sys.schema_unused_indexes`. Drop after a full business cycle (some are used for monthly jobs).

---

## TL;DR

> **Indexes turn O(N) scans into O(log N) lookups by maintaining a sorted auxiliary structure (usually a B+Tree). They speed up reads but slow down writes and consume RAM. The job of a senior engineer is to (a) understand the workload, (b) pick the smallest set of indexes covering the hot queries, (c) prefer composite + partial + covering indexes, (d) verify with `EXPLAIN ANALYZE`, and (e) drop indexes that aren't earning their keep.**

> **Default: B+Tree. Specialize when the query pattern demands it (BRIN for time-series, GIN for JSONB/FTS, GiST for geo, LSM for write-heavy at scale). Always measure — don't guess.**

