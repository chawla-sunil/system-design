# URL Shortening Service — High-Level Design (HLD Interview)

> **Simulated Interview Duration:** 1–1.5 hours  
> **Candidate Experience Level:** 5–6 years  
> **Interviewer Prompt:** "Design a URL Shortening Service like TinyURL."

---

## Table of Contents

1. [Step 1 — Clarify Requirements (5–7 min)](#step-1--clarify-requirements)
2. [Step 2 — Back-of-the-Envelope Estimation (5–7 min)](#step-2--back-of-the-envelope-estimation)
3. [Step 3 — High-Level API Design (5–7 min)](#step-3--high-level-api-design)
4. [Step 4 — Data Model & Storage (7–10 min)](#step-4--data-model--storage)
5. [Step 5 — Core Algorithm — Short URL Generation (10–12 min)](#step-5--core-algorithm--short-url-generation)
6. [Step 6 — High-Level Architecture (10–12 min)](#step-6--high-level-architecture)
7. [Step 7 — Deep Dive — Scaling & Reliability (10–12 min)](#step-7--deep-dive--scaling--reliability)
8. [Step 8 — Cache Design (5–7 min)](#step-8--cache-design)
9. [Step 9 — Analytics & Monitoring (5 min)](#step-9--analytics--monitoring)
10. [Step 10 — Security Considerations (3–5 min)](#step-10--security-considerations)
11. [Step 11 — Summary & Trade-offs (3–5 min)](#step-11--summary--trade-offs)

---

## Step 1 — Clarify Requirements

> **"Before I start designing, I'd like to clarify a few things about the scope and constraints."**

### Functional Requirements

| # | Requirement | Notes |
|---|-------------|-------|
| FR-1 | **Shorten URL** — Given a long URL, generate a unique, short URL. | Core feature |
| FR-2 | **Redirect** — Given a short URL, redirect the user to the original long URL. | Core feature |
| FR-3 | **Custom Alias (optional)** — Users can optionally pick a custom short link. | Nice to have |
| FR-4 | **Expiration / TTL** — Short URLs can have an expiration time. | Default: never expire, or configurable (e.g., 5 years) |
| FR-5 | **Analytics (optional)** — Track click count, geographic data, referrer, etc. | Bonus feature |

### Non-Functional Requirements

| # | Requirement | Target |
|---|-------------|--------|
| NFR-1 | **High Availability** | 99.99% uptime |
| NFR-2 | **Low Latency** | Redirect < 100 ms (p99) |
| NFR-3 | **Scalability** | Handle 100M+ URLs, billions of redirects |
| NFR-4 | **Durability** | Once created, a URL mapping must never be lost |
| NFR-5 | **Read-Heavy** | Read:Write ratio ≈ 100:1 |
| NFR-6 | **Non-guessable** | Short URLs should not be easily predictable |

### Out of Scope (Clarify with Interviewer)

- User authentication & dashboards (unless asked)
- Link editing / deletion (mention but deprioritize)
- Spam / phishing detection (mention for bonus points)

---

## Step 2 — Back-of-the-Envelope Estimation

> **"Let me do some quick math to understand the scale."**

### Traffic Estimates

| Metric | Value |
|--------|-------|
| New URLs created per day | **1 million / day** |
| URLs created per second | ~**12 URLs/sec** (1M / 86400) |
| Read/redirect per day (100:1 ratio) | **100 million / day** |
| Reads per second | ~**1,150 reads/sec** |
| Peak reads per second (3x) | ~**3,500 reads/sec** |

### Storage Estimates

| Metric | Value |
|--------|-------|
| Duration to support | **5 years** |
| Total URLs in 5 years | 1M × 365 × 5 = **~1.825 billion URLs** |
| Average size per record | ~500 bytes (short URL + long URL + metadata) |
| Total storage | 1.825B × 500B = **~912 GB ≈ ~1 TB** |

### Short URL Length Calculation

We need to encode **1.825 billion** unique URLs.

- Using **Base62** (a-z, A-Z, 0-9) → 62 characters
- 62^6 = **56.8 billion** combinations
- 62^7 = **3.5 trillion** combinations

✅ **A 7-character Base62 string is more than sufficient** for our scale and provides room for decades of growth.

### Bandwidth

| Metric | Value |
|--------|-------|
| Write bandwidth | 12 req/s × 500B = **6 KB/s** (negligible) |
| Read bandwidth | 1,150 req/s × 500B = **575 KB/s** (easily manageable) |

### Memory (Cache — 80/20 Rule)

| Metric | Value |
|--------|-------|
| Cache 20% of daily read requests | 100M × 0.2 = **20 million URLs/day** |
| Memory needed | 20M × 500B = **~10 GB/day** |

✅ Fits in a single Redis / Memcached instance (with replication for HA).

---

## Step 3 — High-Level API Design

> **"Let me define the core APIs the system will expose."**

### REST API

#### 1. Create Short URL

```
POST /api/v1/urls
Content-Type: application/json

Request Body:
{
  "longUrl": "https://www.example.com/very/long/path?query=param",
  "customAlias": "my-link",        // optional
  "expiresAt": "2027-03-28T00:00:00Z"  // optional
}

Response: 201 Created
{
  "shortUrl": "https://tinyurl.com/Ab3xK9p",
  "longUrl": "https://www.example.com/very/long/path?query=param",
  "expiresAt": "2027-03-28T00:00:00Z",
  "createdAt": "2026-03-28T10:30:00Z"
}
```

#### 2. Redirect (Short URL → Long URL)

```
GET /{shortUrlKey}
e.g., GET /Ab3xK9p

Response: 301 Moved Permanently  (or 302 Found)
Location: https://www.example.com/very/long/path?query=param
```

> **301 vs 302 — Important Discussion Point:**
>
> | Code | Type | Browser Caches? | Analytics Impact |
> |------|------|-----------------|-----------------|
> | **301** | Permanent Redirect | ✅ Yes | Fewer requests hit our server → lose analytics accuracy |
> | **302** | Temporary Redirect | ❌ No | Every click hits our server → accurate analytics |
>
> **My recommendation:** Use **302** if we need analytics. Use **301** if we want to reduce server load and don't need click tracking.

#### 3. Get URL Info (Optional)

```
GET /api/v1/urls/{shortUrlKey}

Response: 200 OK
{
  "shortUrl": "https://tinyurl.com/Ab3xK9p",
  "longUrl": "https://www.example.com/...",
  "createdAt": "...",
  "expiresAt": "...",
  "clickCount": 45023
}
```

---

## Step 4 — Data Model & Storage

> **"Now let's define the data model and choose the right database."**

### Core Table: `url_mappings`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Auto-increment primary key |
| `short_url_key` | VARCHAR(7) | The 7-char short URL key (unique, indexed) |
| `long_url` | VARCHAR(2048) | The original long URL |
| `custom_alias` | VARCHAR(32) | User-chosen alias (nullable) |
| `created_at` | TIMESTAMP | Creation timestamp |
| `expires_at` | TIMESTAMP | Expiration timestamp (nullable) |
| `user_id` | BIGINT | Creator user ID (nullable, for registered users) |

**Indexes:**
- **Unique index** on `short_url_key` — for fast lookup during redirect
- **Index** on `long_url` — to check for duplicate URLs (optional dedup)
- **Index** on `expires_at` — for cleanup jobs

### Analytics Table (Optional): `click_events`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Event ID |
| `short_url_key` | VARCHAR(7) | FK to url_mappings |
| `clicked_at` | TIMESTAMP | When the click happened |
| `ip_address` | VARCHAR(45) | Client IP |
| `user_agent` | VARCHAR(512) | Browser info |
| `referrer` | VARCHAR(2048) | Where the user came from |
| `country` | VARCHAR(3) | Geo from IP |

### Database Choice

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **MySQL / PostgreSQL** | ACID, mature, supports indexes well | Scaling writes requires sharding | ✅ **Good default choice** |
| **DynamoDB / Cassandra** | Horizontally scalable, great for key-value lookups | Eventual consistency, limited query flexibility | ✅ Great for redirect reads |
| **MongoDB** | Flexible schema | Less ideal for this structured data | ⚠️ Okay, not optimal |

**My recommendation:**
- **Primary store:** **MySQL / PostgreSQL** with horizontal sharding (by `short_url_key` hash).
- **Or:** **DynamoDB** if we're building on AWS — natural fit for key-value lookup pattern.
- **Analytics:** Write click events to **Kafka → ClickHouse / Apache Druid** (columnar DB optimized for analytics).

---

## Step 5 — Core Algorithm — Short URL Generation

> **"This is the heart of the system. Let me walk through multiple approaches and their trade-offs."**

### Approach 1: Hash the Long URL (MD5 / SHA-256) + Base62

**How it works:**
1. Take the long URL, compute `MD5(longUrl)` → 128-bit hash → hex string
2. Take the first 7 characters of the Base62-encoded hash
3. Check the DB for collision; if collision, rehash with a salt or append a counter

```
longUrl = "https://example.com/long/path"
hash = MD5(longUrl) = "d41d8cd98f00b204e9800998ecf8427e"
shortKey = base62Encode(hash[:43bits]) = "Ab3xK9p"
```

| Pros | Cons |
|------|------|
| Same long URL → same short URL (dedup) | Collision possible → need collision resolution |
| Deterministic | Collision check requires DB read → adds latency |
| No coordination needed | CAS (check-and-set) in distributed environment is tricky |

### Approach 2: Counter-Based (Auto-Increment ID + Base62)

**How it works:**
1. Insert the long URL into DB → get auto-increment ID (e.g., `1000000007`)
2. Convert ID to Base62 → `"15ftgG"`

```
id = 1000000007
shortKey = base62Encode(1000000007) = "15ftgG"
```

| Pros | Cons |
|------|------|
| **Zero collisions** — IDs are unique | Sequential → predictable / guessable |
| Simple to implement | Single point of failure (single DB counter) |
| Efficient | Requires coordination in distributed setup |

**Mitigation for predictability:** Shuffle the bits of the ID before Base62 encoding (bijective scramble), or combine with a random salt.

### Approach 3: Pre-Generated Key Service (KGS) ⭐ **Recommended**

**How it works:**
1. A separate **Key Generation Service (KGS)** pre-generates millions of unique 7-char Base62 keys.
2. Stores them in a `keys` table with two pools: `unused_keys` and `used_keys`.
3. When a URL shortening request comes in, the app server **fetches a key from KGS** and assigns it.

```
                        ┌──────────────┐
   Shortening Request → │  App Server  │
                        └──────┬───────┘
                               │ "Give me a key"
                               ▼
                        ┌──────────────┐
                        │     KGS      │
                        │ (Key Gen Svc)│
                        └──────┬───────┘
                               │ returns "Ab3xK9p"
                               ▼
                        ┌──────────────┐
                        │   Database   │
                        │  (key pool)  │
                        └──────────────┘
```

**KGS Implementation Details:**

- Pre-generate keys using all permutations of Base62 (7 chars).
- Store in DB: `key_pool` table with columns: `key VARCHAR(7)`, `is_used BOOLEAN`.
- KGS loads a **batch** of keys (e.g., 1,000) into memory for each app server.
- When the batch runs out, fetch the next batch.
- Use **row-level locking** or **atomic batch claim** to avoid two servers getting the same key.

| Pros | Cons |
|------|------|
| **No collisions** | Extra service to maintain |
| **No runtime computation** | Pre-generation takes storage (~6 GB for 1B keys) |
| Fast — just a lookup | Need to handle KGS failures gracefully |
| Keys are random → non-guessable | Wasted keys if server crashes mid-batch |

**Why I recommend this:** It decouples key generation from the main request path, guarantees uniqueness, and keys are non-sequential (non-guessable). Wasted keys from crashes are negligible at our scale.

### Approach 4: Snowflake-like Distributed ID Generator

Use a distributed unique ID generator (Twitter Snowflake) that encodes:
```
| 41 bits: timestamp | 10 bits: machine ID | 12 bits: sequence |
```
Then Base62-encode the generated 64-bit ID.

| Pros | Cons |
|------|------|
| Distributed, no coordination | Requires clock synchronization |
| Naturally unique | Slightly longer keys |
| Sortable by time | More complex to implement |

### ✅ Final Decision

> **I'll go with Approach 3 (KGS)** as the primary strategy for production. It's battle-tested, avoids collisions, and keeps the main service path simple and fast. For a simpler MVP, Approach 2 (counter + Base62) with bit-scrambling is also acceptable.

### Custom Alias Handling

If the user provides a custom alias:
1. Check if the alias is already taken → return error if yes.
2. Validate: length ≤ 16 chars, alphanumeric + hyphens only.
3. Store directly as the `short_url_key` — bypass KGS.

---

## Step 6 — High-Level Architecture

> **"Let me draw the architecture and walk through each component."**

```
                                    ┌─────────────────────────────────┐
                                    │          DNS (Route 53)         │
                                    └───────────────┬─────────────────┘
                                                    │
                                                    ▼
                                    ┌─────────────────────────────────┐
                                    │      CDN (CloudFront/Akamai)    │
                                    │  (cache 301/302 redirects)      │
                                    └───────────────┬─────────────────┘
                                                    │
                                                    ▼
                                    ┌─────────────────────────────────┐
                                    │    Load Balancer (ALB / Nginx)  │
                                    │  (Round Robin / Least Conn)     │
                                    └───────────────┬─────────────────┘
                                                    │
                              ┌─────────────────────┼─────────────────────┐
                              │                     │                     │
                              ▼                     ▼                     ▼
                   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
                   │   App Server 1   │  │   App Server 2   │  │   App Server N   │
                   │  (Stateless)     │  │  (Stateless)     │  │  (Stateless)     │
                   └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
                            │                     │                     │
                            └─────────────────────┼───────────���─────────┘
                                                  │
                          ┌───────────────────────┼───────────────────────┐
                          │                       │                       │
                          ▼                       ▼                       ▼
                 ┌─────────────────┐    ┌─────────────────┐     ┌─────────────────┐
                 │   Cache Layer   │    │   Database (DB)  │     │ Key Generation  │
                 │  (Redis Cluster)│    │  (MySQL/Postgres │     │  Service (KGS)  │
                 │                 │    │   + Sharding)    │     │                 │
                 └─────────────────┘    └─────────────────┘     └─────────────────┘
                                                  │
                                                  ▼
                                        ┌─────────────────┐
                                        │   Kafka / SQS   │
                                        │ (Click Events)  │
                                        └────────┬────────┘
                                                 │
                                                 ▼
                                        ┌─────────────────┐
                                        │ Analytics Store  │
                                        │ (ClickHouse /   │
                                        │  Druid / S3)    │
                                        └─────────────────┘
```

### Request Flow — Create Short URL

```
1. Client sends POST /api/v1/urls { longUrl: "..." }
2. Load Balancer routes to an App Server.
3. App Server:
   a. Validates the long URL (format, reachability check — optional).
   b. [Optional] Checks if the long URL already exists in DB → return existing short URL.
   c. Requests a pre-generated key from KGS (or local batch cache).
   d. Inserts { short_url_key, long_url, created_at, expires_at } into the Database.
   e. Writes through to Cache (Redis) for fast subsequent reads.
   f. Returns the short URL to the client.
```

### Request Flow — Redirect (Read Path) ⭐ Hot Path

```
1. Client hits GET /Ab3xK9p
2. CDN checks if it has a cached redirect → if yes, return 301/302 immediately.
3. If CDN miss → Load Balancer routes to an App Server.
4. App Server checks Redis Cache for key "Ab3xK9p":
   a. Cache HIT → Get long URL → return 302 redirect.
   b. Cache MISS → Query Database → Get long URL → populate cache → return 302.
5. [Async] Publish click event to Kafka for analytics processing.
6. Client browser follows the redirect to the long URL.
```

### Component Responsibilities

| Component | Responsibility |
|-----------|---------------|
| **DNS** | Resolves `tinyurl.com` to Load Balancer IPs |
| **CDN** | Caches popular redirects at edge locations worldwide |
| **Load Balancer** | Distributes traffic across stateless app servers |
| **App Servers** | Stateless services handling create/redirect logic |
| **Redis Cache** | In-memory cache for hot URL mappings (LRU eviction) |
| **Database** | Persistent store for all URL mappings |
| **KGS** | Pre-generates unique short URL keys |
| **Kafka** | Async event stream for click analytics |
| **Analytics Store** | ClickHouse/Druid for click analytics queries |

---

## Step 7 — Deep Dive — Scaling & Reliability

> **"Let me address how we scale each layer and handle failures."**

### 7.1 Database Sharding

With ~1.8 billion rows in 5 years, a single DB won't suffice.

**Sharding Strategy: Hash-Based on `short_url_key`**

```
shard_id = hash(short_url_key) % number_of_shards
```

| Aspect | Detail |
|--------|--------|
| Shard key | `short_url_key` (7-char string) |
| # of shards | Start with 4–8, grow to 16–32 |
| Why this key? | Uniform distribution (Base62 is random-ish), and the redirect lookup is always by this key |

**Range-based sharding** (by first character) is also possible but can lead to hot shards if certain prefixes are more common.

**Replication:**
- Each shard has **1 primary + 2 replicas** (async replication).
- Reads go to replicas (eventual consistency is fine for redirects — a few ms lag is acceptable).
- Writes go to the primary.

### 7.2 App Server Scaling

- **Stateless** → horizontally scalable.
- Use **auto-scaling groups** (AWS ASG / K8s HPA) based on CPU / request count.
- Each app server caches a batch of pre-generated keys locally (e.g., 1,000 keys in memory).

### 7.3 Cache Scaling (Redis)

```
┌─────────────────────────────────────────────────────┐
│                Redis Cluster                        │
│                                                     │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐            │
│  │ Shard 1 │  │ Shard 2 │  │ Shard 3 │  ...       │
│  │ Primary │  │ Primary │  │ Primary │            │
│  │ + Replica│  │ + Replica│  │ + Replica│            │
│  └─────────┘  └─────────┘  └─────────┘            │
└─────────────────────────────────────────────────────┘
```

- **Redis Cluster** with consistent hashing across shards.
- **LRU eviction** policy — evict least recently used entries when memory is full.
- **TTL on cache entries** matching the URL expiration.
- **Write-through** on URL creation to warm the cache.
- **Cache-aside** pattern on reads: check cache → miss → query DB → write to cache.

### 7.4 Key Generation Service (KGS) Reliability

**Problem:** KGS is a potential single point of failure.

**Solution:**
1. **KGS Redundancy:** Deploy 2+ KGS instances.
2. **Key Range Partitioning:** Each KGS instance owns a range of keys (e.g., KGS-1 owns keys starting with a–m, KGS-2 owns n–z).
3. **Batch Pre-fetching:** App servers pre-fetch batches of 1,000 keys. If KGS goes down, they can still serve requests until the batch runs out.
4. **Standby KGS:** A standby instance can take over if the primary fails.

**What about wasted keys?**
- If an app server crashes with 500 unused keys in memory, those keys are "lost."
- With 3.5 trillion possible 7-char Base62 keys, losing a few thousand keys per crash is negligible.

### 7.5 Handling Hot URLs (Thundering Herd)

Some URLs go viral (e.g., shared on Twitter). A single cache key gets millions of reads.

**Mitigations:**
1. **CDN caching** — The CDN absorbs most of the traffic at the edge.
2. **Redis read replicas** — Spread reads across replicas.
3. **Local in-process cache** (Caffeine / Guava) on app servers — L1 cache with short TTL (30s).
4. **Rate limiting** — Protect against abuse.

### 7.6 Data Cleanup (Expired URLs)

- A **background CRON job** runs periodically (e.g., every hour).
- Queries: `SELECT short_url_key FROM url_mappings WHERE expires_at < NOW() LIMIT 10000`
- Deletes expired entries from DB and Cache.
- Returns expired keys back to the KGS key pool for reuse (optional).

### 7.7 Availability & Fault Tolerance

| Failure | Mitigation |
|---------|------------|
| App server crash | LB health checks route to healthy servers; auto-scaling replaces instances |
| DB primary failure | Automated failover to replica (RDS Multi-AZ / Patroni for PostgreSQL) |
| Redis node failure | Redis Sentinel / Cluster auto-failover to replica |
| KGS failure | Standby KGS + app servers have local key buffer |
| Full datacenter outage | Multi-region active-active or active-passive deployment |

---

## Step 8 — Cache Design

> **"Let me go deeper into the caching strategy since this is a read-heavy system."**

### Multi-Level Cache Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   CDN Edge   │ ──→ │ App Server   │ ──→ │    Redis     │ ──→ │   Database   │
│   Cache      │     │ Local Cache  │     │   (L2)       │     │  (Source of  │
│   (L0)       │     │ (L1 - Guava) │     │              │     │   Truth)     │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
   TTL: 5 min          TTL: 30 sec         TTL: 24 hours        Permanent
   Hit rate: ~60%      Hit rate: ~15%      Hit rate: ~20%       ~5% reach DB
```

### Cache Strategy Summary

| Operation | Strategy |
|-----------|----------|
| **Redirect (Read)** | Cache-aside: Check L1 → L2 → DB → backfill cache |
| **Create URL** | Write-through: Write to DB and cache simultaneously |
| **Expire URL** | TTL-based eviction + cleanup job invalidates cache |
| **Eviction** | LRU (Least Recently Used) in all cache layers |

### Why Not Write-Behind (Write-Back)?

Write-behind would improve write latency but risks data loss if cache fails before persisting to DB. For a URL shortener, **durability of the mapping is critical** — we can't afford to lose it. Hence, write-through is safer.

---

## Step 9 — Analytics & Monitoring

> **"If the interviewer asks about analytics, here's how I'd design it."**

### Click Tracking Architecture

```
App Server  ──→  Kafka Topic: "url-clicks"  ──→  Stream Processor  ──→  ClickHouse
                                                   (Flink / Spark)
```

**Click Event Schema:**
```json
{
  "shortUrlKey": "Ab3xK9p",
  "timestamp": "2026-03-28T10:30:00Z",
  "ip": "203.0.113.42",
  "userAgent": "Mozilla/5.0...",
  "referrer": "https://twitter.com/...",
  "country": "US",
  "city": "San Francisco"
}
```

**Why async via Kafka?**
- Redirect latency must be minimal — don't block on analytics writes.
- Kafka provides durability and buffering.
- Decouples the hot read path from analytics processing.

### Metrics Dashboard

| Metric | Tool |
|--------|------|
| Total clicks per URL | ClickHouse aggregate query |
| Clicks over time (hourly/daily) | Grafana + ClickHouse |
| Geographic distribution | MaxMind GeoIP + ClickHouse |
| Top referrers | ClickHouse aggregate |
| System health (latency, errors, throughput) | Prometheus + Grafana |
| Cache hit ratio | Redis INFO stats → Prometheus |

---

## Step 10 — Security Considerations

> **"A few security points I'd like to highlight."**

| Concern | Solution |
|---------|----------|
| **Malicious URLs (phishing)** | Integrate with Google Safe Browsing API to check long URLs before shortening |
| **Rate Limiting** | Limit URL creation to N per IP per minute (e.g., 10/min for anonymous, 100/min for registered) |
| **Spam / Abuse** | Require CAPTCHA for anonymous users; flag & review high-volume creators |
| **Enumeration Attack** | Short keys are random (KGS), not sequential — hard to enumerate |
| **SQL Injection** | Parameterized queries / ORM |
| **Open Redirect** | Display a preview page ("You are being redirected to...") for unknown/suspicious URLs |
| **HTTPS** | All traffic over HTTPS; HSTS header enabled |
| **DDoS Protection** | AWS Shield / Cloudflare; CDN absorbs volumetric attacks |

---

## Step 11 — Summary & Trade-offs

> **"Let me wrap up with a summary and the key trade-offs."**

### Architecture Summary

```
Client → DNS → CDN → Load Balancer → App Servers (stateless)
                                         │
                     ┌───────────────────┬┴────────────────────┐
                     │                   │                     │
                   Redis            DB (Sharded)            KGS
                  (Cache)         (MySQL/Postgres)     (Key Gen Service)
                                     │
                                   Kafka → ClickHouse (Analytics)
```

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Short URL generation | KGS (pre-generated keys) | No collisions, non-guessable, fast |
| Short URL length | 7 characters, Base62 | 3.5T combinations — sufficient for decades |
| Database | MySQL/PostgreSQL, sharded | ACID guarantees, mature, good tooling |
| Cache | Redis Cluster + Local Cache | Sub-ms reads, LRU eviction |
| Redirect code | 302 (Temporary) | Enables analytics; 301 if analytics not needed |
| Analytics | Kafka → ClickHouse (async) | Doesn't block redirect path |
| Sharding key | `short_url_key` | All lookups are by this key; uniform distribution |

### Trade-offs Discussed

| Trade-off | Chosen | Alternative |
|-----------|--------|-------------|
| Consistency vs Availability | AP (Available + Partition-tolerant) | CP would add latency |
| 301 vs 302 redirect | 302 for analytics | 301 for performance |
| SQL vs NoSQL | SQL (sharded) | DynamoDB is also viable |
| Write-through vs Write-behind cache | Write-through | Write-behind risks data loss |
| Pre-generated keys vs Hash-based | Pre-generated (KGS) | Hash has collision risk |

### What I'd Do in Phases

| Phase | Scope |
|-------|-------|
| **MVP (Week 1-2)** | Single server, single DB, counter-based ID, no cache |
| **V1 (Month 1)** | Add Redis cache, KGS, LB + 2 app servers |
| **V2 (Month 3)** | DB sharding, Redis cluster, CDN, analytics pipeline |
| **V3 (Month 6+)** | Multi-region deployment, advanced analytics, custom domains |

---

## Bonus: Questions the Interviewer Might Ask

### Q: "What if two users shorten the same long URL?"
**A:** Two options:
1. **Dedup:** Check if the long URL already exists → return the same short URL. Requires an index on `long_url`. Saves storage but adds a DB read on every write.
2. **No dedup:** Every request gets a unique short URL. Simpler, faster writes. Uses slightly more storage but is negligible at our scale.
> I'd go with **no dedup** for simplicity, unless it's a product requirement.

### Q: "How do you handle the transition from a single DB to sharded?"
**A:** 
1. Start with a single DB + read replicas.
2. When approaching capacity, implement **consistent hashing** on `short_url_key`.
3. Use a **dual-write migration:** Write to both old and new shards, backfill historical data, then switch reads.
4. Tools like Vitess (for MySQL) or Citus (for PostgreSQL) help with this.

### Q: "What about custom domains (e.g., amzn.to, bit.ly)?"
**A:** 
- Each user/org can register a custom domain.
- DNS CNAME from `amzn.to → tinyurl.com`.
- Store a `domain_id` in the `url_mappings` table.
- On redirect, match both the domain and the short key.

### Q: "What's the latency of a redirect?"
**A:** 
- CDN hit: **< 10 ms** (served at edge)
- Redis hit: **< 20 ms** (LB → app server → Redis → response)
- DB hit: **< 100 ms** (LB → app server → DB → Redis backfill → response)
- p99 target: **< 100 ms** (with cache, vast majority are < 20 ms)

### Q: "How do you monitor the system?"
**A:**
- **Golden signals:** Latency, Traffic, Errors, Saturation (Google SRE)
- **Alerting:** PagerDuty alerts on p99 latency > 200ms, error rate > 1%, cache hit rate < 80%
- **Dashboards:** Grafana showing redirect QPS, create QPS, cache hit ratio, DB query latency, KGS key pool size

---

## Diagram — Complete System Architecture

```
                               ┌─────────┐
                               │  Users  │
                               └────┬────┘
                                    │
                                    ▼
                            ┌───────────────┐
                            │     DNS       │
                            │  (Route 53)   │
                            └───────┬───────┘
                                    │
                                    ▼
                            ┌───────────────┐
                            │     CDN       │
                            │ (CloudFront)  │
                            └───────┬───────┘
                                    │
                                    ▼
                     ┌──────────────────────────────┐
                     │     Load Balancer (ALB)       │
                     └──────────────┬───────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
                    ▼               ▼               ▼
             ┌──────────┐   ┌──────────┐   ┌──────────┐
             │  App Svc  │   │  App Svc  │   │  App Svc  │
             │  (Java)   │   │  (Java)   │   │  (Java)   │
             └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
                   │               │               │
                   └───────────────┼───────────────┘
                                   │
               ┌───────────────────┼────────────────────┐
               │                   │                    │
               ▼                   ▼                    ▼
        ┌─────────────┐    ┌──────────────┐    ┌──────────────┐
        │    Redis     │    │   Database    │    │     KGS      │
        │   Cluster    │    │   (Sharded)   │    │  (Key Gen)   │
        │  ┌───┬───┐  │    │  ┌───┬───┐   │    │              │
        │  │S1 │S2 │  │    │  │S1 │S2 │   │    │  ┌────────┐  │
        │  │+R │+R │  │    │  │+R │+R │   │    │  │Key Pool│  │
        │  └───┴───┘  │    │  │+R │+R │   │    │  └────────┘  │
        └─────────────┘    │  └───┴───┘   │    └──────────────┘
                           └──────┬───────┘
                                  │
                                  ▼
                          ┌──────────────┐
                          │    Kafka     │
                          │ Click Events │
                          └──────┬───────┘
                                 │
                    ┌────────────┼────────────┐
                    │            │            │
                    ▼            ▼            ▼
             ┌──────────┐ ┌──────────┐ ┌──────────┐
             │ Consumer │ │ Consumer │ │ Consumer │
             │  Group   │ │  Group   │ │  Group   │
             └─────┬────┘ └─────┬────┘ └─────┬────┘
                   └────────────┼────────────┘
                                │
                                ▼
                        ┌──────────────┐
                        │  ClickHouse  │
                        │ (Analytics)  │
                        └──────────────┘
```

---

> **Final Note:** In an actual interview, you don't need to cover ALL of this. Prioritize:
> 1. Requirements clarification
> 2. Estimation
> 3. API design
> 4. Core algorithm (short URL generation) — **spend the most time here**
> 5. High-level architecture
> 6. Scaling & trade-offs
>
> Let the interviewer guide the deep dives. The goal is to demonstrate structured thinking, trade-off analysis, and the ability to design a production-grade system.

