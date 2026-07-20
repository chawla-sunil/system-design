# Facebook System Design — Interview Revision Notes

This folder is made from the summary of the following sources:
- [Github Design Images](https://github.com/codekarle/system-design/blob/master/system-design-prep-material/architecture-diagrams/Facebook%20System%20Design.png)
- [Blog](https://www.codekarle.com/system-design/facebook-system-design.html)
- [YouTube](https://www.youtube.com/watch?v=9-hjBGxuiEs&list=PLhgw50vUymycJPN6ZbGTpVKAJ0cL4OEH3&index=6)

> Same design applies to Instagram / LinkedIn / Twitter with minor tweaks.

---

## ⚡ 30-Second Interview Recap (Read This First)

- **Problem**: Design a read-heavy global social network (1.7B DAU, 2.6B MAU, 95% mobile). Post/Like/Comment/Share, add friends, timeline, activity log.
- **Guiding principles**: Latency low, small **lag is OK** (10–20s to propagate a post is fine → gives us room to run ML/relevance async). Read-heavy → cache aggressively.
- **Core write path**: Client → Post Ingestion → Cassandra + Kafka → Analytics (tags) → Post Processor → **fan-out to friends' timelines in Redis** (only for normal users). Famous users are **fan-out on read** to avoid millions of Redis writes.
- **Core read path**: Timeline Service pulls normal-user posts from Redis + famous-user posts from Post Service, merges, returns. Old timelines live in **Cassandra (Archival)**; new ones in **Redis**.
- **Key stores**: MySQL (user, graph) · Cassandra (posts, comments, likes, archival, activity) · Redis (timelines, user cache, like counts) · Kafka (event backbone) · Elasticsearch (search) · S3 + CDN (media) · Hadoop + Spark (offline analytics).
- **Killer optimizations**: user segmentation (Famous/Active/Live/Passive/Inactive), WebSocket push for live users, CDN eviction based on access pattern, Relevance Tags to filter fan-out audience.

---

## 1. Requirements

### Functional
| # | Feature | Notes |
|---|---------|-------|
| 1 | Post | Text / image / video |
| 2 | Like | On posts (extendable to comments) |
| 3 | Comment | On posts only (kept simple) |
| 4 | Share | Modeled as a new post with `parent_id` |
| 5 | Add Friend | **Non-directional** (bi-directional) relationship, but has a **directional weightage** |
| 6 | Timeline | Own timeline (friends' posts) + others' profile timeline |
| 7 | View profile / posts | UX pages |
| 8 | Activity Log | Every user action tracked (post/like/comment/search/share) |

### Non-Functional
- **Read-heavy**: ~100 reads per write.
- **Low latency**, **lag is OK** — page must render fast, but a post can take 10–20s to reach friends.
- **Access pattern**: Post is hot on creation, peaks, then decays → use to evict media from CDN.
- **Global**: multiple languages, wide device variety, wide bandwidth range → geo-distributed CDNs.
- **Scale**: 1.7B DAU · 2.6B MAU · 95% mobile · per **minute**: 150K images, 300K statuses, 500K comments.

---

## 2. User Segmentation (VERY IMPORTANT — drives design decisions)

| Segment | Definition | How we treat them |
|---|---|---|
| **Famous** | Huge friend/follower count | **Fan-out on read** (don't push to millions of timelines) |
| **Active** | Used FB in last ~3 days | Pre-compute timeline, cache in Redis |
| **Live** | Currently on the app | Push updates via **WebSocket** |
| **Passive** | Rarely active | No caching; compute on demand |
| **Inactive** | Deleted/fake/soft-deleted | Ignore |

---

## 3. High-Level Architecture (Big Picture)

```
                          ┌─────────────────────────────────────────┐
                          │   Clients (Mobile 95% / Web)            │
                          └──────────────┬──────────────────────────┘
                                         │
                              ┌──────────▼──────────┐
                              │  LB + Reverse Proxy │
                              │  + Auth / AuthZ     │
                              └──────────┬──────────┘
                                         │
   ┌────────────┬────────────┬───────────┼──────────┬─────────────┬───────────┐
   │            │            │           │          │             │           │
┌──▼───┐  ┌─────▼────┐ ┌─────▼────┐ ┌────▼─────┐ ┌──▼────┐  ┌─────▼────┐  ┌──▼──────┐
│User  │  │ Graph    │ │ Post     │ │ Timeline │ │ Like  │  │ Comment  │  │ Search  │
│Svc   │  │ Svc      │ │ Ingestion│ │ Svc      │ │ Svc   │  │ Svc      │  │ Svc     │
└──┬───┘  └────┬─────┘ └─────┬────┘ └────┬─────┘ └──┬────┘  └─────┬────┘  └──┬──────┘
   │           │             │           │          │             │          │
   │ MySQL     │ MySQL       │           │ Redis   │ Cassandra    │ Cassandra│ ES
   │ +Redis    │ +Redis      ▼           │ (T-line)│ +Redis(count)│          │ +Redis
   │           │        ┌────────┐       │          │             │          │
   │           │        │ Kafka  │◄──────┼──────────┴─────────────┴──────────┘
   │           │        └───┬────┘       │ (all events fan-in)
   │           │            │            │
   │           │       ┌────▼─────┐  ┌───▼────────┐   ┌────────────┐
   │           │       │ Analytics│  │Post        │   │Archival Svc│
   │           │       │ (ML tags)│  │Processor   │   │(Redis→Cass)│
   │           │       └────┬─────┘  │(fan-out)   │   └────────────┘
   │           │            │        └──┬─────────┘
   │           │            ▼           │
   │           │       ┌─────────┐      │        ┌──────────────┐
   │           └──────►│ Hadoop  │      └───────►│Live User Svc │◄── WebSocket
   │                   │(Spark)  │               │ (push)       │    to clients
   │                   │profile+ │               └──────────────┘
   │                   │graphWt+ │
   │                   │trends   │
   │                   └─────────┘
   │
Media path: Client → Post Ingestion → Asset Service → S3 (cold) + CDN (hot)
URL path:   Client → Post Ingestion → Short URL Service → Long URL redirect
```

**Legend**: Green = client, Black = LB/Auth, Blue = Web services, Red = DBs / Kafka / third-party.

---

## 4. Component-by-Component Walkthrough

### 4.1 User Service
- **Owner** of user info (name/email/phone/location) — source of truth.
- **DB**: Clustered **MySQL** (sharded) — relational, rarely-changing data.
- **Cache**: **Redis** — cache-aside: read → Redis → miss → MySQL → update Redis.
- **Events**: On signup/update → **Kafka** (fraud checks, notification prefs, etc.).
- Redis also stores against `userId`:
  - User Details
  - **User Type** (famous/active/live/passive)
  - **Relevance Tags** (from analytics — see §5)
  - **Last Access Time**

### 4.2 Graph Service
- Manages the **friendship graph** + **directional weightage**.
- **DB**: Clustered MySQL — core table `{user_id, friend_id}` (very large; sharded).
- **Cache**: Redis with `key = user_id`, `value = list of friends`.

### 4.3 Post — Write Path

```
Client (Add Post)
   │
   ▼
Post Ingestion Service ──► Asset Service (if media) ──► S3 + CDN
   │                   ──► Short URL Service (if URL)
   │
   ├──► Cassandra (posts)     [postId, userId, content, ts, ...]
   │
   └──► Kafka (post-created event)
              │
              ▼
        Analytics Streaming Consumer
        (ML classification → tags: sports/politics/tech...)
              │
              ▼
        Kafka (post-tagged event)
              │
              ▼
        Post Processor
              │
              ├─► User Svc  (fetch relevance tags of friends)
              ├─► Graph Svc (fetch friend list)
              │
              │  Match post tags vs user's relevance tags →
              │  compute audience subset
              │
              └─► Redis (append postId to each target user's timeline)
                       │
                       └─► If user is LIVE → push event to Live User Svc topic
```

- **Post Service** = read-side owner of posts. APIs: `getPostById`, `getPostsByIds`.
- **Storage**: **Cassandra** — chosen for very high write throughput (HBase is alternative).
- **Why async fan-out is OK**: NFR allows lag → ML tagging + relevance computation happens in the background.

### 4.4 Asset Service (Media)
- Handles multiple resolutions/formats/aspect ratios (mobile vs desktop).
- **Hot media in CDN**, cold in **S3**.
- Uses the access-pattern property: when a post's traffic decays → evict from CDN → S3.
- If an old photo suddenly gets hit (e.g., celebrity comment) → pull back from S3 → warm CDNs where it's being accessed.

### 4.5 Timeline Service — Read Path

Two flavors:

**(a) Viewing someone else's profile timeline** → simple: `Timeline Svc → Post Svc → return`.

**(b) Own timeline (friends' feed)** — the interesting one:

```
Client → Timeline Svc
            │
            ├─► Redis  (posts from NORMAL friends — pre-computed by Post Processor)
            │
            ├─► Graph Svc  (get friends, split into normal vs famous)
            │
            ├─► Post Svc  (fetch famous friends' recent posts — pull model)
            │
            ├─► Merge, rank, return
            │
            └─► Optionally cache merged timeline back to Redis with timestamp Ti
                (Reuse for a minute; re-pull famous posts if stale)
```

- **Why hybrid (push + pull)?** Push-only fails for famous users (millions of Redis writes per post). Pull-only fails for regular users (join+scan on read is too slow at scale). Split by user type.

### 4.6 Live User Service (WebSocket Push)
- Maintains persistent **WebSocket** connections with all currently-online clients.
- Post Processor detects target user is LIVE → publishes to a dedicated Kafka topic.
- Live User Svc consumes → pushes new-post event over the socket → app renders instantly.

### 4.7 Archival Service (Cost Optimization)
- **Rule**: Redis only holds **today's** timelines. Everything older → **Cassandra (Aggregated Timeline)**.
- Daily job: flush Redis timelines → write to Cassandra as `{userId, date, [postIds...]}`.
- Timeline Svc pulls from Archival Cassandra when user scrolls to older days.
- Also computes/backfills timelines for **passive/famous** users so they aren't recomputed on demand.
- Stores **only postIds** — actual content fetched from Post Service.

### 4.8 Likes
```
Client → Like Svc ─► Cassandra {postId, userId, [type, parentType]}
                 ─► Redis: INCR count on postId (atomic; TTL a few days)
                 ─► Kafka (like event) → analytics + activity tracker
```
- Redis for **count only** — because feed cards show "N likes". `SELECT COUNT(*)` on Cassandra would be slow.
- TTL: only recent posts are counted from Redis; old posts computed on demand.
- Schema extension for comment-likes: add `parent_type` (post/comment) and optional `like_type` (up/down).

### 4.9 Comments
```
Client → Comment Svc ─► Cassandra {postId, userId, text, ts}   (partition by postId)
                    ─► Kafka (comment event)
```
- **No Redis needed** — lookup by `postId` on Cassandra is already fast (aggregate not needed).

### 4.10 Share
- Just another post with a `parent_post_id` pointing to the original.

### 4.11 Activity Tracker
- Consumes **the same Kafka topic** all services publish to (posts, likes, comments, searches).
- **Cassandra** schema: `{userId, timestamp, action, attributes}`.
- Provides both write (from Kafka consumer) and read (activity UI) APIs.

### 4.12 Search
- Same as Twitter design: Kafka consumer → **Elasticsearch** → Search Service → optional Redis cache for hot queries.
- Search queries themselves are pushed to Kafka → Activity Tracker logs them.

---

## 5. Analytics (Offline / Batch — Hadoop + Spark)

All events pour into Kafka → **Spark Streaming Consumer** → **Hadoop (HDFS)**. Then run:

### 5.1 User Profile Job
- Classifies user interests from their posts/likes/comments (e.g., "sports, politics").
- Writes profile tags back → Kafka → User Service → stored as **Relevance Tags** in Redis.
- Used by Post Processor to decide **who sees a post** (a sports post goes to sports-interested friends, not everyone).

### 5.2 Graph Weight Job
- Directional friendship weight: how often does A like/comment on B's posts?
- Output: personalized affinity → some friends' posts rank higher in your feed.

### 5.3 Trends Job
- Tokenize all posts/comments, strip stop-words, count frequencies over a time window.
- Store top phrases in Redis via **Trends Service** → powers "what's trending".

---

## 6. Data Store Choices — Quick Justifications

| Store | Where used | Why |
|---|---|---|
| **MySQL** | User Svc, Graph Svc | Structured, rarely changing, relational |
| **Cassandra** | Post, Comment, Like, Archival, Activity | Massive write throughput, horizontal scale, wide-row lookups by partition key |
| **Redis** | User cache, Graph cache, Timeline (today), Like counts, Trends | Sub-ms reads, atomic INCR, TTL |
| **Kafka** | Backbone for all cross-service events | Decouple producers/consumers, durable, replayable |
| **Elasticsearch** | Search | Text tokenization, ranking |
| **S3 + CDN** | Media | Cheap cold storage + geo-distributed hot delivery |
| **Hadoop + Spark** | Offline ML / trends | Batch analytics on massive data |

---

## 7. Gotchas / Common Interview Follow-ups

- **Cassandra hot spots**: Never use `date` as a partition key — all traffic hits one node. Use `userId` or hash-based key. Applies to posts, timeline archive, activity, comments.
- **Fan-out explosion for famous users**: fix with hybrid push+pull (§4.5).
- **Media cost**: use access-pattern decay to evict from CDN to S3, and rehydrate on unexpected spikes (old celeb photo going viral).
- **Global latency**: geo-distributed CDNs; potentially regional service deployments.
- **Horizontal scaling**: every service, DB, and cache scales by adding nodes.
- **Monitoring**: latency, throughput, CPU/mem/disk on every service + DB + cache + Kafka. Alerts on threshold breaches.
- **Only postIds are cached** anywhere — actual content comes from Post Service (single source of truth). Avoids stale-content problems.

---

## 8. Timeline Data Model — Cheat Sheet

```
Redis (today's timelines, per user):
  key   : "timeline:{userId}"
  value : sorted set / list of postIds with timestamps
  TTL   : end of day → archived

Cassandra (archival):
  PRIMARY KEY ((userId), date)   -- partition by userId, cluster by date
  value: List<postId>

Cassandra (posts):
  PRIMARY KEY (postId)
  cols: userId, content, mediaRefs, createdAt, parentPostId (for shares)

Cassandra (likes):
  PRIMARY KEY ((postId), userId)
  cols: parentType, likeType, ts

Cassandra (comments):
  PRIMARY KEY ((postId), commentId)
  cols: userId, text, ts

Redis (like counts):
  key   : "likes:{postId}"    value: INTEGER (INCR)   TTL: days
```

---

## 9. 60-Second Whiteboard Recap Order (for interview)

1. Clarify FR/NFR (read-heavy, lag OK, mobile-first, global).
2. Numbers (DAU/writes-per-minute).
3. Segment users (Famous/Active/Live/Passive/Inactive) — earns huge points.
4. Draw the boxes: User, Graph, Post Ingestion, Post Svc, Timeline Svc, Like, Comment, Search, Live, Activity, Asset, Analytics.
5. Walk write path (Post → Cassandra → Kafka → Analytics → Post Processor → Redis).
6. Walk read path (Timeline Svc: Redis for normal + Post Svc for famous + Archival for old).
7. Call out **hybrid fan-out** + **WebSocket for live** + **CDN decay for media** — these are the differentiators.
8. Analytics loop: Hadoop/Spark → User Profile + Graph Weight + Trends → back to serving path via Redis.
9. Mention scaling + monitoring + Cassandra hot-spot pitfall.

---

*Same architecture generalizes to Twitter, Instagram, LinkedIn. Twitter differs mainly in "follow" being directional and lower media volume.*
