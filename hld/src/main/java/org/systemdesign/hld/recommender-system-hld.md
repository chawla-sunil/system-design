# Recommender System - 40-50 Minute HLD Interview

> **Candidate level:** Software engineer with about 5 years of experience
>
> **Prompt:** "Design a recommendation system that analyzes user preferences and behavior to suggest relevant content, products, or services with personalized ranking and filtering."
>
> **Example surface:** A personalized home feed for a large content or e-commerce platform. The same design can recommend videos, posts, music, products, jobs, restaurants, or services.

---

## 1. How I Would Start the Interview

I would not start by naming a machine-learning algorithm. A recommender system is an end-to-end data and serving system: it collects behavior, builds features, retrieves a manageable set of candidates, ranks them, applies business and safety constraints, serves results at low latency, and learns from feedback.

### Opening statement

> **Candidate:** "I will first clarify the recommendation surface, success metric, scale, freshness, and filtering rules. Then I will define requirements and estimates, design the APIs and data model, draw the online serving and offline learning architecture, and deep-dive into candidate generation, ranking, filtering, feedback, cold start, reliability, and experimentation."

### The four highest-value questions to ask

1. **What are we recommending and on which surface?** Is this a personalized home page, similar-items widget, search ranking, email, or notifications? Are items videos, products, posts, or services?
2. **What is the optimization objective?** Should we maximize clicks, watch time, purchases, revenue, long-term retention, or a weighted combination? Are there diversity, fairness, safety, or seller-exposure constraints?
3. **What scale and latency should I design for?** How many users, active items, recommendation requests, behavior events, regions, and what p95 latency and availability?
4. **How fresh must recommendations be?** Should a click, purchase, new item, inventory change, or content takedown affect recommendations within seconds, minutes, or a day?

These questions change the design more than asking about a particular database or model.

### Simulated interviewer answers

> **Interviewer:** Design the personalized home page of a global video platform. Each request should return a ranked list of videos. Users can filter by language and category. The design should also support excluding already-consumed or unavailable videos.

> **Interviewer:** Optimize primarily for meaningful watch time and long-term engagement, not raw clicks. Maintain diversity, freshness, creator fairness, content safety, and age restrictions.

> **Interviewer:** Assume 100 million daily active users, 10 million active videos, 1 billion recommendation requests per day, 20 billion interaction events per day, and a peak of 50,000 recommendation requests per second. The recommendation API should have p95 latency below 200 ms and 99.99% availability.

> **Interviewer:** User interactions should influence results within one minute. New videos should become eligible within a few minutes. Deletions, policy blocks, and regional restrictions should take effect within seconds.

### What I state back

> **Candidate:** "I will design a multi-stage recommender for a personalized home feed. The online path will retrieve roughly 1,000-2,000 candidates from several sources, pre-rank them, run a more accurate ranking model, apply hard eligibility filters and final re-ranking, and return about 20-50 items within 200 ms. Kafka will durably capture interaction and catalog events. Stream processing will update near-real-time features and invalidation state; batch pipelines will compute historical features and train models. An online feature store, vector index, cache, relational metadata store, object storage/data lake, and analytical warehouse will each serve different access patterns."

### Assumptions I explicitly state

- This is a **home-feed recommender**, not a general search engine. Search ranking can reuse the ranker but has query-specific retrieval.
- The response contains 20 items and uses cursor-based pagination.
- Logged-in users receive personalized results; anonymous users receive session, regional, and popularity-based recommendations.
- The catalog service is the source of truth for video metadata and availability.
- Recommendation results may be eventually consistent, but **safety, takedown, privacy, and entitlement filters must be fresh and fail closed**.
- A recommendation impression does not guarantee the user will see or click the item; the client must report actual impressions and interactions.
- Model training is asynchronous. Online behavior affects streaming features quickly, but a newly trained model is deployed only after validation.
- Exact model internals are not the HLD focus; model interfaces, features, latency, lifecycle, and failure behavior are.

---

## 2. Interview Time Plan

| Time | Discussion |
|---|---|
| 0-5 min | Clarifying questions, scope, objective, and assumptions |
| 5-9 min | Functional and non-functional requirements |
| 9-13 min | Capacity estimates |
| 13-18 min | APIs and data model |
| 18-25 min | High-level architecture and component responsibilities |
| 25-33 min | Deep dive: candidate generation and ranking |
| 33-38 min | Deep dive: event ingestion, features, and training |
| 38-42 min | Filtering, diversity, cold start, and exploration |
| 42-46 min | Scaling, caching, failures, multi-region, and privacy |
| 46-49 min | Metrics, A/B tests, model deployment, and trade-offs |
| 49-50 min | Final summary |

If time becomes limited, prioritize:

1. Multi-stage retrieval and ranking.
2. Online versus offline data flow.
3. Feature freshness and training-serving consistency.
4. Filtering correctness and failure behavior.
5. Metrics, cold start, and trade-offs.

---

## 3. Requirements and Scope

### 3.1 Functional requirements

| ID | Requirement |
|---|---|
| FR-1 | Return a personalized, ranked list of videos for a user or anonymous session |
| FR-2 | Learn from impressions, clicks, skips, watch duration, likes, dislikes, follows, and hides |
| FR-3 | Combine multiple recommendation strategies: collaborative, content-based, trending, followed creators, and exploration |
| FR-4 | Support user filters such as language, category, duration, and maturity preference |
| FR-5 | Exclude deleted, blocked, unavailable, age-inappropriate, region-restricted, or already-consumed items |
| FR-6 | Maintain diversity across creators, topics, and content types |
| FR-7 | Handle new users and new videos with sensible cold-start behavior |
| FR-8 | Support pagination without excessive duplicates or unstable ordering |
| FR-9 | Record why and from which model/version each recommendation was produced |
| FR-10 | Support controlled model rollout and A/B experiments |
| FR-11 | Allow users to hide items, reset recommendation history, or opt out of personalization |
| FR-12 | Quickly revoke an item from all recommendation surfaces |

### 3.2 Non-functional requirements

| ID | Target |
|---|---|
| NFR-1 | Recommendation API p95 below 200 ms and p99 below 350 ms |
| NFR-2 | 99.99% serving availability |
| NFR-3 | Support 50,000 peak recommendation requests/second |
| NFR-4 | Ingest over 20 billion interaction events/day with durable buffering |
| NFR-5 | User actions influence streaming features in under one minute |
| NFR-6 | Takedowns and hard eligibility changes propagate within seconds |
| NFR-7 | Horizontally scalable serving with no single global bottleneck |
| NFR-8 | Graceful degradation when models, features, or candidate sources fail |
| NFR-9 | Reproducible model training and consistent online/offline feature definitions |
| NFR-10 | Privacy, consent, retention, deletion, encryption, and access-control compliance |
| NFR-11 | Explainability and auditability at least at source/reason/model-version level |
| NFR-12 | Prevent one creator, topic, or popularity loop from monopolizing recommendations |

### 3.3 Out of scope

- Video upload, transcoding, playback, and CDN delivery.
- Full catalog management and moderation workflow; this system consumes their decisions.
- Advertising auction and sponsored-item billing. Sponsored candidates could be integrated as a separately labeled source.
- Building the internals of a deep-learning framework.
- Human editorial tooling beyond accepting curated candidate lists.
- Search query parsing and indexing.

### 3.4 Core invariants

State these because they drive the design:

1. A blocked, deleted, unauthorized, or region-ineligible item must never be intentionally returned.
2. Every served item is traceable to a request, model version, experiment, candidate source, and score/reason.
3. An interaction event has a unique ID and contributes at most once to derived aggregates.
4. Online inference uses version-compatible features and model schemas.
5. The ranking service does not treat caches, search indexes, or vector indexes as the source of truth for hard eligibility.
6. A model outage must not make the feed unavailable; a safe non-personalized fallback exists.
7. User deletion and consent changes propagate to both serving state and future training datasets.

---

## 4. Back-of-the-Envelope Estimation

The purpose is to expose dominant workloads, not produce exact procurement numbers.

### 4.1 Given and derived traffic

| Metric | Assumption |
|---|---:|
| Daily active users | 100 million |
| Active recommendable videos | 10 million |
| Recommendation requests/day | 1 billion |
| Average recommendation QPS | 1B / 86,400 = **~11,600 QPS** |
| Peak recommendation QPS | **50,000 QPS** |
| Items returned/request | 20 |
| Interaction events/day | 20 billion |
| Average event rate | 20B / 86,400 = **~231,000 events/sec** |
| Peak event rate at 3x | **~700,000 events/sec** |

### 4.2 Serving bandwidth

