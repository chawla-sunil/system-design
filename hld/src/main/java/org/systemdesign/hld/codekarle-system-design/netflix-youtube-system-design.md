# Designing a Video Streaming Platform (Netflix / YouTube)

This folder is made from the summary of the following sources:
- [Github Design Images](https://github.com/codekarle/system-design/blob/master/system-design-prep-material/architecture-diagrams/Video%20Streaming%20Platform.png)
- [Blog](https://www.codekarle.com/system-design/netflix-system-design.html)
- [YouTube](https://www.youtube.com/watch?v=lYoSd2WCJTo&list=PLhgw50vUymycJPN6ZbGTpVKAJ0cL4OEH3&index=5)

Last-minute interview revision notes.

---

## 1. Requirements

### Functional
1. **Production houses** (or any user) can **upload videos**.
2. Users get a **home page** with recommendations.
3. Users can **search** for videos and **play** them.
4. Must work on **all devices** — laptop, phone, TV, smartwatch, etc.

### Non-Functional
- **No buffering** → low latency + high availability.
- **Maximize session time** → good UX + strong recommendations.
- **Support every device** → every combination of format × dimension × bandwidth.

### The "Format Explosion" Problem
```
Formats (I)  ×  Dimensions/Resolutions (J)  ×  Bandwidths (K)  =  I × J × K variants per video
```
Format => mp4, avi, mkv, webm, etc.
Dimension => 1080p, 720p, 480p, 360p, etc.
Bandwidth => 5 Mbps, 3 Mbps, 1 Mbps, 500 Kbps

Every raw upload must be transcoded into `I×J×K` chunk versions to cover all clients.

### Three Actors
| Actor | Meaning |
|---|---|
| **Client** | The device (browser/TV/phone) — has smart player logic. |
| **User** | The human watching. |
| **Production House** | Anyone uploading (individuals or studios). |

---

## 2. The Client — Adaptive Bitrate Streaming (ABR)

Videos are **never downloaded fully**. Client requests **chunks** while playing previous chunks.

1. Client detects device capabilities → picks best supported format.
2. Requests high-quality chunks first.
3. If subsequent chunks arrive slowly → drops to lower quality (e.g., 1080p → 480p).
4. If bandwidth recovers → moves back to high quality.

Goal: **maximize quality without buffering**.

---

## 3. High-Level Architecture

**Color convention:** 🟢 clients/UI · ⚫ LB + auth · 🔵 our services · 🔴 datastores / infra.

### 3.1 Production House / Upload Flow

```
Production UI ──► Asset Onboarding Service ──► pulls from SFTP/S3
                          │
                          ├──► Amazon S3 (raw video, source of truth)
                          │
                          └──► Kafka (event: "new asset uploaded")
                                   │
                                   ▼
                          Content Processor (workflow engine)
                                   │
                                   ▼
                          File Chunker
                                   │
                                   ▼
                          Content Filter (piracy, nudity, banned content)
                                   │
                                   ▼
                          Content Tagger (auto-tags + generates thumbnails)
                                   │
                                   ▼
                          Transcoder (raw → MP4/AVI/MKV/...)
                                   │
                                   ▼
                          Quality Converter (each format → multiple bitrates)
                                   │
                                   ▼
                          Upload chunks → CDNs
                                   │
                                   ▼
                          Kafka events → Asset Service
                                                  │
                                                  ├──► Cassandra (all chunk metadata)
                                                  └──► Notification Service → producer notified
```

### Key upload facts
- Raw movies are 20–40 GB → uploaded via **SFTP URL**, not browser upload.
- **S3** = source of truth for raw files. **Cassandra** = metadata store.
- Everything is a **chunk** — enables parallel processing at every stage.
- Each stage publishes to Kafka → next stage picks up → fully async pipeline.
- Fan-out: 1 raw video → N chunks → (N × formats) → (N × formats × bitrates) chunks.

### 3.2 User / Playback Flow

```
User Device ──► LB + Auth ──► User Device Login Flow ──► User Service ──► MySQL
                                                              │            └──► Redis (user cache)
                                                              └──► Kafka (device fingerprint, geo)

User Device ──► Home Page Service    ──┐
User Device ──► Search Service       ──┼──► results
User Device ──► Analytics Service    ──┘        │
                                                 ▼
                                              Kafka (user activity)

Play button ──► Host Identity Service ──► Asset Service (Cassandra)
                        │                 (returns best CDN by geo)
                        ▼
              Main CDN   OR   Local (Geo-optimized) CDN
                        │
                        ▼
              Chunks stream to client
                        │
                        └──► Stream Stats Logger ──► Kafka (watch %, ratings proxy)
```

### 3.3 Search Path

```
Kafka (new asset event) ──► Search Consumer ──► Elasticsearch (fuzzy + type-ahead)

User query ──► Search Service ──► Elasticsearch
                    │
                    └──► User Service (for age/geo filtering)
                    ▼
              filtered results ──► UI
```

Why **Elasticsearch**: fuzzy search (typos in "Pirates of the Caribbean"), auto-suggest, multi-field search (title, cast, crew).

---

## 4. Data Store Choices

| Store | Purpose | Why |
|---|---|---|
| **Amazon S3** | Raw + processed video files | Cheap blob storage, source of truth. |
| **Cassandra** | Asset metadata, chunks, user classifications | Massive read+write, no master, scales horizontally, great for **partition-key** lookups (movie_id, publisher_id). Bad at random queries & aggregation — we don't do those here. |
| **MySQL** | User records | Structured, needs consistency for account/subscription. |
| **Redis** | User cache | High-read cache for user info called by many services. |
| **Elasticsearch** | Search index | Fuzzy search, type-ahead, multi-field. |
| **Kafka** | Event backbone | Async pipeline glue. |
| **Hadoop + Spark** | Analytics & ML | Aggregations, recommendation models. |

---

## 5. CDN Strategy

### Main CDNs vs Local CDNs
- **Main CDNs**: few, hold everything.
- **Local (Geo) CDNs**: many, per-country/region, hold only the videos likely to be watched there.

### Traffic Predictor + CDN Writer
An ML model predicts what each region will watch tomorrow (based on historical views + new launches, e.g., a German series → cache in Germany).
```
Traffic Predictor ──► Kafka event ("LC1 needs C1..C6")
                          │
                          ▼
                    CDN Writer ──► Asset Service (what's already there?)
                          │
                          └──► delta: add C4-C6, remove C10-C11
```

### Torrent-style CDN Distribution (avoid S3 bottleneck)
Instead of every local CDN pulling all chunks from S3:
1. S3 sends **different subsets** of chunks to different local CDNs.
2. Local CDNs then **share amongst themselves** (peer-to-peer).
3. Run during **off-peak hours per region** (3 am local time).

```
       S3 / Main CDN
       /  |  |  |  \
     C1-5 C6-10 C11-15 C16-20 C21-25
      │    │     │      │      │
     LC1  LC2   LC3    LC4    LC5
      └────┴─────┴──────┴──────┘
        peer-to-peer exchange
```

### Netflix Open Connect
Netflix gives ISPs a **free hardware appliance (Open Connect Appliance)** that caches Netflix content inside the ISP's network.
- **ISP wins**: saves bandwidth costs.
- **Netflix wins**: lower CDN load, cost-neutral (saves elsewhere).
- **User wins**: fewer hops = lower latency, higher availability.

---

## 6. Analytics & ML on Kafka + Hadoop + Spark

Every event flows to Kafka → Spark → Hadoop for offline crunching.

### 6.1 Tag Aggregation
Content Tagger emits per-chunk tags → Spark Streaming does `GROUP BY movie_id, tag → COUNT → TOP 10` → sends best tags back via Kafka → Asset Service stores in Cassandra.

### 6.2 Best Thumbnail (A/B testing → ML)
- Serve random thumbnail variants to users → Analytics captures clicks.
- Simple version: find single best thumbnail per movie.
- Advanced: **ML model** picks best thumbnail **per user profile**.

### 6.3 Rating Proxy
No explicit rating? Use **watch percentage** as proxy (10 sec → dislike; 90% → like). Convert to 1–5.

### 6.4 User Classification (Genres)
Watch history + search history → classify user into genres (action, comedy, etc.) → store in Cassandra.

### 6.5 Recommendation Engine
- **Genre-based**: user likes action → show top action movies.
- **Collaborative filtering** (e.g., **ALS – Alternating Least Squares**): if U1 & U2 like {A,B,C} and U3 likes {B,C}, recommend A to U3.

### 6.6 Home Page & Search Quality Feedback
- Analytics tracks: are users clicking page-1 results, or scrolling to page 2/3?
- If lots of page-2 clicks → home/search algorithm needs tuning.

### 6.7 Account Sharing Detection
Login events (device fingerprint + geo) → Spark → detect accounts logged in from many devices/geos → flag credential sharing.

---

## 7. Full Architecture Overview

```
                                ┌─────────────────────────────────────────────┐
                                │                   KAFKA                     │
                                │        (central async event backbone)       │
                                └─────────────────────────────────────────────┘
                                    ▲    ▲    ▲    ▲    ▲    ▲    ▲    ▲
                                    │    │    │    │    │    │    │    │
   PRODUCTION HOUSE PATH            │    │    │    │    │    │    │    │
   Prod UI                          │    │    │    │    │    │    │    │
     │                              │    │    │    │    │    │    │    │
     ▼                              │    │    │    │    │    │    │    │
   Asset Onboarding ────────────────┘    │    │    │    │    │    │    │
     │                                    │    │    │    │    │    │    │
     ▼                                    │    │    │    │    │    │    │
   S3 (raw)  →  Content Processor ────────┴────┘    │    │    │    │    │
                (chunker→filter→tagger→               │    │    │    │    │
                 transcoder→quality)                   │    │    │    │    │
                        │                              │    │    │    │    │
                        ▼                              │    │    │    │    │
                  Main + Local CDNs ─────► Asset Service (Cassandra)
                                                                       ▲
   USER PATH                                                           │
   User Device ──► LB+Auth ──► User Service (MySQL+Redis) ─────────────┘
        │                          │
        │                          └──► Kafka (login/device)
        │
        ├──► Home Page Service ──► reads from Cassandra
        ├──► Search Service   ──► Elasticsearch  ←── Search Consumer ←── Kafka
        ├──► Analytics Service ──► Kafka
        ├──► Host Identity Service ──► Asset Service → returns best CDN
        │                                    │
        │                                    ▼
        └──► Play chunks from CDN ──► Stream Stats Logger ──► Kafka

   ANALYTICS PATH
   Kafka ──► Spark Streaming ──► Hadoop
                                   │
                                   ├──► Tag aggregation → Cassandra
                                   ├──► Thumbnail A/B + ML → Cassandra
                                   ├──► User classification → Cassandra
                                   ├──► Recommendation (ALS)  → Cassandra
                                   ├──► Traffic Predictor ──► Kafka ──► CDN Writer → Local CDNs
                                   └──► Account sharing / usage reports
```

All services are **horizontally scalable** — add more nodes under load.

---

## 8. Interview Talking Points (Cheat Sheet)

- **Chunk early**: every upload split into chunks → parallelize filter, tag, transcode, quality-convert.
- **Format explosion**: `I × J × K` variants per movie; handled by parallel workers.
- **Kafka** is the glue — decouples every stage, enables analytics on the same events.
- **Adaptive Bitrate Streaming** = client-side quality selection based on runtime bandwidth.
- **Cassandra** for metadata (partition-key queries), **Elasticsearch** for fuzzy search, **S3** for blobs, **MySQL** for users, **Redis** for user cache.
- **Two-tier CDNs**: main (all content) + geo local (predicted content). Populated via **Traffic Predictor** and distributed **torrent-style** during off-peak hours.
- **Netflix Open Connect**: appliance inside ISPs — win/win/win.
- **Recommendations**: genre-based + collaborative filtering (ALS) on Spark/Hadoop.
- **Rating proxy** = watch percentage. **Thumbnail selection** = A/B → per-user ML.
- **No single point of failure**: replicate S3 → main CDNs → local CDNs → peer-to-peer.
