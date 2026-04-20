# HLD: Chat Application (WhatsApp / Telegram / Messenger)

> **Interview Simulation** — A structured 1-hour System Design walkthrough.

---

## Table of Contents

1. [Step 1 — Clarifying Requirements (5 min)](#step-1--clarifying-requirements)
2. [Step 2 — Capacity Estimation (5 min)](#step-2--capacity-estimation)
3. [Step 3 — High-Level API Design (5 min)](#step-3--high-level-api-design)
4. [Step 4 — Data Model & Storage (10 min)](#step-4--data-model--storage)
5. [Step 5 — High-Level Architecture (15 min)](#step-5--high-level-architecture)
6. [Step 6 — Deep Dives (15 min)](#step-6--deep-dives)
7. [Step 7 — Scalability, Reliability & Trade-offs (5 min)](#step-7--scalability-reliability--trade-offs)

---

## Step 1 — Clarifying Requirements

> **"Before I start designing, let me clarify the scope with the interviewer."**

### Functional Requirements (Must-Have)

| # | Feature | Notes |
|---|---------|-------|
| F1 | **1:1 Chat** | Real-time text messaging between two users |
| F2 | **Group Chat** | Up to 256 members (WhatsApp-like) |
| F3 | **Online / Last-Seen Status** | Presence indicator |
| F4 | **Message Delivery Receipts** | Sent ✓, Delivered ✓✓, Read (blue ✓✓) |
| F5 | **Push Notifications** | When receiver is offline |
| F6 | **Media Sharing** | Images, videos, documents |
| F7 | **Chat History / Persistence** | Messages stored and retrievable |

### Non-Functional Requirements

| # | Requirement | Target |
|---|-------------|--------|
| NF1 | **Low Latency** | Message delivery < 200 ms (same region) |
| NF2 | **High Availability** | 99.99% uptime |
| NF3 | **Consistency** | Eventual consistency is acceptable; ordering must be preserved per conversation |
| NF4 | **Scale** | 500M DAU, 50B messages/day (WhatsApp scale) |
| NF5 | **Durability** | No message loss |

### Out of Scope (mention explicitly)

- End-to-end encryption (can discuss briefly)
- Voice / Video calls
- Stories / Status feature
- Payment integration

---

## Step 2 — Capacity Estimation

> **"Let me do a quick back-of-the-envelope calculation."**

### Traffic

| Metric | Value |
|--------|-------|
| DAU | 500 M |
| Messages/day | 50 B |
| Messages/sec (avg) | 50B / 86400 ≈ **~580K msg/sec** |
| Peak (3x) | **~1.7M msg/sec** |

### Storage

| Item | Calculation |
|------|-------------|
| Avg message size | ~100 bytes (text) |
| Daily text storage | 50B × 100B = **5 TB/day** |
| Yearly text storage | 5 TB × 365 = **~1.8 PB/year** |
| Media (20% messages have media, avg 200KB) | 50B × 0.2 × 200KB = **2 PB/day** |

### Bandwidth

| Direction | Calculation |
|-----------|-------------|
| Ingress (text) | 580K × 100B = **~58 MB/s** |
| Egress (text, fan-out ~2x for 1:1) | **~116 MB/s** |
| Media bandwidth | Orders of magnitude higher; served via CDN |

### Connection

| Metric | Value |
|--------|-------|
| Concurrent WebSocket connections | ~500M (1 per online user) |
| Each connection ~10KB memory | **~5 TB RAM** just for connections |
| → Need thousands of chat servers | e.g., 10K servers × 50K connections each |

---

## Step 3 — High-Level API Design

### 1. WebSocket-Based Real-Time APIs

```
// Client opens a persistent WebSocket connection
ws://chat.example.com/ws?token=<auth_token>
```

**Client → Server messages (over WebSocket):**

```json
// Send message
{
  "type": "SEND_MESSAGE",
  "conversationId": "conv_123",
  "content": "Hello!",
  "contentType": "TEXT",
  "clientMsgId": "uuid-abc-123",   // idempotency key
  "timestamp": 1713600000000
}

// Typing indicator
{
  "type": "TYPING",
  "conversationId": "conv_123"
}

// Read receipt
{
  "type": "READ_RECEIPT",
  "conversationId": "conv_123",
  "lastReadMsgId": "msg_456"
}
```

**Server → Client messages (over WebSocket):**

```json
// New message
{
  "type": "NEW_MESSAGE",
  "messageId": "msg_789",
  "conversationId": "conv_123",
  "senderId": "user_A",
  "content": "Hello!",
  "contentType": "TEXT",
  "timestamp": 1713600000000
}

// Delivery ACK
{
  "type": "ACK",
  "clientMsgId": "uuid-abc-123",
  "messageId": "msg_789",
  "status": "DELIVERED"
}
```

### 2. REST APIs (for non-real-time operations)

```
POST   /api/v1/users/signup
POST   /api/v1/users/login
GET    /api/v1/conversations                         // list conversations
GET    /api/v1/conversations/{id}/messages?before=<cursor>&limit=50  // paginated history
POST   /api/v1/media/upload                          // returns mediaUrl
PUT    /api/v1/users/{id}/profile
```

---

## Step 4 — Data Model & Storage

### Storage Choices

| Data | Store | Why |
|------|-------|-----|
| **User profiles** | MySQL / PostgreSQL | Relational, low volume, strong consistency |
| **Messages** | Cassandra / HBase | Write-heavy, time-series, horizontally scalable |
| **Conversations / Group metadata** | MySQL / PostgreSQL | Relational queries (members, admin, etc.) |
| **Media files** | Object Store (S3) + CDN | Blob storage, globally distributed |
| **User presence / online status** | Redis | In-memory, TTL-based, fast reads |
| **Undelivered message queue** | Kafka | Durable, ordered, replayable |

### Key Tables

#### Users Table (PostgreSQL)

```sql
users (
  user_id        UUID PRIMARY KEY,
  username       VARCHAR(50) UNIQUE,
  phone_number   VARCHAR(15) UNIQUE,
  display_name   VARCHAR(100),
  profile_pic_url TEXT,
  created_at     TIMESTAMP
)
```

#### Conversations Table (PostgreSQL)

```sql
conversations (
  conversation_id  UUID PRIMARY KEY,
  type             ENUM('ONE_TO_ONE', 'GROUP'),
  group_name       VARCHAR(100),       -- NULL for 1:1
  created_by       UUID REFERENCES users,
  created_at       TIMESTAMP
)

conversation_members (
  conversation_id  UUID REFERENCES conversations,
  user_id          UUID REFERENCES users,
  role             ENUM('ADMIN', 'MEMBER'),
  joined_at        TIMESTAMP,
  PRIMARY KEY (conversation_id, user_id)
)
```

#### Messages Table (Cassandra)

```sql
-- Partition Key: conversation_id
-- Clustering Key: message_id (TimeUUID → sorted by time)

CREATE TABLE messages (
  conversation_id  UUID,
  message_id       TIMEUUID,
  sender_id        UUID,
  content          TEXT,
  content_type     TEXT,       -- TEXT, IMAGE, VIDEO, DOCUMENT
  media_url        TEXT,
  status           TEXT,       -- SENT, DELIVERED, READ
  created_at       TIMESTAMP,
  PRIMARY KEY (conversation_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

> **Why Cassandra?**
> - Partition by `conversation_id` → all messages in a chat are co-located
> - Clustering by `message_id` (TimeUUID) → naturally sorted by time
> - Handles massive write throughput (580K+ writes/sec)
> - Horizontal scaling via consistent hashing

---

## Step 5 — High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                     │
│          (Mobile App / Web App / Desktop App)                            │
└──────────┬───────────────────────────┬───────────────────────────────────┘
           │ WebSocket                 │ HTTPS (REST)
           ▼                           ▼
┌─────────────────────┐    ┌─────────────────────────┐
│   Load Balancer      │    │    API Gateway           │
│   (L4 - TCP level)   │    │  (Auth, Rate Limit,      │
│   (sticky sessions   │    │   Routing)               │
│    by user_id)       │    │                          │
└─────────┬───────────┘    └──────────┬──────────────┘
          │                            │
          ▼                            ▼
┌─────────────────────┐    ┌─────────────────────────┐
│  Chat Servers        │    │  REST API Servers        │
│  (WebSocket Handlers)│    │  (History, Profile,      │
│  - Stateful          │    │   Upload, Auth)          │
│  - 10K+ instances    │    │  - Stateless             │
│  - Each holds ~50K   │    └──────────┬──────────────┘
│    connections        │               │
└────┬───────┬─────────┘               │
     │       │                          │
     │       │    ┌─────────────────────┘
     │       │    │
     ▼       ▼    ▼
┌───────────────────────────────────────────────────────┐
│              Message Service / Router                   │
│  - Determines which chat server holds the receiver     │
│  - Looks up "User → Chat Server" mapping from Redis    │
│  - Routes message to correct chat server               │
│  - If user offline → pushes to Kafka for persistence   │
│    + triggers Push Notification                        │
└───┬──────────┬──────────┬──────────┬──────────────────┘
    │          │          │          │
    ▼          ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌──────────────┐
│ Redis  │ │Cassandra│ │ Kafka  │ │ Notification │
│Cluster │ │ Cluster │ │Cluster │ │  Service     │
│        │ │         │ │        │ │  (FCM/APNs)  │
│- User  │ │- Message│ │- Async │ └──────────────┘
│  session│ │  store  │ │  queue │
│  mapping│ │         │ │  for   │
│- Presence│ │        │ │  offline│
│- Last  │ │         │ │  msgs  │
│  seen  │ │         │ │        │
└────────┘ └────────┘ └────────┘

    ┌──────────────────────────────────────┐
    │         Media / File Service          │
    │  - Upload to S3 / Blob Storage       │
    │  - Generate thumbnail                │
    │  - Return CDN URL                    │
    │  - CDN distributes globally          │
    └──────────────────────────────────────┘
```

### Message Flow — 1:1 Chat (User A sends to User B)

```
User A                Chat Server 1        Message Service       Redis           Chat Server 2        User B
  │                        │                     │                  │                  │                 │
  │── SEND_MESSAGE ───────►│                     │                  │                  │                 │
  │   (via WebSocket)      │                     │                  │                  │                 │
  │                        │── route(msg) ──────►│                  │                  │                 │
  │                        │                     │── lookup B ─────►│                  │                 │
  │                        │                     │◄── B@Server2 ────│                  │                 │
  │                        │                     │                  │                  │                 │
  │                        │                     │── write to ──────────► Cassandra    │                 │
  │                        │                     │   (async)        │                  │                 │
  │                        │                     │                  │                  │                 │
  │                        │                     │── forward msg ──────────────────────►│                 │
  │                        │                     │                  │                  │── NEW_MSG ─────►│
  │                        │                     │                  │                  │                 │
  │                        │                     │                  │                  │◄── DELIVERED ───│
  │                        │                     │◄── delivery ack ─────────────────────│                 │
  │◄── ACK(DELIVERED) ─────│◄── ack ─────────────│                  │                  │                 │
  │                        │                     │                  │                  │                 │
```

### Message Flow — User B is OFFLINE

```
Message Service
  │── lookup B in Redis ──► NOT FOUND (offline)
  │
  │── write message to Cassandra (status=SENT)
  │── publish to Kafka (offline_messages topic)
  │
  │── Notification Service consumes from Kafka
  │   └── Send push notification via FCM / APNs
  │
  │ ... later, User B comes online ...
  │
  User B connects WebSocket → Chat Server 3
  Chat Server 3 ── pull undelivered msgs from Cassandra ──► deliver to User B
  Chat Server 3 ── update status to DELIVERED
  Chat Server 3 ── send delivery receipt back to User A (if online)
```

### Group Message Flow

```
User A sends to Group G (100 members)
  │
  Message Service:
  │── Write message to Cassandra (partition: group_conv_id)
  │── Fetch group members from DB / cache
  │── For EACH member:
  │     ├── Lookup member's chat server in Redis
  │     ├── If ONLINE  → forward message to their chat server
  │     └── If OFFLINE → enqueue in Kafka → push notification
  │
  (Fan-out on write for small groups ≤256)
```

---

## Step 6 — Deep Dives

### 6.1 — How Do We Handle WebSocket Connection Management?

**Problem:** 500M concurrent connections across thousands of servers. How does the system know which server holds User B's connection?

**Solution: Session Registry in Redis**

```
Key:   session:{user_id}
Value: { "chatServerId": "cs-2847", "connectedAt": 1713600000 }
TTL:   Heartbeat-refreshed (e.g., 60s TTL, client sends heartbeat every 30s)
```

- When a user connects via WebSocket → Chat Server registers in Redis
- When a user disconnects → entry removed (or expires via TTL)
- Message Service looks up this registry to route messages

**Handling Reconnection:**
- Client maintains a local `lastReceivedMsgId`
- On reconnect, sends `SYNC` request with `lastReceivedMsgId`
- Server fetches all newer messages from Cassandra and delivers

### 6.2 — Message Ordering

**Problem:** Messages must appear in order within a conversation.

**Solution:**
- **Cassandra TimeUUID** as `message_id` → globally unique + time-ordered
- Server assigns the `message_id` (not client) → single source of truth for ordering
- Within a partition (conversation), Cassandra clustering order guarantees sort
- Client sorts by `message_id` for display

**Edge case — concurrent messages from 2 users:**
- Both get different TimeUUIDs → deterministic ordering
- "Last writer wins" display — both users see same order

### 6.3 — Delivery Receipts & Read Receipts

```
State Machine per message:

  SENT (server received) ──► DELIVERED (receiver's device got it) ──► READ (receiver opened chat)
   ✓                              ✓✓                                    ✓✓ (blue)
```

**Implementation:**
1. **SENT**: Server ACKs sender immediately after persisting
2. **DELIVERED**: Receiver's chat server ACKs after pushing to device → Message Service updates status → notifies sender
3. **READ**: Receiver explicitly sends `READ_RECEIPT` when they open the conversation → batched update (last read msg ID per conversation)

**Optimization for groups:** Don't send individual read receipts for every member. Store per-user `lastReadMsgId` and compute "read by N" on demand.

### 6.4 — Online / Last Seen / Presence

**Problem:** 500M users — broadcasting presence to all contacts is extremely expensive.

**Solution: Lazy / Pull-based Presence**

```
Redis:
  Key:   presence:{user_id}
  Value: { "status": "ONLINE", "lastSeen": 1713600000 }
  TTL:   60 seconds (auto-expires → offline)
```

- Client sends heartbeat every 30s → refreshes TTL
- When User A opens a chat with User B → Client fetches B's presence from server
- **No fan-out broadcast** — presence is pulled, not pushed
- For "typing" indicators → short-lived, sent only to active conversation participants via WebSocket

### 6.5 — Media Sharing

```
User A                    API Server              S3 / Blob          CDN           User B
  │                           │                      │                │               │
  │── POST /media/upload ────►│                      │                │               │
  │   (multipart file)        │── upload ───────────►│                │               │
  │                           │◄── s3://bucket/key ──│                │               │
  │                           │── generate CDN URL ──────────────────►│               │
  │◄── { mediaUrl: cdn://... }│                      │                │               │
  │                           │                      │                │               │
  │── SEND_MESSAGE { contentType: IMAGE, mediaUrl: cdn://... } ─────────────────────►│
  │                           │                      │                │               │
  │                           │                      │        User B fetches image    │
  │                           │                      │◄──────────from CDN ───────────│
```

- **Upload before send**: Client uploads media, gets URL, then sends message with URL
- **Compression / Thumbnails**: Server generates thumbnails on upload for preview
- **CDN**: Media served from edge locations — low latency globally
- **Presigned URLs**: For private media, use time-limited presigned URLs

### 6.6 — Push Notifications

```
┌────────────────────┐
│ Notification       │
│ Service            │
│                    │
│ Consumes from      │◄── Kafka: offline_messages topic
│ Kafka              │
│                    │
│ Looks up device    │──► Device Token Store (Redis/DB)
│ tokens             │
│                    │
│ Sends via:         │──► FCM (Android)
│                    │──► APNs (iOS)
│                    │──► Web Push (Browser)
└────────────────────┘
```

- **Deduplication**: If user comes online before notification is processed → skip
- **Batching**: Group multiple unread messages into single notification
- **Rate limiting**: Don't spam — aggregate ("5 new messages from Alice")

---

## Step 7 — Scalability, Reliability & Trade-offs

### Scalability Strategies

| Component | Strategy |
|-----------|----------|
| **Chat Servers** | Horizontally scale; stateful but replaceable (reconnect to another server) |
| **Cassandra** | Partition by `conversation_id`; add nodes for more throughput |
| **Redis** | Cluster mode; shard by `user_id` |
| **Kafka** | Partition by `conversation_id` for ordering; add partitions for throughput |
| **API Servers** | Stateless; auto-scale behind load balancer |
| **Media** | S3 is infinitely scalable; CDN handles read distribution |

### Reliability / Fault Tolerance

| Failure | Mitigation |
|---------|------------|
| **Chat Server crashes** | Client detects disconnect → reconnects to another server via LB → re-registers in Redis → pulls missed messages from Cassandra |
| **Cassandra node down** | Replication factor = 3; read/write at quorum (2/3) → no data loss |
| **Redis node down** | Redis Cluster with replicas; worst case: user appears offline briefly |
| **Kafka broker down** | Replication factor = 3; ISR ensures no message loss |
| **Entire data center down** | Multi-DC deployment; DNS failover; Cassandra supports multi-DC replication natively |

### Key Trade-offs Discussed

| Decision | Trade-off |
|----------|-----------|
| **Cassandra over MySQL for messages** | Eventual consistency, no joins → but massive write throughput and horizontal scaling |
| **Fan-out on write (groups)** | Higher write amplification → but lower read latency (message already in each member's feed) |
| **Pull-based presence** | Users don't get instant presence updates → but saves enormous broadcast bandwidth |
| **WebSocket over HTTP polling** | Stateful connections, harder to load balance → but real-time, low latency, less bandwidth |
| **Async persistence (write to Kafka first)** | Tiny window of message in-flight → but better latency for sender |

### Monitoring & Observability

- **Metrics**: Message delivery latency (p50, p95, p99), WebSocket connection count, Kafka consumer lag
- **Alerting**: Delivery latency > 500ms, Kafka lag growing, chat server connection drops
- **Distributed tracing**: Trace a message from send → route → deliver → ACK

---

## Summary Diagram (Simplified)

```
                        ┌─────────────┐
                        │   Clients   │
                        └──────┬──────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
              WebSocket (WS)          REST (HTTPS)
                    │                     │
              ┌─────▼─────┐        ┌──────▼──────┐
              │    L4 LB   │        │  API Gateway │
              └─────┬─────┘        └──────┬──────┘
                    │                     │
              ┌─────▼─────┐        ┌──────▼──────┐
              │Chat Servers│        │ API Servers  │
              │(Stateful)  │        │ (Stateless)  │
              └─────┬─────┘        └──────┬──────┘
                    │                     │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Message Service   │
                    │   (Router + Logic)  │
                    └──┬─────┬─────┬─────┘
                       │     │     │
                 ┌─────▼┐ ┌─▼───┐ ┌▼─────────┐
                 │Redis │ │Kafka│ │Cassandra  │
                 │      │ │     │ │           │
                 └──────┘ └──┬──┘ └───────────┘
                             │
                    ┌────────▼────────┐
                    │  Notification   │──► FCM / APNs
                    │  Service        │
                    └─────────────────┘

                    ┌─────────────────┐
                    │  S3 + CDN       │  (Media)
                    └─────────────────┘
```

---

## How I'd Present This in an Interview (Timeline)

| Time | What I'd Cover |
|------|----------------|
| **0–5 min** | Clarify requirements, scope, and assumptions with interviewer |
| **5–10 min** | Back-of-envelope estimation (DAU, QPS, storage, bandwidth) |
| **10–15 min** | API design (WebSocket + REST) |
| **15–25 min** | Data model + storage technology choices with justification |
| **25–40 min** | Architecture diagram + message flows (1:1, group, offline) |
| **40–50 min** | Deep dives: connection management, ordering, receipts, presence, media |
| **50–55 min** | Scalability, fault tolerance, trade-offs |
| **55–60 min** | Monitoring, extensions, Q&A |

---

> **Key things interviewers look for:**
> 1. Structured approach — don't jump into drawing boxes
> 2. Justify every technology choice
> 3. Address the hard problems (connection management at scale, message ordering, offline delivery)
> 4. Discuss trade-offs, not just solutions
> 5. Numbers — show you can estimate scale