Assume each recommendation card contains IDs and compact metadata totaling 1 KB. In practice, image bytes come from a CDN.

```text
Response size = 20 items * 1 KB + envelope ~= 22 KB

Peak API egress = 50,000 requests/sec * 22 KB
                ~= 1.1 GB/sec before compression
```

The API should preferably return IDs plus compact card metadata. Thumbnails are CDN URLs, not proxied through the recommendation service.

### 4.3 Event throughput and storage

Assume an encoded event averages 500 bytes before replication:

```text
Raw event data/day = 20B * 500 bytes = 10 TB/day
Annual raw data     = 3.65 PB/year before compression and replication
```

Kafka needs enough partitions for approximately 700K peak events/second and enough retention to replay after downstream failures. The data lake stores compressed columnar files such as Parquet, partitioned by event date/hour and event type.

### 4.4 Feature storage

Example rough sizes:

```text
100M users * 5 KB hot online features  = 500 GB raw
10M items  * 10 KB online features     = 100 GB raw
10M item embeddings * 256 * 4 bytes    = ~10 GB raw
100M user embeddings * 256 * 4 bytes   = ~102 GB raw
```

Replication, indexes, allocator overhead, and multiple feature/model versions multiply these numbers. User history can be much larger, so the online store keeps bounded recent history and aggregates; full history remains in the data lake.

### 4.5 Inference cost

At 50K requests/second:

```text
Candidates retrieved/request       ~= 2,000
Candidates after pre-ranking       ~= 300
Candidates scored by main ranker   ~= 300
Final items                        = 20

Main ranking predictions/sec = 50,000 * 300 = 15M item scores/sec
```

This is why we do not score all 10 million videos. Candidate retrieval and pre-ranking are essential. Use batch/vectorized inference per request, efficient models, horizontal replicas, and optionally GPUs only when cost and latency justify them.

### 4.6 Likely bottlenecks

- Online feature-store fan-out and tail latency.
- Approximate nearest-neighbor retrieval at high QPS.
- Ranking inference cost.
- Hot-key behavior for globally trending content.
- Event volume and skew from very active users/items.
- Cache invalidation for policy and availability changes.

---

## 5. API Design

### 5.1 Get recommendations

```http
POST /v1/recommendations
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "surface": "HOME",
  "sessionId": "sess_72f1",
  "limit": 20,
  "cursor": null,
  "filters": {
    "languages": ["en", "hi"],
    "categories": ["technology", "education"],
    "maxDurationSeconds": 1800
  },
  "context": {
    "deviceType": "MOBILE",
    "locale": "en-IN",
    "region": "IN",
    "timeZone": "Asia/Kolkata"
  }
}
```

```json
{
  "requestId": "rec_01J8X...",
  "modelVersion": "home-ranker-2026-09-12",
  "experimentIds": ["home_ranker:B"],
  "items": [
    {
      "itemId": "video_981",
      "title": "Distributed Systems Explained",
      "thumbnailUrl": "https://cdn.example.com/...",
      "creatorId": "creator_17",
      "reason": "Because you watched system design videos",
      "trackingToken": "signed-opaque-token",
      "rank": 1
    }
  ],
  "nextCursor": "opaque-signed-cursor",
  "generatedAt": "2026-09-15T02:31:15Z"
}
```

**Why POST for a read?** The request has structured context and potentially large filters; it avoids sensitive parameters in URLs. It remains logically read-only. If CDN/HTTP caching is important for anonymous feeds, expose a cacheable `GET` variant for coarse cohorts.

**Important API behavior:**

- `requestId` correlates impressions, interactions, logs, and experiments.
- `trackingToken` is signed and contains or references request ID, item ID, position, candidate source, model version, and experiment assignment. The client cannot forge attribution.
- The cursor is opaque and signed. It can contain a recommendation-session ID, offset, seen-set reference, model version, and expiry.
- The server enforces a maximum limit, for example 50.
- Do not accept arbitrary user IDs from the body; derive identity from authentication.

### 5.2 Record client events

```http
POST /v1/events:batch
Authorization: Bearer <token>
```

```json
{
  "sessionId": "sess_72f1",
  "events": [
    {
      "eventId": "evt_01J8Y...",
      "eventType": "IMPRESSION",
      "itemId": "video_981",
      "trackingToken": "signed-opaque-token",
      "clientTimestamp": "2026-09-15T02:31:18.100Z",
      "metadata": {
        "visibleMillis": 1200,
        "position": 1
      }
    },
    {
      "eventId": "evt_01J8Z...",
      "eventType": "WATCH",
      "itemId": "video_981",
      "trackingToken": "signed-opaque-token",
      "clientTimestamp": "2026-09-15T02:35:18.100Z",
      "metadata": {
        "watchMillis": 240000,
        "completionRatio": 0.8
      }
    }
  ]
}
```

Return `202 Accepted` after authentication, schema validation, deduplication check where practical, and durable queue acknowledgment. The asynchronous pipeline performs enrichment and aggregation.

Event types include:

```text
IMPRESSION, CLICK, PLAY, WATCH, COMPLETE, SKIP,
LIKE, DISLIKE, SHARE, FOLLOW, HIDE, PURCHASE
```

Impressions are essential. Training only on clicked items creates selection bias because the model does not know what the user saw and ignored.

### 5.3 Explicit feedback/preferences

```http
PUT    /v1/users/me/recommendation-preferences
POST   /v1/users/me/hidden-items/{itemId}
DELETE /v1/users/me/hidden-items/{itemId}
POST   /v1/users/me/recommendation-history:reset
```

Example preferences:

```json
{
  "personalizationEnabled": true,
  "languages": ["en", "hi"],
  "blockedCategories": ["gambling"],
  "maturityLevel": "TEEN"
}
```

### 5.4 Administrative invalidation

An internal authenticated API or event is used by catalog/moderation:

```http
POST /internal/v1/items/{itemId}:invalidate
```

```json
{
  "reason": "POLICY_BLOCK",
  "regions": ["IN"],
  "effectiveAt": "2026-09-15T02:31:15Z",
  "catalogVersion": 982731
}
```

This updates the eligibility store and invalidates affected caches. Hard blocks must not wait for model retraining.

### 5.5 API error behavior

| Status | Meaning |
|---|---|
| `400` | Invalid filters, context, cursor, or event schema |
| `401/403` | Missing identity, invalid token, or forbidden preference/admin operation |
| `409` | Stale preference update where optimistic concurrency is used |
| `422` | Valid request but unsupported surface/filter combination |
| `429` | Per-user/client rate limit exceeded |
| `503` | No safe recommendation response can be produced |

When personalization dependencies fail, returning a clearly tracked popular-but-safe fallback is normally better than returning `503`.

---

## 6. Data Model and Storage Choices

No single database fits every access pattern.

### 6.1 Storage summary

| Data | Store | Why |
|---|---|---|
| Canonical user preferences, consent, experiments | PostgreSQL/MySQL | Transactions, constraints, auditability, low-to-medium write volume |
| Canonical catalog metadata | Existing catalog DB, often relational/document | Source-of-truth ownership stays with catalog service |
| Raw interaction events | Kafka, then object storage/data lake | Durable ordered buffering, replay, cheap long-term analytical storage |
| Online user/item features | DynamoDB/Cassandra/ScyllaDB or managed feature store | Key-based, high-QPS, horizontally scalable reads/writes |
| Very hot features/session state | Redis | Sub-millisecond TTL data and bounded recent history |
| Embedding nearest-neighbor lookup | Vector index such as FAISS/Milvus/Elastic/OpenSearch vector | Efficient approximate nearest-neighbor retrieval |
| Trending counters/top lists | Stream processor state + Redis | Sliding windows and low-latency reads |
| Training datasets/model artifacts | Object storage | Cheap, durable, versioned large files |
| Analytical queries/experiments | BigQuery/Snowflake/ClickHouse/Hive | Large scans, joins, aggregations, and BI |
| Model registry/metadata | MLflow-like registry + relational DB/object store | Versioning, lineage, approval, rollback |
| Recommendation-session/seen set | Redis or wide-column TTL store | Fast pagination state and automatic expiration |
| Full-text metadata retrieval | OpenSearch/Elasticsearch | Filterable text/category retrieval when needed |

### 6.2 Why not use only PostgreSQL?

PostgreSQL is excellent for preferences and business metadata, but not for:

- 700K peak event writes/second plus replay.
- PB-scale training scans.
- High-QPS vector nearest-neighbor search.
- Millions of per-request feature lookups with independently scalable partitions.
- Low-cost retention of raw behavioral data.

Using specialized stores avoids forcing transactional, streaming, analytical, vector, and caching workloads into one engine.

