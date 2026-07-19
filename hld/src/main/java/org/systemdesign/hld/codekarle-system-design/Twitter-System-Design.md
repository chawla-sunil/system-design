# 🐦 Twitter System Design — Interview Revision Notes

This folder is made from the summary of the following sources:
- [Github Design Images](https://github.com/codekarle/system-design/blob/master/system-design-prep-material/architecture-diagrams/Twitter%20System%20Design.png)
- [Blog](https://www.codekarle.com/system-design/Twitter-system-design.html)
- [YouTube](https://www.youtube.com/watch?v=EkudBdvbDhs&list=PLhgw50vUymycJPN6ZbGTpVKAJ0cL4OEH3&index=5)

> **Goal:** Read heavy social platform (100x reads vs writes). Design must minimize read latency using **pre-computation + caching**.

---

## 1. Requirements

### ✅ Functional
| # | Feature | Notes |
|---|---------|-------|
| 1 | **Tweet** | 140 chars, may contain text, image, video, URL |
| 2 | **Re-Tweet** | Similar to FB "share" |
| 3 | **Follow user** | **Directed / unidirectional** graph (A→B ≠ B→A) |
| 4 | **Search** | Text search over all tweets (trend analysis) |

### ⚡ Non-Functional
- **Read heavy** → ~100x more reads than writes
- **Low latency** on both timeline render (<1s) and tweet post
- **High availability** — cannot go down
- **Some lag is OK** — a tweet can be visible ~20s later; but when shown, it must render fast (eventual consistency acceptable)

### 📊 Scale Numbers (approx.)
```
DAU              : 150 M
MAU              : 350 M
Total accounts   : 1.5 B
Tweets / day     : 500 M
Tweets / sec avg : ~5,700
Tweets / sec peak: ~12,000
Reads : Writes   : 100 : 1
```

---

## 2. 🔑 KEY IDEA — Classify Users into 5 Buckets

**This classification drives the whole architecture.** Different buckets are handled differently to save cost and RAM.

| Bucket | Definition | Strategy |
|--------|------------|----------|
| **Famous** | Millions of followers (celebs, politicians) | **Fan-out on READ** (do NOT push their tweets to millions of caches) |
| **Live** | Active users online *right now* | **Push via WebSocket** — no polling needed |
| **Active** | Logged in within last 3 days (Twitter uses 30d) | **Fan-out on WRITE** — pre-compute their timeline in Redis |
| **Passive** | Have account, not accessed in last 3 days | Timeline built **on-demand** at login |
| **Inactive** | Soft-deleted accounts | Do nothing |

> 💡 **Interview mnemonic:** "**F**amous **L**ives **A**re **P**erfectly **I**nactive" — F, L, A, P, I

---

## 3. High-Level Architecture

```
                                ┌───────────────┐
                                │   UI / App    │  (green = user touch-point)
                                └───────┬───────┘
                                        │
                                ┌───────▼───────┐
                                │  Load Balancer│  (black = LB / auth / reverse proxy)
                                └───────┬───────┘
      ┌────────────┬────────────┬───────┼──────────────┬──────────────┐
      │            │            │       │              │              │
┌─────▼────┐  ┌────▼────┐  ┌────▼───┐ ┌─▼────────┐ ┌───▼──────┐  ┌────▼────┐
│  User    │  │ Graph   │  │Tweet    │ │Timeline  │ │ Search   │  │Analytics│
│  Service │  │ Service │  │Ingest.  │ │ Service  │ │ Service  │  │ Service │
└─────┬────┘  └────┬────┘  └────┬───┘ └────┬─────┘ └────┬─────┘  └────┬────┘
      │            │            │           │            │              │
   ┌──▼──┐      ┌──▼──┐      ┌──▼──┐    ┌──▼──┐    ┌────▼─────┐   ┌────▼───┐
   │MySQL│      │MySQL│      │Kafka│    │Redis│    │Elastic   │   │ Kafka  │
   │ +   │      │ +   │      │  +  │    │(TL) │    │Search    │   │  +     │
   │Redis│      │Redis│      │Cass.│    │     │    │  +Redis  │   │ Spark  │
   └─────┘      └─────┘      └─────┘    └─────┘    └──────────┘   └────┬───┘
                         Cass. => Cassandra                            │
                                                                   ┌────▼───┐
                                                                   │ Hadoop │
                                                                   └────────┘
```

**Legend:** 🟢 UI · ⬛ LB · 🟦 Our services · 🟥 DBs / Open-source clusters

---

## 4. Component Deep Dives

### 4.1 User Service (Onboarding, Profile, Login)

```
     UI → LB → User Service ──► Redis  (userID → user object)
                          └──► MySQL cluster (source of truth)
```

- **DB:** MySQL — data is relational, bounded (~1.5B rows), rarely updated
- **Cache:** Redis (key = userID, value = user profile object)
- **APIs:** getByUserId, getByEmail, POST update, **bulk GET** (fetch 20–40 users in one call — saves network bandwidth when rendering followers list)
- **Flow:** Read → check Redis → miss → read replica → hydrate Redis → return

### 4.2 Graph Service (Follow Relationships)

- **Purpose:** Directed follow-graph (userID → followerID + timestamp)
- **DB:** Sharded MySQL cluster (billions of edges)
- **Cache:** Redis stores 2 mappings per user:
  - `userID → [list of people they follow]`
  - `userID → [list of their followers]`
- Rarely updated per-user → excellent caching candidate

### 4.3 Live User Tracking (WebSocket)

```
   Live User App  ◄──── WebSocket ────►  Live User Service
                                                 │
                     (user goes offline) ────────▼
                                              Kafka
                                                 │
                                                 ▼
                                          User Service cache
                                     (marks user: live → active)
```

- Keeps WebSocket open with all online users
- Enables **push notifications** (new tweet by someone you follow, mentions)
- On disconnect → emits Kafka event → user type flipped to *active*, `lastAccessTime` stored

### 4.4 Analytics Ingestion (Signal Capture)

Every UI interaction (dwell time on tweet, clicks, scrolls) → LB → Analytics Service → **Kafka** → downstream consumers (Hadoop, Spark, ML)

---

## 5. Tweet Write Path

```
       ┌─────────┐     text+URL+asset
       │   UI    │──────────────────────┐
       └────┬────┘                      │
            │                           ▼
    (media) │              ┌─────────────────────┐
            ├─────────────►│  Tweet Ingestion    │
    (URL)   │              │  Service            │
            │              └──────┬──────┬───────┘
            ▼                     │      │
    ┌──────────────┐              │      └──►  Cassandra
    │Asset Service │              │            (tweetID, userID, content, ts)
    │(video/img)   │              ▼
    └──────────────┘           Kafka  ── "new tweet t1 by u1"
                                 │
      ┌──────────────────────────┼──────────────────────────┐
      ▼                          ▼                          ▼
┌──────────────┐        ┌──────────────────┐        ┌──────────────┐
│Tweet Processor│       │ Search Consumer  │        │Spark Stream  │
│(fan-out write)│       │ → Elasticsearch  │        │(trends)      │
└──────────────┘        └──────────────────┘        └──────────────┘
```

### Sub-components
| Component | Purpose |
|-----------|---------|
| **Asset Service** | Handles image/video upload & delivery (reuse Netflix-like design) |
| **Short URL Service** | Shrinks big URLs (reuse TinyURL design) |
| **Tweet Ingestion** | Persists text + emits Kafka event. **Write-only, no GET APIs.** |
| **Cassandra** | Chosen over HBase → simpler ops (no ZK/Hadoop needed), horizontally scalable |
| **Tweet Service** | Source-of-truth read API on top of Cassandra (get by tweetID, tweets by user, timeline queries) |

---

## 6. Tweet Read Path (The Interesting Part)

### Two Views
- **User Timeline** = your own posts + retweets. `SELECT * FROM tweets WHERE userID = you`
- **Home Timeline** = tweets from everyone you follow. `SELECT * FROM tweets WHERE userID IN (people you follow)`

> ❌ Doing this SELECT on Cassandra at runtime for 150M users hitting home screen repeatedly → **doesn't scale**.
> ✅ **Precompute home timelines** and cache in Redis.

### 6.1 Fan-out on Write (for Active users)

```
u1 posts t1
    │
    ▼
  Kafka
    │
    ▼
┌──────────────────┐    Graph Service    ┌─────────────────┐
│ Tweet Processor  │────────────────────►│ get followers   │
└────────┬─────────┘                     │ of u1 → u2,u3,u4│
         │                               └─────────────────┘
         ▼
   For each follower ui:
   Prepend t1 to  Redis["timeline:ui"]
```

**Data structure in Redis:**
```
timeline:userID → { tweets: [t_n, t_n-1, ...], lastFamousMergeTS: T_i }
```

### 6.2 Timeline Service — Read Flow

```
User hits home screen
        │
        ▼
Timeline Service asks User Service: what type is this user?
        │
        ├─ ACTIVE  → hit Redis → return timeline (cache hit)
        │
        ├─ PASSIVE → build on-demand:
        │     1. Graph Service → who do they follow?
        │     2. Tweet Service → last N tweets of each
        │     3. Sort by timestamp → store in Redis → return
        │
        └─ LIVE    → don't even query. Push new tweets via WebSocket
                     as they arrive.
```

### 6.3 Handling Famous Users (Fan-out on READ)

> **Problem:** Donald Trump has 75M followers. One tweet → 75M Redis updates. ❌ Not scalable.
>
> **Solution:** For famous users, do NOT fan-out. Merge their tweets at read time.

```
Timeline Service read flow (with famous-user merge):

1. Get precomputed timeline from Redis (contains only normal-user tweets)
2. Look at lastFamousMergeTS stored with it
3. If (now - lastFamousMergeTS) > threshold (e.g., a few seconds):
      - Ask Graph Service: which famous users does this user follow?
      - Ask Tweet Service: latest tweets from those famous users
      - Merge with existing timeline, sort by ts
      - Write merged result + new lastFamousMergeTS back to Redis
4. Else: return Redis result directly (no famous re-query needed)
5. Return final timeline to UI
```

### 6.4 Famous-follows-Famous Corner Case
If Trump follows Musk, neither fan-outs normally. But they should still see each other.
→ **Tweet Processor** does a *small* fan-out only among other **famous users** who follow this famous user. Cheap because famous-user count is small.

---

## 7. Summary Matrix — Who Does What

| User Type      | Write-time behavior           | Read-time behavior                   |
|----------------|-------------------------------|--------------------------------------|
| Active reader  | Their timeline gets updated   | Cheap Redis lookup                   |
| Live reader    | Same as active + push notif   | WebSocket push (no query!)           |
| Passive reader | Nothing happens               | Build timeline on-demand at login    |
| Famous poster  | No fan-out to normal followers| Merged in at read time (per-user)    |
| Famous→Famous  | Fan-out only to other famous  | Same as above                        |

---

## 8. Search Subsystem

```
Kafka (tweets) ─► Search Consumer ─► Elasticsearch (Lucene, TF-IDF ranking)

User → Search UI → LB → Search Service ─► Redis (search cache, TTL ~2-3 min)
                                      └► Elasticsearch (on miss)
```

- **Why Elasticsearch?** Text search + relevance ranking (TF-IDF via Lucene)
- **Why Redis cache?** Trending queries are searched repeatedly → cache result for 2–3 min TTL → massive load reduction on ES
- Some lag OK per NFR (tweet may appear in search 5 min later)

---

## 9. Analytics & Trends

```
Kafka (tweets) ──► Spark Streaming Consumer ──► Trend Service (Redis, ~30 min)
                                                        │
                                                        ▼
                                                    Trends UI
```

- **Spark Streaming:** tokenize each tweet → remove stop-words → aggregate top words over last 1 hour
- Refresh every ~30 min
- Also computed **per geography** (India trends, France trends…)

### Batch Analytics (Hadoop)
- All tweets also dumped into Hadoop for offline analytics
- Use cases: most engaging user, most retweeted, weekly personalized newsletter (ML → top 5 relevant tweets for each *passive* user → **Notification Service** → email/SMS/push)

---

## 10. Data Store Summary

| Data | Store | Why |
|------|-------|-----|
| User profile | MySQL + Redis | Relational, bounded, rarely updated |
| Follow graph | Sharded MySQL + Redis | Relational edges, huge but shardable |
| Tweets | Cassandra | Massive write scale, horizontal scaling |
| Timelines (precomputed) | Redis | Sub-ms reads for home screen |
| Search index | Elasticsearch | Lucene text search + TF-IDF |
| Search cache | Redis | 2-3 min TTL for hot queries |
| Trends | Redis | Temp data, refreshed by Spark |
| Assets (img/video) | Asset Service (S3-like) | Blob storage |
| Analytics warehouse | Hadoop | Batch/ML workloads |
| Message bus | Kafka | Decouple ingest → all downstream consumers |

---

## 11. Bottlenecks & Scaling Notes

⚠️ **Critical components to scale:**
1. **Cassandra** — heavy queries, especially for passive users' timeline builds
2. **Redis** — everything's in RAM → expensive, needs sharding + TTLs to evict stale data
3. **Kafka** — hub of everything; must be sized well

✅ **All three are horizontally scalable.** Add machines as tweet volume grows.

⚠️ **TTL on Redis is essential** — otherwise stale timelines / cache pollute memory.

---

## 12. NFR Mapping Cheatsheet

| NFR | How the design satisfies it |
|-----|-----------------------------|
| Read-heavy 100:1 | Precompute timelines + heavy caching (Redis everywhere) |
| Home-screen <1s | Cache hit on Redis (active users), WebSocket push (live) |
| Highly available | Every layer stateless + horizontal scale; DBs clustered |
| Lag acceptable | Async fan-out via Kafka; eventual consistency is fine |
| Cannot go down | No single point of failure; horizontally scalable stores |

---

## 13. 🎯 Interview One-Liners (Memorize)

1. **"Twitter is read-heavy; the trick is to fan-out tweets to follower timelines at write time and cache them in Redis."**
2. **"Users are classified — active/live/passive/famous/inactive — because a single strategy doesn't fit all."**
3. **"Famous users flip the model: we merge their tweets at read time to avoid millions of cache writes per tweet."**
4. **"Kafka sits between ingestion and every consumer (timelines, search, trends, analytics) — one write, many derived views."**
5. **"Cassandra for tweets (write scale), MySQL for users/graph (relational), Elasticsearch for search, Redis everywhere for latency."**
6. **"Live users get WebSocket push instead of polling; passive users' timelines are built lazily on login."**

---

## 14. Related Systems to Cross-Reference

- **Asset Service** → Netflix / Amazon Prime video-hosting design
- **Short URL Service** → TinyURL design
- **Notification Service** → Scalable notification system design
- **Facebook-scale variant** → When scale is 5× Twitter, in-memory Redis fan-out breaks → different architecture needed

---

### 🖼️ One-Glance Recap Diagram

```
                       ┌─────────────────────────────────────────┐
                       │              KAFKA (spine)              │
                       └──▲───────────────▲───────────────▲──────┘
                          │               │               │
        ┌─────────────────┘               │               └──────────────┐
        │                                 │                              │
┌───────┴────────┐              ┌─────────┴──────────┐         ┌─────────┴────────┐
│Tweet Ingestion │              │  Tweet Processor   │         │ Spark / Search   │
│(write→Cassandra│              │(fan-out to timeline│         │  Consumers       │
└────────────────┘              │  Redis for active) │         └──────────────────┘
                                 └────────┬───────────┘
                                          │
                                          ▼
             ┌────────────────────────────────────────────────┐
             │              REDIS TIMELINE CACHE               │
             │  key: userID  →  {tweets[], famousMergeTS}      │
             └──────────────────┬─────────────────────────────┘
                                │
                                ▼
                       ┌────────────────┐
                       │Timeline Service│  ← merges famous users at read time
                       └───────┬────────┘
                               ▼
                            User UI
```

---

**End of Notes** — Use this as a last-minute recap before your next system design interview. 🚀
