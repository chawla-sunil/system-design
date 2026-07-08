# Choosing the Right Database — System Design Cheatsheet

Quick reference for picking a database in a system design interview. The database rarely affects functional requirements, but it heavily impacts **scale, latency, and query patterns** (i.e. NFRs).

---

## 1. What Drives the Choice

1. **Data structure** — structured vs unstructured.
2. **Query pattern** — reads/writes shape, need for transactions, search, analytics.
3. **Scale** — data volume and growth rate.

---

## 2. Common Use Cases → Database Mapping

| Use Case | Recommended Store | Notes |
|---|---|---|
| **Caching** (query results, remote API responses) | **Redis** (or Memcached, etcd, Hazelcast) | Simple key-value; key = query/param, value = response. |
| **Files / images / videos** (Amazon, Netflix) | **Blob store: Amazon S3** + **CDN** | Not a DB — files aren't queried. CDN distributes content geographically. |
| **Text search** (product titles, movies, maps) | **Elasticsearch** or **Solr** (built on Apache Lucene) | Supports **fuzzy search** via edit distance. ⚠️ Not a source of truth — data can be lost. Keep a primary DB behind it. |
| **Metrics / time-based data** (Grafana, Prometheus) | **Time-series DB: InfluxDB, OpenTSDB** | Optimized for **append-only writes** and **time-range reads**. No random updates. |
| **Analytics / reporting on huge datasets** | **Data warehouse: Hadoop** (offline) | For offline reporting, not transactional. |

---

## 3. SQL vs NoSQL — Decision Flow

```
Is data structured?
├── YES ──► Do you need ACID / transactions?
│           ├── YES ──► Relational DB (MySQL, Postgres, Oracle, SQL Server)
│           └── NO  ──► RDBMS or NoSQL — either works
│
└── NO  ──► │ Wide/varied attributes & flexible queries?  ── YES ──► Document DB (MongoDB, Couchbase)
            │ Ever-increasing data + few query types? ── YES ──► Columnar DB (Cassandra, HBase)
```

### When to use what

- **Relational (RDBMS)** — Payments, inventory, orders. Needs atomicity, consistency, transactions.
- **Document DB** — E-commerce catalog: shirts have size/color, fridges have volume/star rating. Flexible schema + rich queries on varied attributes.
- **Columnar DB (Cassandra/HBase)** — Uber driver location pings; data grows super-linearly, but only a few query types (e.g. "get all locations for driver X"). Cassandra is lighter to deploy than HBase.

---

## 4. Real-World: Combining Databases (Amazon Example)

No serious system uses just one DB. Example combinations:

- **Active orders** → **MySQL** (ACID: prevent overselling).
- **Delivered/historical orders** → **Cassandra** (ever-growing archive, legal retention).
- **Reporting queries** (e.g., "users who bought sugar in last 5 days") → **MongoDB** stores a queryable subset of orders (user ID, order ID, items, date). Query MongoDB to find matching order IDs, then fetch full details from MySQL/Cassandra.

---

## 5. Quick Recall List

- Cache → **Redis**
- Files → **S3 + CDN**
- Search → **Elasticsearch / Solr**
- Metrics → **InfluxDB / OpenTSDB**
- Big data analytics → **Hadoop**
- ACID / structured → **MySQL / Postgres**
- Flexible attributes → **MongoDB**
- Massive, append-heavy, narrow queries → **Cassandra**

> This is an interview cheat sheet. In real systems, evaluate each option deeper before committing.