### 6.3 Canonical relational tables

```sql
CREATE TABLE recommendation_preferences (
    user_id                  BIGINT PRIMARY KEY,
    personalization_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    languages                TEXT[] NOT NULL,
    blocked_categories       TEXT[] NOT NULL,
    maturity_level           VARCHAR(20) NOT NULL,
    version                  BIGINT NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL
);

CREATE TABLE model_deployments (
    surface          VARCHAR(50) NOT NULL,
    model_version    VARCHAR(100) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    traffic_percent  NUMERIC(5,2) NOT NULL,
    feature_schema   VARCHAR(100) NOT NULL,
    artifact_uri     TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (surface, model_version)
);

CREATE TABLE experiment_assignments (
    subject_id       VARCHAR(100) NOT NULL,
    experiment_id    VARCHAR(100) NOT NULL,
    variant          VARCHAR(50) NOT NULL,
    assigned_at      TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ,
    PRIMARY KEY (subject_id, experiment_id)
);
```

Experiment assignment may be deterministically computed using a stable hash instead of persisted for every user:

```text
bucket = hash(experiment_id, user_id or session_id) mod 10,000
```

Persist only when eligibility changes over time or strict assignment auditing requires it.

### 6.4 Event schema

Events use Avro/Protobuf with a schema registry:

```text
InteractionEvent {
  event_id
  event_type
  event_time
  ingestion_time
  user_id?          // nullable for anonymous users
  session_id
  item_id
  request_id?
  tracking_token?
  surface
  position?
  watch_millis?
  completion_ratio?
  device_type
  region
  model_version?
  experiment_ids[]
  schema_version
}
```

Use both event and ingestion time. Stream processors need watermarks and allowed lateness because mobile clients can upload events late or out of order.

### 6.5 Online feature keys

```text
user_features:{user_id}:{feature_version}
  recent_categories
  recent_item_ids (bounded)
  category_affinity
  creator_affinity
  average_watch_duration
  last_active_at
  user_embedding

item_features:{item_id}:{feature_version}
  category
  language
  creator_id
  age_rating
  publish_time
  quality_score
  recent_ctr
  recent_watch_time
  item_embedding
```

Features should be fetched in batches, not one network request per candidate. Item features can be co-located, cached, or embedded into candidate-index records to avoid 2,000 remote reads.

### 6.6 Partitioning and indexes

- Kafka interaction topics: partition by `user_id` when per-user order is useful; anonymous events use `session_id`. Very hot users are rare and manageable.
- Online user features: partition by hashed `user_id`.
- Online item features: partition by hashed `item_id`, with cache for hot items.
- Data lake: partition by `event_date`, `event_hour`, and optionally `event_type`; avoid high-cardinality partitions such as user ID.
- Vector indexes: shard by embedding/model version and optionally language/region/category. Replicate read-heavy shards.
- Trending lists: key by `(region, language, category, time_window)`.
- PostgreSQL preference table: primary key on `user_id`; add only indexes required by admin or compliance jobs.

### 6.7 Retention and deletion

- Raw events: retain according to policy, for example 13 months, then aggregate or delete.
- Online recent history: bounded by count and TTL.
- Recommendation-session state: 30-60 minute TTL.
- Model artifacts and training snapshots: versioned retention with lineage.
- Deleting a user writes a tombstone to a privacy topic, removes online features and caches, and excludes/tombstones the user in data-lake datasets. Future training jobs consume deletion manifests.

---

## 7. High-Level Architecture

The design has three connected planes:

1. **Online serving plane** for low-latency recommendations.
2. **Streaming feature plane** for minute-level updates and invalidations.
3. **Offline ML/data plane** for historical feature computation, training, evaluation, and deployment.

```text
                                      ONLINE SERVING PLANE

 Mobile/Web Client
        |
        v
 Global DNS / Anycast / CDN
        |
        v
 API Gateway -- auth, quotas, rate limits, request ID
        |
        v
 Recommendation Orchestrator
   |        |          |           |             |
   |        |          |           |             +--> Experiment Service
   |        |          |           +----------------> User/Session Feature Store
   |        |          +----------------------------> Eligibility/Policy Store
   |        +---------------------------------------> Recommendation Cache
   |
   +--> Candidate Generation (parallel)
          |-- ANN collaborative retrieval
          |-- content-based ANN retrieval
          |-- followed-creator candidates
          |-- regional/category trending
          |-- fresh/exploration candidates
                    |
                    v
              Merge + Deduplicate
                    |
                    v
              Hard Pre-Filters
                    |
                    v
                Pre-Ranker
             2,000 -> 300 items
                    |
                    v
          Feature Hydration / Feature Store
                    |
                    v
              Ranking Service
               300 scored items
                    |
                    v
         Re-ranker / Constraint Solver
    diversity, freshness, fairness, seen-items
                    |
                    v
            Final Eligibility Check
                    |
                    v
       Response + Impression Context / Cache


                         EVENT + STREAMING FEATURE PLANE

 Client Events --> Event Collector --> Kafka --> Stream Processing
                                      |          |-- session/user aggregates
 Catalog/Policy Events -------------->|          |-- item aggregates/trending
                                      |          |-- recent-history updates
                                      |          +-- invalidation/cache eviction
                                      |
                                      +------> Data Lake (raw immutable events)


                              OFFLINE ML / DATA PLANE

 Data Lake + Catalog Snapshots + Labels
                  |
                  v
     Batch Feature Pipelines / Feature Registry
          |                           |
          |                           +--> Offline Feature Store / Training Sets
          v
  User/Item Embedding Jobs
          |                           Training Pipeline
          v                                  |
   Build ANN Index                           v
          |                         Offline Evaluation
          |                                  |
          +------------------------- Model Registry
                                             |
                                    Canary / Shadow / A-B
                                             |
                                             v
                                      Ranking Service
```

### 7.1 Component responsibilities

| Component | Responsibility |
|---|---|
| API Gateway | Authentication, abuse prevention, quotas, routing, request IDs |
| Recommendation Orchestrator | Deadline propagation, parallel retrieval, merge, ranking stages, fallback, tracing |
| Candidate services | Retrieve high-recall candidate subsets using different signals |
| Feature Store | Versioned, low-latency online features with matching offline definitions |
| Ranking Service | Batch-score user-candidate pairs using a versioned model |
| Re-ranker | Enforce diversity, fairness, freshness, exploration, and page-level constraints |
| Eligibility Service | Enforce availability, policy, age, region, entitlement, user blocks, and filters |
| Event Collector | Validate, authenticate, enrich minimally, and durably publish client events |
| Stream Processor | Windowed aggregates, recent history, trending, near-real-time feature updates |
| Data Lake | Immutable raw history and reproducible training input |
| Feature Registry | Feature definitions, ownership, type/schema, freshness, and offline/online transformations |
| Training Pipeline | Dataset creation, negative sampling, model training, evaluation, and artifact creation |
| Model Registry | Model lineage, metrics, approval, deployment state, and rollback |
| Experiment Service | Stable variant assignment and experiment configuration |

### 7.2 Why a multi-stage funnel?

Scoring every item is impossible:

```text
10 million items * 50,000 requests/sec
= 500 billion pair scores/sec
```

Instead:

```text
10M available items
  -> ~2,000 high-recall retrieved candidates
  -> ~300 inexpensive pre-ranked candidates
  -> ~100-300 accurately ranked candidates
  -> 20 diverse, eligible results
```

Early stages maximize **recall and speed**. Later stages maximize **precision and page-level quality**.

---

## 8. End-to-End Online Recommendation Flow

### 8.1 Request flow

1. The client calls `POST /v1/recommendations` with surface, session, cursor, context, and filters.
2. The API gateway authenticates the user, applies rate limits, generates a request ID, and routes to the nearest serving region.
3. The orchestrator loads preferences, consent, experiment assignment, session state, and compact user features in parallel.
4. It checks a recommendation cache keyed by a coarse context such as:

   ```text
   user_id + surface + filter_hash + experiment_variant + model_version
   ```

5. On a cache miss or insufficient cached items, the orchestrator calls candidate sources concurrently with individual deadlines.
6. Candidate lists include `(item_id, source, retrieval_score, reason, source_model_version)`.
7. The orchestrator merges and deduplicates candidates. If an item appears in multiple sources, preserve all source signals.
8. It applies cheap hard filters before expensive scoring: deleted, wrong region/language, blocked creator/category, age restriction, unavailable, and explicit user hides.
9. The pre-ranker uses inexpensive features to reduce approximately 2,000 candidates to 300.
10. Feature hydration batch-loads the required user, item, user-item cross, context, and real-time features.
11. The ranking service vectorizes and scores all remaining candidates in one or a few inference calls.
12. The re-ranker applies page-level constraints: diversity, freshness, fairness, source quotas, exploration, and near-duplicate suppression.
13. A final eligibility check protects against a policy change during ranking.
14. The orchestrator stores short-lived recommendation-session state for pagination, emits a `RECOMMENDATION_GENERATED` log, and returns items with tracking tokens.
15. The client emits an `IMPRESSION` only after an item is actually visible, then later sends click/watch/skip feedback.

### 8.2 Latency budget

All downstream calls receive deadlines; do not allow one source to consume the whole request.

| Stage | Budget |
|---|---:|
| Gateway/auth/routing | 10 ms |
| Context, preferences, experiments, user features | 15 ms |
| Candidate retrieval in parallel | 35 ms |
| Merge and hard pre-filter | 10 ms |
| Pre-ranking | 10 ms |
| Item/cross-feature hydration | 25 ms |
| Main ranking inference | 45 ms |
| Re-ranking and final eligibility | 15 ms |
| Serialization/network reserve | 30 ms |
| **Total p95 target** | **195 ms** |

The parallel retrieval stage costs approximately the slowest successful source, not the sum of all sources.

### 8.3 Pagination

Two reasonable choices:

**Option A: recommendation session**

- First request computes 100 ranked items, returns 20, and stores the rest under a session ID in a TTL store.
- Cursor references session ID and next offset.
- Stable pages, fewer repeated inference calls, and easy deduplication.
- State consumes memory and later pages may become less fresh.

**Option B: stateless recomputation**

- Cursor carries seen-item hashes/IDs, seed, and model version.
- Recompute on each page and exclude seen items.
- More fresh and stateless, but costs more and can reorder/duplicate due to changing inputs.

For a home feed, I would use a **hybrid**: keep a short-lived server-side feed session for the next few pages, then refresh. Explicit hides and hard invalidations are still applied when a later page is served.

---

## 9. Candidate Generation

Candidate generation aims for high recall: the best items must enter the funnel before the ranker can order them.

### 9.1 Collaborative filtering candidates

Learn user and item embeddings from the user-item interaction graph. Similar users and items are close in embedding space.

```text
user embedding --> ANN search over item embeddings --> top K items
```

Signals can weight meaningful watch time, completion, like, follow, repeat engagement, and negative feedback differently.

**Strengths:** discovers non-obvious behavioral similarity and strong personalization.

**Weaknesses:** cold start, popularity bias, feedback loops, sparse users/items, and embedding/index freshness.

### 9.2 Content-based candidates

Build item embeddings from title, description, transcript, category, creator, language, image/audio signals, and moderation tags. Build a user-interest vector from positively engaged items.

**Strengths:** works for new or niche items and provides explainable topic similarity.

**Weaknesses:** can over-specialize and repeatedly recommend nearly identical content.

### 9.3 Item-to-item candidates

Use recently watched/liked items as seeds:

```text
recent item A --similar-items ANN--> candidates
recent item B --co-watch graph-----> candidates
```

This captures short-term intent better than a long-term user profile.

### 9.4 Social/subscription candidates

Retrieve recent, eligible videos from followed creators or subscriptions. This source may have explicit business priority because the user requested the relationship.

### 9.5 Trending and popular candidates

Maintain top items by region, language, category, and sliding time window:

```text
trending_score =
    weighted_engagement
    / age_decay
    adjusted_for_impressions_and_spam
```

This is valuable for anonymous users, fallback, and current events. Do not use raw click counts because they favor already-exposed content.

### 9.6 Fresh/exploration candidates

Reserve a small candidate budget for new or uncertain items that pass minimum quality and safety checks. Without exploration, new items never receive impressions, so the system can never learn their quality.

### 9.7 Curated/editorial candidates

Allow policy-approved, time-bounded editorial collections for important events. Treat this as a candidate source with transparent rules, not hidden score manipulation.

### 9.8 Source quotas

Example retrieval budget:

| Source | Candidates |
|---|---:|
| User-to-item collaborative ANN | 600 |
| Recent-item similarities | 400 |
| Content-based | 350 |
| Followed creators | 200 |
| Trending | 200 |
| Fresh/exploration | 150 |
| Editorial | 100 |
| **Before deduplication** | **2,000** |

Budgets are configurable by surface and experiment. If one source fails or returns too few results, redistribute capacity to healthy sources.

### 9.9 ANN index freshness

- Rebuild a full base index daily or several times per day.
- Add new-item embeddings to a smaller mutable delta index every few minutes.
- Query base and delta indexes in parallel and merge.
- Periodically compact delta into the next base index.
- Version indexes and atomically switch readers after health checks.

This avoids rebuilding a 10-million-item index for every new video.

---

## 10. Ranking and Re-ranking

### 10.1 Pre-ranker

The pre-ranker is inexpensive, for example a linear model, small gradient-boosted tree, or shallow neural model. It uses:

- Retrieval scores and source identity.
- User-category and user-creator affinity.
- Item quality/popularity.
- Freshness and recency.
- Device, region, time of day.
- Simple negative signals.

Its goal is to remove clearly weak candidates without discarding good long-tail items.

### 10.2 Main ranker

The main ranker predicts multiple outcomes rather than only click probability:

```text
P(click)
expected_watch_time
P(meaningful_completion)
P(like/share/follow)
P(hide/dislike/quick_skip)
P(return_next_day)
```

A simplified utility:

```text
utility =
    w1 * normalized_expected_watch_time
  + w2 * P(meaningful_completion)
  + w3 * P(like_or_share)
  + w4 * P(long_term_return)
  - w5 * P(hide_or_dislike)
  - w6 * P(quick_skip)
```

Weights are product decisions tested through controlled experiments. Guardrails prevent increasing short-term watch time at the expense of user satisfaction, safety, or creator ecosystem health.

### 10.3 Feature groups

| Group | Examples |
|---|---|
| User | Language, category affinity, creator affinity, long/short-form preference, activity level |
| Item | Topic, creator, language, age, quality, freshness, global/regional engagement |
| User-item cross | Similarity, creator follow, prior exposure, topic match, embedding dot product |
| Context | Device, network, locale, time of day, day of week, session depth |
| Session | Recent watches/skips, current short-term intent, last query/category |
| Real-time | Last-hour CTR/watch time, current trend velocity, availability |
| Retrieval | Candidate source, source score, number of sources that retrieved the item |

Sensitive attributes should not casually become ranking features. Where fairness analysis needs them, use tightly controlled, purpose-limited offline evaluation.

### 10.4 Pointwise, pairwise, or listwise ranking

- **Pointwise:** predict engagement for each item independently. Easy and fast, but ignores ordering relationships.
- **Pairwise:** learn which of two items should rank higher. Often improves relative ordering.
- **Listwise:** optimize the whole slate/list. Better matches the surface, but is more complex and expensive.

I would begin with a pointwise or pairwise multi-task ranker, then use a deterministic re-ranker for slate constraints. Move to listwise learning only if experiments justify the operational complexity.

### 10.5 Re-ranking the page

The highest individual scores may form a poor page: twenty videos from one creator or topic. Re-ranking maximizes page utility subject to constraints.

Possible approach: Maximal Marginal Relevance (MMR):

```text
MMR(item) =
    lambda * relevance(item)
  - (1 - lambda) * max_similarity(item, already_selected_items)
```

Additional rules:

- Maximum two items from the same creator in a page.
- Minimum number of distinct topics.
- Cap recently seen topics/creators.
- Reserve controlled positions for exploration.
- Boost fresh content within a bounded range.
- Enforce creator or marketplace exposure constraints.
- Suppress near-duplicate clips.
- Avoid consecutive items with the same format.

Hard constraints guarantee policy. Soft constraints add score penalties/bonuses and may relax if the eligible pool is too small.

### 10.6 Calibration

Raw model probabilities may not be calibrated across models, sources, or content categories. Calibrate outputs so a predicted `0.7` has comparable meaning, especially when combining multiple objectives or candidate sources.

### 10.7 Explainability

Do not expose raw internal features or sensitive inferences. Produce safe reason codes:

```text
FOLLOWED_CREATOR
SIMILAR_TO_RECENTLY_WATCHED
POPULAR_IN_YOUR_REGION
MATCHES_SELECTED_TOPIC
NEW_FROM_A_TOPIC_YOU_FOLLOW
```

Reasons are generated from dominant approved signals and candidate source, not unconstrained model text.

---

## 11. Filtering and Eligibility

Filtering has two categories.

### 11.1 Hard filters

An item must be removed if any hard condition fails:

- Deleted, private, quarantined, or moderation-blocked.
- Not licensed or available in the user's region.
- Outside publication time window.
- Incompatible age/maturity rating.
- User blocked the creator/category/item.
- Subscription or entitlement required but absent.
- Language/category filter mismatch.
- Product out of stock or service unavailable, for commerce/service variants.

Hard filters should run early to save ranking cost and again immediately before the response to close the race with policy updates.

### 11.2 Soft filters/preferences

- Prefer selected languages but allow high-quality cross-language items if configured.
- Down-rank already-seen items rather than permanently exclude all of them.
- Penalize repetitive topics.
- Prefer available duration for the current session.

### 11.3 Where filtering runs

Apply filters at multiple points:

1. **During retrieval** using index partitions/metadata predicates, reducing waste.
2. **Before ranking** using a fast eligibility snapshot.
3. **During re-ranking** for page constraints.
4. **Immediately before response** for current hard blocks.

### 11.4 Fast takedowns

When moderation blocks an item:

1. Catalog/moderation publishes a versioned `ItemEligibilityChanged` event.
2. Stream processing updates a strongly refreshed regional eligibility store.
3. Cache invalidation removes precomputed lists containing that item where feasible.
4. Candidate services consume block-set updates.
5. The final eligibility check prevents stale cached/indexed candidates from escaping.
6. Clients may also receive a playback denial from the authoritative playback/entitlement service.

For policy safety, if the eligibility service cannot determine status, fail closed for affected candidates and fill from safe fallback lists.

---

## 12. Event Ingestion and Near-Real-Time Features

### 12.1 Event flow

```text
Client SDK
   |
   v
Regional Event Collector
   | validate schema/token, rate-limit, add server timestamp
   v
Kafka interaction topic
   |
   +--> Stream processor --> Online feature store
   |         |             --> Recent user history
   |         |             --> Trending lists
   |         |             --> Item/user counters
   |         +------------> Cache invalidations
   |
   +--> Object-storage sink --> Raw data lake
   |
   +--> Monitoring/fraud pipeline
```

### 12.2 Delivery semantics

Kafka and consumers are usually at-least-once. Exactly-once across every external store is expensive, so make processing idempotent:

- Every event has a globally unique `event_id`.
- Kafka producer idempotence prevents many producer retries from duplicating records.
- Stream processors checkpoint offsets and state.
- Deduplicate within a bounded event-time window.
- For long-window aggregates, use upserts with deterministic window keys.
- Critical billing-like outcomes would need stronger handling, but recommendations tolerate small corrected discrepancies.

### 12.3 Event-time windows

Use event time, watermarks, and allowed lateness:

```text
5-minute, 1-hour, 24-hour, and 7-day windows
```

Late events update windows until the watermark closes them. Very late events remain in the data lake and are incorporated by batch correction jobs.

### 12.4 Real-time versus batch features

| Streaming | Batch |
|---|---|
| Last 20 session events | 90-day topic affinity |
| Last-hour item trend velocity | Long-term creator affinity |
| Current item availability | Collaborative embeddings |
| Recent negative feedback | Historical completion rate |
| Session intent vector | User lifecycle segment |

Combine them at serving time or materialize a joined online feature record.

### 12.5 Lambda versus Kappa

A pure streaming/Kappa architecture reduces duplicate logic, but complex ML training joins and historical recomputation are often easier in batch. A pragmatic design uses:

- Streaming for freshness-sensitive features.
- Batch for long-history features and training.
- Shared feature definitions and validation to reduce training-serving skew.

---

## 13. Offline Training and Model Lifecycle

### 13.1 Training flow

1. Raw interaction events and catalog snapshots land in the versioned data lake.
2. Data-quality jobs validate schema, nulls, volume, distributions, bot traffic, and delayed partitions.
3. Label generation joins impressions with later outcomes within a defined attribution window.
4. Feature pipelines compute point-in-time-correct user, item, context, and cross features.
5. The dataset is split by time into training, validation, and test sets.
6. Training produces embeddings, retrieval models, pre-ranker, and main ranker artifacts.
7. Offline evaluation checks relevance, calibration, bias, diversity, safety slices, and latency/cost.
8. Approved artifacts enter the model registry with complete lineage.
9. Deployment proceeds through shadow, canary, and A/B stages.
10. Monitoring compares online metrics and feature distributions; automated or manual rollback is available.

### 13.2 Point-in-time correctness

Training must not use information created after the prediction time. For an impression at 10:00, do not join an item's end-of-day CTR or a user's later purchase.

Use versioned/as-of joins:

```text
feature_timestamp <= impression_timestamp
```

Otherwise offline performance looks excellent but fails in production due to data leakage.

### 13.3 Positive and negative labels

Positive examples:

- Meaningful watch duration/completion.
- Like, save, share, follow, purchase.
- Return engagement after consuming the item.

Negative examples:

- Visible impression with no click.
- Quick skip after play.
- Hide/dislike/report.

Do not label an unexposed catalog item as a negative; the user never had a chance to choose it. Negative sampling should come primarily from exposed items, plus carefully sampled alternatives for retrieval training.

### 13.4 Training-serving skew

Common causes:

- Different code computes the same feature offline and online.
- Time zones or windows differ.
- Missing-value defaults differ.
- Feature schemas/model versions mismatch.
- Online features are stale.

Mitigations:

- Central feature registry and shared transformation definitions.
- Version every feature schema.
- Log served feature values for a sampled fraction of predictions.
- Compare offline recomputation against logged online values.
- Reject model deployment if required online features are unavailable.

### 13.5 Model deployment

```text
Registered -> Validated -> Shadow -> Canary 1% -> 10% -> 50% -> 100%
                                      |
                                      +--> rollback on guardrail breach
```

- **Shadow:** new model scores copied traffic but does not affect users.
- **Canary:** model affects a small percentage; monitor latency, errors, scores, and safety.
- **A/B test:** stable user-level assignment measures product impact.
- Keep the prior model and compatible feature/index versions available for immediate rollback.

### 13.6 Model and feature version compatibility

A deployment manifest should pin:

```text
ranker_model_version
pre_ranker_version
user_embedding_version
item_embedding_version
ANN_index_version
feature_schema_version
re_ranker_config_version
```

Do not independently roll these pieces without compatibility checks.

---

## 14. Cold Start, Exploration, and Feedback Loops

### 14.1 New user

For a user with little history:

1. Ask for a few preferred languages/topics during onboarding, if product allows.
2. Use regional, language, device, and time-aware popularity.
3. Use session clicks/skips immediately.
4. Mix broad categories to learn preferences.
5. Gradually shift from cohort/session signals to personalized embeddings.

For anonymous users, key short-lived features by `session_id`. If they sign in and consent allows, merge appropriate session signals carefully.

### 14.2 New item

- Use metadata/content embeddings immediately.
- Retrieve from topic/category and similar-item indexes.
- Allocate exploration traffic after safety/quality checks.
- Use creator priors carefully, with caps so established creators do not dominate.
- Update streaming performance signals as impressions arrive.

### 14.3 Exploration versus exploitation

Pure exploitation recommends only known winners and creates a self-reinforcing popularity loop. Reserve, for example, 5% of slots or candidate budget for controlled exploration.

Possible methods:

- Epsilon-greedy with strict quality thresholds.
- Contextual bandits.
- Thompson sampling using uncertainty.
- Upper-confidence-bound score.

Start with a bounded exploration bucket because it is simple and safe. More advanced bandits require reliable delayed-reward attribution and off-policy evaluation.

### 14.4 Preventing feedback loops

- Train from impressions, not only clicks.
- Correct for position and exposure bias.
- Add exploration.
- Cap creator/topic repetition.
- Track catalog coverage and long-tail exposure.
- Use counterfactual/off-policy evaluation cautiously.
- Detect bots and engagement manipulation.
- Optimize for long-term satisfaction, not only immediate CTR.

---

## 15. Caching and Precomputation

### 15.1 What to cache

- User feature vectors and preferences.
- Item feature records.
- Trending lists by coarse cohort.
- Similar-item ANN results.
- Short-lived recommendation lists/pages.
- Model artifacts in ranking-service memory.
- Eligibility block sets with push invalidation and short TTL.

### 15.2 Recommendation cache trade-off

Per-user cache improves latency and cost but can be stale after an interaction. Use:

- Short TTL, for example 1-5 minutes.
- Cache enough candidates for multiple pages, not days.
- Filter cached results against current hard eligibility.
- Remove recently consumed/hidden items at read time.
- Refresh asynchronously when remaining eligible results fall below a threshold.

### 15.3 Cache key correctness

Include all dimensions that materially affect output:

```text
user/session
surface
filter hash
region
age/entitlement class
experiment/model version
```

Omitting region or maturity class risks serving an ineligible item.

### 15.4 Cache stampede prevention

- Request coalescing/single flight per key.
- TTL jitter.
- Serve slightly stale personalized candidates only after current hard filtering.
- Refresh ahead for active users.
- Fall back to cohort lists rather than overload rankers.

### 15.5 Precompute or rank on demand?

| Precompute | On demand |
|---|---|
| Low serving latency and predictable cost | Fresh context and behavior |
| Wasteful for inactive users | Higher inference cost |
| Stale quickly | More dependency/tail-latency risk |
| Good for coarse candidates/fallback | Best for final ranking |

Use a hybrid: precompute embeddings, ANN indexes, similar items, and optional candidate pools; perform final filtering and ranking online.

---

## 16. Scalability and Multi-Region Design

### 16.1 Horizontal scaling

- Serving services are stateless and scale by QPS/CPU/GPU utilization and tail latency.
- Partition feature stores by user/item ID.
- Shard vector indexes, with replicas for hot shards.
- Partition Kafka topics sufficiently and scale consumer groups.
- Batch item-feature fetches and reuse local caches.
- Separate candidate, ranking, and event ingestion autoscaling because their bottlenecks differ.

### 16.2 Regional architecture

Deploy a complete serving stack in several regions:

```text
Global traffic manager
   |-- Americas recommendation region
   |-- Europe recommendation region
   +-- Asia recommendation region
```

Each region contains orchestrators, ranking replicas, candidate indexes, eligibility state, online feature replicas, caches, and regional event collectors.

### 16.3 Data replication

- User traffic is routed to a home or nearest compliant region.
- Catalog metadata, item features, model artifacts, and ANN indexes replicate globally.
- User features remain within allowed data residency boundaries.
- Events are collected regionally and copied to approved central/regional lakes.
- Preferences/consent use strongly consistent ownership or single-writer routing per user.
- Model deployment manifests use controlled global rollout.

### 16.4 Region failure

- Global traffic manager stops routing to the unhealthy region.
- A secondary region serves users if policy permits.
- If personalized user features have not replicated, use session/cohort/trending fallback.
- Regional event collectors buffer locally in Kafka and replicate after recovery.
- Safety/eligibility data has high-priority replication; if unavailable, fail closed on uncertain content.

### 16.5 Hot items and hot keys

A viral item creates read skew:

- Cache immutable item features locally.
- Replicate hot item partitions.
- Salt high-write counter keys and merge partial aggregates.
- Stream processors use two-stage aggregation: local partial counts then global merge.
- Do not synchronously update one database row for every impression.

---

## 17. Reliability, Degradation, and Failure Handling

### 17.1 Dependency failure matrix

| Failure | Behavior |
|---|---|
| One candidate source times out | Continue with other sources and redistribute quota |
| Collaborative ANN unavailable | Use content, follows, and trending |
| Online user features unavailable | Use session and cohort features; mark fallback mode |
| Item feature batch partly missing | Drop missing candidates or use explicitly defined safe defaults |
| Main ranker unavailable | Use pre-ranker or cached safe list |
| Re-ranker unavailable | Apply deterministic minimum hard rules; never skip eligibility |
| Eligibility service unavailable | Use fresh local replicated block state; fail closed for uncertain candidates |
| Kafka unavailable | Event collector buffers only within strict limits, applies backpressure, and never falsely acknowledges undurable events |
| Stream processor delayed | Serve batch features and expose staleness; trending becomes less fresh |
| Data lake/training outage | Online serving continues with current models |
| Model deployment is bad | Stop rollout and atomically roll back manifest |
| Cache unavailable | Bypass it with rate-limited direct serving; autoscale dependencies |

### 17.2 Timeouts, retries, and circuit breakers

- Propagate a request deadline through every call.
- Use small per-source timeouts.
- Retry only idempotent reads and only when budget remains.
- Add exponential backoff and jitter outside latency-critical paths.
- Circuit-break unhealthy sources to avoid retry storms.
- Use bounded queues and load shedding; never allow unbounded in-memory work.

### 17.3 Graceful fallback hierarchy

```text
Fully personalized ranking
  -> personalized cached list after fresh eligibility filtering
  -> session/content-based ranking
  -> cohort/regional/category trending
  -> globally popular safe editorial list
```

Every fallback is labeled in logs and metrics. A fallback should be safe and useful, not success-shaped empty output.

### 17.4 Idempotency and reconciliation

- Event ingestion deduplicates by `event_id` within practical windows.
- Feature materializations are reproducible from Kafka/data lake.
- Model/index deployment uses immutable versioned artifacts and atomic pointers.
- Periodic jobs compare online feature snapshots, eligibility state, ANN index membership, and source-of-truth catalog.
- Rebuild derived state rather than manually repairing opaque values.

### 17.5 Disaster recovery

Example objectives:

| Asset | RPO | RTO |
|---|---:|---:|
| Recommendation serving | Near zero via multi-region | < 5 minutes |
| Preferences/consent | < 1 minute | < 15 minutes |
| Raw events | < 5 minutes | < 1 hour |
| Online features | Rebuildable; < 15 minutes replication | < 1 hour |
| Models/indexes | Zero for published immutable versions | < 15 minutes |

Regularly test regional failover, model rollback, Kafka replay, and online feature rebuild.

---

## 18. Privacy, Security, Safety, and Abuse

### 18.1 Privacy

- Collect only signals needed for declared recommendation purposes.
- Honor personalization opt-out and consent changes quickly.
- Separate identity from analytical event data using pseudonymous IDs.
- Encrypt in transit and at rest.
- Enforce least-privilege access to raw behavior and features.
- Audit feature/dataset/model access.
- Apply retention and regional residency policies.
- Support access, export, reset, and deletion requests.
- Prevent deleted users from reappearing in later training snapshots.

### 18.2 API and event security

- Authenticate users and internal services.
- Sign tracking tokens to prevent fake attribution.
- Validate schemas, enum ranges, timestamps, batch sizes, and item IDs.
- Rate-limit by account, device, IP risk, and client application.
- Detect replayed or impossible event sequences.
- Do not trust client-provided watch duration without server/playback corroboration when stakes are high.

### 18.3 Manipulation and fraud

Attackers may buy clicks, create bot watch farms, or coordinate engagement to enter trending lists.

Mitigations:

- Fraud-risk features and bot classifiers.
- Velocity and graph-anomaly detection.
- Down-weight untrusted/new accounts.
- Use unique qualified viewers, completion, dwell, and satisfaction rather than raw counts.
- Delay or quarantine suspicious trend spikes.
- Separate organic quality score from paid promotion.
- Keep fraud labels out of publicly exposed reasons.

### 18.4 Content safety

- Moderation precedes eligibility.
- Hard blocks override all model scores and caches.
- Age and region policies are centrally managed and versioned.
- Maintain a locally available deny set for emergency revocation.
- Audit why a restricted item was included or filtered.

### 18.5 Fairness

Fairness depends on the domain:

- User fairness: comparable recommendation quality across languages, geographies, devices, and accessibility needs.
- Provider fairness: avoid permanently starving new or small creators/sellers.
- Content fairness: catalog coverage and language/topic representation.

Use measurable exposure and quality guardrails, not a vague "fairness boost." Never relax safety or relevance below an agreed threshold.

---

## 19. Observability and Success Metrics

### 19.1 System metrics

- Request rate, success rate, p50/p95/p99 end-to-end latency.
- Latency and error rate per candidate source, feature store, ranker, and eligibility service.
- Cache hit rate, stale-hit rate, and fill latency.
- Candidates retrieved, deduplicated, filtered, ranked, and returned.
- Fallback rate by reason and region.
- Kafka producer/consumer errors, lag, throughput, and partition skew.
- Feature freshness, missing-feature rate, and online/offline skew.
- Model inference latency, batch size, CPU/GPU utilization, and error rate.
- ANN query latency, recall proxy, index age, and delta-index size.

### 19.2 Product/online metrics

Primary metrics should match product intent:

- Meaningful watch time per active user.
- Completion or qualified-engagement rate.
- Long-term retention and return rate.
- Conversion/revenue for commerce, if applicable.

Guardrail metrics:

- Hide/dislike/report rate.
- Quick-skip or abandonment rate.
- Session satisfaction surveys.
- Diversity and unique creators/topics per session.
- Catalog coverage and long-tail exposure.
- New-item discovery.
- Creator/provider concentration.
- Safety-policy violation rate.
- Latency, crashes, and battery/network cost.

CTR alone is insufficient and can reward clickbait.

### 19.3 Offline model metrics

- Retrieval Recall@K.
- Precision@K.
- NDCG@K.
- Mean Reciprocal Rank where relevant.
- AUC/log loss for predicted outcomes.
- Calibration error.
- Catalog coverage, novelty, serendipity, and intra-list diversity.
- Metrics sliced by region, language, device, user activity, and content age.

High offline NDCG does not guarantee better long-term user outcomes, so online experiments remain necessary.

### 19.4 Logging and tracing

For sampled requests, log:

```text
request_id
user/session pseudonymous key
surface/context
experiment assignments
candidate IDs and sources
filter reason codes
feature/model/index versions
scores at each stage
final rank and reason
fallback mode
latency per stage
```

Avoid logging sensitive raw features indiscriminately. Use trace IDs across orchestrator, candidate services, feature reads, and inference.

### 19.5 Alerts

Alert on SLO symptoms and likely causes:

- p95/p99 latency and error-budget burn.
- Sudden fallback-rate increase.
- Eligibility-filter failure or stale block data.
- Event or stream-processing lag.
- Feature missingness/distribution shift.
- Model score/engagement anomaly.
- Unexpected catalog/creator concentration.
- Experiment guardrail regression.

---

## 20. A/B Testing and Model Evaluation

### 20.1 Stable assignment

Assign at user level so one user consistently sees one variant across devices:

```text
bucket = hash(experiment_id, user_id) % 10,000
```

Anonymous sessions use `session_id`. Experiments must be mutually exclusive when they affect the same ranking layer unless factorial interaction is intentional.

### 20.2 Experiment lifecycle

1. Define hypothesis, primary metric, guardrails, eligible population, duration, and minimum detectable effect.
2. Run an A/A test to validate assignment and instrumentation when needed.
3. Ramp from 1% to larger traffic while checking operational safety.
4. Keep assignments sticky.
5. Measure novelty effects and run long enough for weekly cycles and delayed outcomes.
6. Analyze overall and predefined slices without uncontrolled metric fishing.
7. Ship only when primary metrics improve and guardrails remain acceptable.

### 20.3 Avoiding experiment contamination

- Return and log experiment/model version with every recommendation request.
- Attribute impressions and outcomes using signed tracking tokens.
- Do not allow cache entries from one variant to serve another.
- Keep user assignment stable.
- Account for network effects where creator exposure changes the catalog ecosystem.

### 20.4 Shadow and interleaving

- Shadow traffic validates latency, errors, and score distributions without changing results.
- Interleaving candidates from two rankers can compare ranking preference with less traffic, but attribution and user experience are more complex.
- A/B tests remain the final decision mechanism for product impact.

---

## 21. Important Design Trade-offs

### 21.1 Strong consistency versus freshness and availability

Recommendation scores and aggregates can be eventually consistent. Preferences should provide read-after-write in the user's home region. Safety and eligibility require fresher, conservative behavior.

### 21.2 Accuracy versus latency/cost

A larger neural ranker may improve offline metrics but exceed the latency budget. Options:

- Distill into a smaller online model.
- Reduce candidate count.
- Use GPUs with dynamic batching.
- Precompute expensive embeddings.
- Use a simpler fallback under load.

The best production model maximizes business utility **subject to latency, cost, and reliability**, not just offline accuracy.

### 21.3 Freshness versus caching

Long TTL reduces cost but ignores recent intent. Keep caches short-lived and apply recent-history/hard filters at read time.

### 21.4 Personalization versus privacy

More history and cross-device signals can improve relevance but increase privacy risk. Use purpose limitation, minimization, retention, consent, and a useful non-personalized mode.

### 21.5 Relevance versus diversity/fairness

Strict score sorting can create a repetitive page and starve the catalog. Re-rank with explicit page-level objectives and measure the relevance cost.

### 21.6 Batch versus streaming

Batch is cheaper and reproducible; streaming is fresher but operationally complex. Use streaming only where freshness materially changes recommendations or safety.

### 21.7 Build versus buy

- Managed Kafka, feature stores, warehouses, and model registries reduce operations.
- Custom ranking/retrieval logic is usually a product differentiator.
- Vector database choice depends on index size, filter support, update rate, recall, tail latency, cost, and operational expertise.

---

## 22. How the Design Changes by Domain

The architecture remains similar, but objectives and hard constraints change.

| Domain | Primary signals/objectives | Critical filters |
|---|---|---|
| Video/content | Watch time, completion, satisfaction, retention | Safety, age, region, already seen |
| E-commerce | Click, add-to-cart, purchase, margin, returns | Inventory, delivery region, price, seller status |
| Music | Listen duration, skips, saves, playlist adds | Licensing, explicit-content preference |
| Jobs | Application, recruiter response, long-term match | Location, authorization, salary, qualifications |
| Food/services | Order/booking conversion, repeat use, ETA | Open now, service area, capacity, availability |
| Social feed | Meaningful interaction, connection strength | Privacy, blocks, moderation, freshness |

For e-commerce, inventory and price may need authoritative checks at response and checkout. For jobs, fairness and sensitive-feature controls are especially important. For services, real-time capacity can dominate ranking.

---

## 23. Simulated Interview Follow-up Questions

### Q1. Why do we need both candidate generation and ranking?

> **Candidate:** "The catalog has ten million items, so scoring every user-item pair would require hundreds of billions of scores per second. Candidate generation cheaply finds a few thousand high-recall possibilities. The ranker then spends more computation on a much smaller set to improve precision. The pre-ranker is another cost-latency control between them."

### Q2. Which database would you choose?

> **Candidate:** "There is no single database. PostgreSQL stores strongly consistent preferences and deployment metadata. Kafka durably buffers events. Object storage holds PB-scale raw history and model artifacts. A wide-column or managed feature store handles key-based online features. Redis handles TTL session state and hot values. A vector index serves ANN retrieval. An analytical warehouse supports experiments. Each choice follows an access pattern."

### Q3. How does a click affect recommendations within one minute?

> **Candidate:** "The client sends a signed event to the collector, which publishes it to Kafka. A stream processor updates recent user history, session intent, category affinity, and seen-item state in the online feature store. The next request reads these features and excludes or down-ranks recently consumed items. We do not wait for the next model training run."

### Q4. How do you handle a brand-new user?

> **Candidate:** "Start with onboarding preferences where available, otherwise region/language/category popularity and editorial-safe lists. Capture session behavior immediately and diversify the first page to learn interests. As events accumulate, shift toward session and personalized embeddings. Anonymous users remain useful without requiring identity."

### Q5. How do you handle a new item with no interactions?

> **Candidate:** "Generate content embeddings from metadata and media, place the item in a mutable delta ANN index, retrieve it through content similarity, and allocate bounded exploration traffic after safety checks. Streaming engagement then gives early quality signals. Creator priors can help but are capped to avoid rich-get-richer behavior."

### Q6. How do you prevent duplicates across pages?

> **Candidate:** "Use a short-lived recommendation session. Generate more than one page, store the ordered list and a seen set under a TTL key, and put the session reference and offset in a signed cursor. Reapply hard eligibility and explicit user feedback when serving later pages."

### Q7. What happens if the ranking model goes down?

> **Candidate:** "Circuit-break the model service and use the pre-ranker or a recently cached personalized list after fresh filtering. If user features also fail, fall back to regional/category trending and safe editorial content. I track fallback rate so graceful degradation does not hide an outage."

### Q8. How do you ensure a blocked item is not served from cache?

> **Candidate:** "A moderation event updates regional eligibility/block state and invalidates caches, but invalidation alone is not sufficient. Every cached or freshly ranked result passes a final current eligibility check. If eligibility is uncertain, that item fails closed and we backfill another candidate."

### Q9. How do you choose between collaborative and content-based filtering?

> **Candidate:** "I use both as candidate sources. Collaborative filtering captures behavioral similarity but struggles with sparse and new entities. Content-based retrieval works for new/niche items but can over-specialize. Combining them, plus trending, subscriptions, and exploration, improves recall and resilience."

### Q10. How do you avoid optimizing for clickbait?

> **Candidate:** "Do not optimize raw CTR. Train a multi-task model on meaningful watch duration, completion, explicit satisfaction, negative feedback, and long-term return. Add quality and safety guardrails, calibrate outcomes, and require A/B tests to improve the primary metric without increasing hides, skips, reports, or churn."

### Q11. What is training-serving skew?

> **Candidate:** "It occurs when features differ between training and live inference because code, timing, defaults, or versions diverge. I use a feature registry, shared transformations, point-in-time joins, versioned schemas, sampled online feature logging, and pre-deployment compatibility checks."

### Q12. How frequently would you retrain?

> **Candidate:** "It depends on drift and cost. A reasonable start is daily main-ranker training, several-times-daily or daily embeddings/base indexes, minute-level streaming features, and few-minute delta-index updates for new items. I trigger faster retraining only when measured drift or domain dynamics justify it."

### Q13. Why not precompute every user's feed?

> **Candidate:** "It wastes computation for inactive users, becomes stale after session actions, and cannot incorporate request context well. I precompute expensive reusable pieces such as embeddings, ANN indexes, similar-item lists, and candidate pools, then perform contextual filtering and ranking online. Very active users may get asynchronously prefetched pages."

### Q14. How do you evaluate retrieval quality?

> **Candidate:** "Offline, measure Recall@K: how often a known relevant item appears in the retrieved set. Slice it by source, user activity, language, and item age. In production, log candidate-source coverage and measure downstream contribution through experiments. A ranker cannot recover items missed by retrieval."

### Q15. How do you reduce inference cost?

> **Candidate:** "Reduce and tune candidate budgets, add an inexpensive pre-ranker, batch item scoring per request, cache stable features, precompute embeddings, quantize or distill models, use efficient runtimes, and autoscale by actual inference load. I evaluate cost per thousand recommendations alongside product lift."

### Q16. Would you use exactly-once event processing?

> **Candidate:** "Kafka and stream frameworks can provide strong guarantees inside their boundary, but external feature stores complicate end-to-end exactly-once. I use unique event IDs, idempotent producers, checkpointing, bounded deduplication, deterministic window keys, and periodic batch correction. Recommendation aggregates generally tolerate rare corrected duplicates."

### Q17. How do you handle model drift?

> **Candidate:** "Monitor input-feature distributions, missingness, prediction distributions, calibration, and outcome metrics against training/reference windows. Slice by region and cohort. Drift can trigger investigation or retraining, but rollout still goes through offline validation, shadow/canary, and rollback controls."

### Q18. How do you support user-provided filters without hurting latency?

> **Candidate:** "Normalize filters into a bounded schema and filter hash. Push coarse filters such as language/region/category into candidate indexes where supported, apply fast metadata bitsets before ranking, and include filter dimensions in cache keys. I do not create arbitrary database queries from filter expressions."

### Q19. What is the source of truth for whether an item is playable?

> **Candidate:** "The catalog/entitlement domain owns the source of truth. The recommender consumes a low-latency replicated eligibility projection for serving, performs a final check, and the playback service independently enforces entitlement. Search/vector indexes and recommendation caches are never authoritative."

### Q20. What would you build first?

> **Candidate:** "Phase one: event instrumentation, safe popular/category candidates, explicit filters, and deterministic ranking. Phase two: offline data lake, collaborative/content retrieval, feature store, and learned ranker. Phase three: streaming features, multi-objective ranking, exploration, sophisticated re-ranking, and global optimization. Correct events and experiments come before model complexity."

---

## 24. Common Mistakes in This Interview

1. Starting with "use collaborative filtering" without defining product goals or system flow.
2. Scoring the entire catalog on every request.
3. Using one database for events, features, vectors, metadata, and analytics.
4. Ignoring impressions and training only from clicks.
5. Treating ranking as simple score sorting and ignoring page diversity.
6. Forgetting new users and new items.
7. Applying moderation filters only during candidate generation, allowing stale cache/index data through.
8. Giving every dependency the full 200 ms latency budget instead of propagating deadlines.
9. Claiming exactly-once without describing boundaries and idempotency.
10. Optimizing CTR without long-term satisfaction and negative-signal guardrails.
11. Ignoring feature/model/index version compatibility.
12. Recommending a complex deep model without discussing inference cost and fallback.
13. Forgetting user privacy, consent, deletion, and data retention.
14. Saying "A/B test it" without stable assignment and impression attribution.
15. Not explaining what happens when feature stores, candidate sources, or models fail.

---

## 25. What I Would Draw on the Whiteboard

Draw in this order:

1. **Two top-level paths:** online serving and event/offline learning.
2. **Online funnel:** candidate sources -> merge/filter -> pre-rank -> rank -> re-rank -> final eligibility.
3. **Data stores:** feature store, vector index, Redis, relational preferences, Kafka, data lake.
4. **Feedback loop:** client impression/interaction -> Kafka -> streaming features and data lake.
5. **Training loop:** data lake -> features -> training/evaluation -> model registry -> serving.
6. **Cross-cutting labels:** experiment assignment, model/version lineage, hard safety filters, cache, fallback.

The most important numbers to write beside the funnel:

```text
10M items -> 2,000 candidates -> 300 ranked -> 20 returned
50K peak recommendation QPS
700K peak events/sec
p95 < 200 ms
```

---

## 26. Five-Minute Final Answer

> **Candidate:** "I designed a personalized home-feed recommendation system for 100 million daily users, ten million videos, 50,000 peak recommendation QPS, and roughly 700,000 peak behavior events per second. The API returns 20 ranked items under a 200 ms p95 target and accepts signed impression and engagement events asynchronously."
>
> "The online system uses a multi-stage funnel because scoring the entire catalog is infeasible. Parallel candidate sources retrieve around 2,000 items using collaborative embeddings, content similarity, recent-item similarity, followed creators, trending, and controlled exploration. We merge, deduplicate, and apply cheap hard eligibility filters. A pre-ranker reduces the pool to about 300, the main multi-task ranker predicts meaningful outcomes such as watch time, completion, satisfaction, and negative feedback, and a final re-ranker adds diversity, freshness, creator fairness, and exploration. A final authoritative eligibility projection prevents blocked, unavailable, age-restricted, or region-ineligible content from escaping stale caches or indexes."
>
> "For storage, PostgreSQL owns preferences, consent, and deployment metadata; Kafka durably buffers events; stream processing updates near-real-time features, history, and trending lists; a wide-column/managed feature store serves online features; Redis stores hot session and feed state; a vector index performs ANN retrieval; and object storage plus an analytical warehouse hold raw history, training datasets, models, and experiment data. These stores are chosen by access pattern rather than forcing every workload into one database."
>
> "Offline pipelines create point-in-time-correct labels and features, train retrieval and ranking models, validate them, and publish immutable artifacts through a model registry. Deployment pins compatible model, feature, embedding, and ANN-index versions, then progresses through shadow, canary, and A/B stages with immediate rollback. Shared feature definitions and sampled online-feature logging prevent training-serving skew."
>
> "The design handles cold start with onboarding/cohort/session signals for users and content embeddings plus bounded exploration for new items. It degrades from fully personalized results to cached personalized, session-based, cohort trending, and finally a safe editorial list. Hard safety filters never degrade open. Success is measured by meaningful watch time and long-term retention with latency, hide/report rate, diversity, catalog coverage, creator concentration, privacy, and safety as guardrails."

---

## 27. Interview Cheat Sheet

### Opening

```text
Clarify: surface, objective, scale/latency, freshness/filtering.
Design: requirements -> estimates -> APIs/data -> architecture ->
candidate generation -> ranking -> feedback/training -> reliability/metrics.
```

### Core numbers

```text
100M DAU
10M items
1B rec requests/day
50K peak rec QPS
20B events/day
~700K peak events/sec
10M -> 2K -> 300 -> 20
p95 < 200 ms
```

### Core online flow

```text
Request
-> identity/context/experiments
-> parallel candidate retrieval
-> merge + dedup
-> hard pre-filter
-> pre-rank
-> feature hydration
-> main rank
-> diversity/fairness/exploration re-rank
-> final eligibility
-> response/tracking
```

### Core feedback flow

```text
Impression/click/watch/hide
-> Event Collector
-> Kafka
-> stream features + trending + recent history
-> data lake
-> training/evaluation
-> model registry
-> shadow/canary/A-B
-> serving
```

### Database answer

```text
PostgreSQL: preferences, consent, deployments
Kafka: durable events and replay
Object storage: raw history, training data, models
Wide-column/feature store: online features
Redis: hot/session/feed TTL state
Vector index: ANN candidate retrieval
Warehouse: analytics and experiments
Search index: metadata/content retrieval if required
```

### Must mention

- Impressions as well as clicks.
- Multi-stage retrieval and ranking.
- Streaming plus batch features.
- Point-in-time-correct training.
- Model/feature/index version compatibility.
- Final hard eligibility check.
- Cold start and exploration.
- Diversity and feedback-loop prevention.
- Safe fallback hierarchy.
- Stable A/B assignment and guardrails.
- Privacy, consent, deletion, and fraud.

### Closing sentence

> "The key trade-off is maximizing long-term user value while staying within latency, cost, safety, privacy, diversity, and ecosystem-health constraints; the most accurate offline model is not automatically the best production recommender."
