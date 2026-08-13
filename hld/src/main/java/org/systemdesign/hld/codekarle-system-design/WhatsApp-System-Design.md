# 📱 WhatsApp / Chat App — System Design (Interview Revision Note)

> Source: 
- [Github Design Images](https://github.com/codekarle/system-design/blob/master/system-design-prep-material/architecture-diagrams/Whatsapp%20System%20design.png)
- [Blog](https://www.codekarle.com/system-design/Whatsapp-system-design.html)
- [YouTube](https://www.youtube.com/watch?v=RjQjbJ2UJDg&list=PLhgw50vUymycJPN6ZbGTpVKAJ0cL4OEH3&index=11)

---

## ⚡ 30-Second Interview Pitch (read this first)

> **"WhatsApp is a real-time, bi-directional messaging system built on persistent WebSocket connections.
> Each online user holds an open WebSocket to a `WebSocket Handler` server. A central `WebSocket Manager`
> (backed by Redis) is the phone-book that maps `user → which handler`. To send a message, my handler saves it
> (Cassandra via `Message Service`), asks the manager which handler owns the recipient, and pushes the message
> to that handler which delivers it. Delivery/read receipts flow back on the same path. Groups fan-out
> asynchronously via Kafka + a `Group Message Handler`. Media goes through an `Asset Service` → S3/CDN.
> Core NFRs are low latency, no lag, and high availability."**

**One-liner to remember:** *Persistent WebSockets + a Redis-backed connection registry + Cassandra for messages + Kafka for group fan-out.*

---

## 🧭 How to Approach This in an Interview (my mental checklist)

1. **Nail the requirements first** — 1:1 chat, group chat, media, receipts (✓✓/blue), last seen.
2. **State the killer NFR** — it's *real-time*. Unlike feed systems, **NO lag is acceptable**. This justifies WebSockets over polling.
3. **Start with the 1:1 happy path** — WebSocket Handler ↔ WebSocket Manager ↔ Message Service. Draw this.
4. **Then layer complexity incrementally:**
   - Receipts (sent → delivered → read)
   - Offline recipient (store & fetch on reconnect)
   - Offline sender (device-local SQLite queue)
   - Race condition on reconnect (and how to fix it)
   - Groups (Kafka fan-out)
   - Media upload (S3/CDN + dedup by hash)
5. **Close with supporting services** — User, Group, Last Seen, Analytics + monitoring.
6. **Justify every DB choice** (Cassandra vs MySQL vs Redis) — interviewers love this.

---

## ✅ Requirements

### Functional
| Feature | Notes |
|---|---|
| **1:1 Chat** | User → User messaging |
| **Group Chat** | One message → all group members |
| **Media** | Text, images, videos, documents |
| **Read Receipts** | ✔ sent · ✔✔ delivered · 🔵 blue = read |
| **Last Seen** | Last active timestamp per user |

### Non-Functional (the important ones)
- 🚀 **Very low latency** — must feel real-time.
- 🔴 **No lag** — cannot batch/delay like a feed system.
- 🟢 **High availability** — must never go down.

### Scale (approx WhatsApp numbers)
- ~**2B** total users, ~**1.6B** monthly active.
- ~**65B** messages/day.
- → Drives Cassandra (writes), Kafka (fan-out), horizontal scaling everywhere.

---

## 🏗️ High-Level Architecture (big picture)

```
                          ┌──────────────────────┐
                          │   WebSocket Manager   │  (connection registry / phone-book)
   ┌──────────┐  WS       │   + Redis             │
   │  User 1  │◄────────► │  user → handler map   │
   │ (device) │           │  handler → users list │
   └──────────┘           └───────▲──────▲────────┘
        ▲                         │      │
        │ WebSocket (TCP,         │      │
        │  bi-directional)        │      │
        ▼                         │      │
   ┌───────────────┐   asks "who has U2?"│
   │ WS Handler 1  │─────────────────────┘
   │ (stateless,   │                     
   │  light logic) │──► Message Service ──► Cassandra   (all messages)
   └──────┬────────┘         │
          │ "deliver to U2"  └──► Kafka (group msgs only) ──► Group Msg Handler
          ▼                                                        │
   ┌───────────────┐                                               ▼
   │ WS Handler 2  │◄──────► User 2 (device)                  Group Service
   └───────────────┘

   Supporting services: User Service (MySQL+Redis) · Group Service (MySQL+Redis)
                        Asset Service (S3+CDN) · Last Seen Service (Cassandra)
                        Analytics (Kafka→Spark→Hadoop)
```

### Key components at a glance
| Component | Role | Datastore | Why |
|---|---|---|---|
| **WebSocket Handler** | Holds live user connections; sends/receives msgs. *Lightweight, little logic.* | — (in-memory cache) | Distributed globally for low latency |
| **WebSocket Manager** | Registry: which user is on which handler | **Redis** | Fast lookups, ephemeral connection state |
| **Message Service** | CRUD messages (by id, by user, filters) | **Cassandra** | Huge write volume, query pattern fit |
| **Group Service** | Group ↔ user membership | **MySQL + Redis** | Relational many-to-many, cache reads |
| **User Service** | Profile info | **MySQL + Redis** | Relational, cached |
| **Asset Service** | Image/video storage | **S3 + CDN** | Blob storage, edge delivery |
| **Last Seen Service** | Last active time | **Cassandra** | Extremely high write throughput |
| **Group Msg Handler** | Kafka consumer, fans out group msgs | — | Async, decoupled fan-out |

---

## 🔌 The Foundation: WebSockets

- **Bi-directional** connection over **TCP** — either side can initiate a send.
- This is what lets the **server push** a message to the client (unlike request/response HTTP).
- Handlers are **spread globally** so users connect to a nearby server → lower latency.
- A user's connection can **break & reconnect to a different handler** (esp. cross-region) → registry must stay updated.

---

## 💬 Flow 1: One-to-One Message (the happy path)

**Scenario:** User1 sends message `m1` to User2.

```
U1 ──"send m1 to U2"──► WS Handler 1
                            │
              ┌─────────────┴──────────────┐  (parallel calls)
              ▼                            ▼
    Message Service                 WebSocket Manager
    → save m1 in Cassandra          → "U2 is on WS Handler 2"
    → returns msg id m1                        │
                                               ▼
              WS Handler 1 ──"got a msg for U2"──► WS Handler 2
                                                        │
                                                        ▼
                                                   U2 (device)
```

**Steps:**
1. U1 sends `m1` (for U2) to its handler **WS Handler 1**.
2. WS Handler 1 does **two things in parallel**:
   - **Message Service** → persist `m1` in Cassandra.
   - **WebSocket Manager** → "which handler has U2?" → returns **WS Handler 2**.
3. WS Handler 1 → tells **WS Handler 2**: "deliver `m1` to U2."
4. WS Handler 2 → pushes `m1` to U2's device (if still connected).

---

## ✔✔ Flow 2: Delivery & Read Receipts

Receipts travel **back along the same path**, in reverse:

```
U2 device ──"received m1" / "read m1"──► WS Handler 2
                                              │  asks Manager: "who has U1?"
                                              ▼
                                         WS Handler 1 ──► U1  (shows ✔✔ / 🔵)
```

- Device tells WS Handler 2: *received* (delivered ✔✔) or *received + seen* (read 🔵).
- WS Handler 2 asks Manager who holds U1 → forwards receipt → WS Handler 1 → U1.
- **Status stored in Cassandra** (same message row): `sent → received → read`.
- Storing receipts/messages at all is a **discuss-with-interviewer** decision:
  - **Facebook style** → keep everything permanently.
  - **WhatsApp style** → delete message after delivery ACK.
    *(Note: Cassandra deletes are inefficient → might pick another store for delete-heavy design.)*
- Even in WhatsApp style, **store at least briefly** — e.g. if U1 goes offline before the receipt arrives, it must be delivered when U1 returns.

---

## ⚡ Caching (to avoid hammering the Manager)

Each handler keeps **two caches**:
1. **Own connected users** (permanent while connected) — if U2 is on the same handler, no Manager call needed.
2. **Recent conversations** (short TTL, ~30s) — "U2 is on Handler H2." Reuse for follow-up messages.
   - ⚠️ **Must have low TTL** — global users reconnect to different handlers frequently (e.g., Sri Lanka user → India server → connection drops → new machine). Stale routing = lost messages.

---

## 📴 Flow 3: Offline Recipient

**Scenario:** U1 → U3, but U3 is offline.

1. WS Handler 1 saves msg + asks Manager for U3's handler.
2. Manager replies **"no machine for U3."** → flow ends, nobody notified.
3. **When U3 reconnects** (to WS Handler 3):
   - First thing H3 does: query **Message Service** → "any messages for U3 not in received/read status?"
   - Delivers them → then normal receipt flow resumes.

---

## ⚠️ The Race Condition (important — interviewers probe this)

**Setup:** U1 sends to U3 *at the exact moment* U3 comes online. Parallel calls interleave badly:

```
1. Manager: "no machine for U3"        (U1's handler thinks U3 offline)
2. U3 connects, fetches all messages    ← m2 NOT saved yet!
3. Manager records "U3 → Handler 3"
4. Message Service finally saves m2      ← too late, U3 already fetched
```

**Result:** `m2` is stored but **never delivered** — U3 fetched *before* it was written, and U1's handler thinks U3 was offline.

### Fixes
| Option | Verdict |
|---|---|
| Do the two calls **sequentially** | ❌ Increases latency — not recommended |
| **Periodic low-frequency pull** (recommended) | ✅ Handler periodically bulk-asks: *"any `sent`-status messages for U1..U100?"* for all its connected users, and delivers any it missed |

---

## 📲 Flow 4: Offline Sender (device-side)

- You can type/send messages while **offline**.
- **All messages are first written to a local on-device DB** (e.g., **SQLite** on Android) — persisted to disk.
- When internet returns → device drains the local **queue** and sends messages via WebSocket.
- (This local-first store is actually how *all* messages are handled, not just offline ones.)

---

## 👥 Flow 5: Group Messages (async fan-out via Kafka)

**Key difference:** the lightweight WS Handler does **NOT** fan out. Fan-out is offloaded to Kafka.

```
U1 ──"m3 to group g1"──► WS Handler ──► Message Service
                                            │  saves m3 in Cassandra
                                            └──► Kafka topic (group msgs)
                                                      │
                                                      ▼
                                          Group Message Handler (Kafka consumer)
                                                      │  1. (validate U1 ∈ g1 — out of scope)
                                                      │  2. Group Service → members of g1
                                                      │  3. remove U1 from list
                                                      ▼
                                          For each member → same 1:1 flow:
                                          Manager → handlers → deliver → receipts
```

**Steps:**
1. WS Handler → Message Service: "U1 → g1 : m3." Just store it.
2. Message Service saves to Cassandra **and** publishes to a **Kafka** group-message topic.
3. **Group Message Handler** (Kafka consumer) picks it up:
   - Queries **Group Service** for all members of `g1`.
   - Removes sender U1 from the list.
   - For each remaining member → **exact same 1:1 delivery flow** (Manager → handler → device), receipts follow the same path.

---

## 🖼️ Flow 6: Media Upload (images / videos)

**Two-step process:** upload asset → get `image id` → send id as a normal message.

```
U3 ──(1) compress──► Asset Service ──► S3 ───(hot images)──► CDN(s)
        (encryption in real WhatsApp,        │
         kept out of scope here)             ▼
                                    returns image id (i1)
U3 ──(2) send i1 as normal message──► ... ──► U2 ──► U2 fetches image
```

- **Compression** happens on device first (real WhatsApp also **encrypts** here — out of scope).
- **Asset Service** (behind Load Balancer, handles auth) stores blobs in **Amazon S3**.
- Hot images get promoted/replicated to **CDN(s)** near traffic sources.
- The returned `image id` is sent to U2 via the **regular message route**; receipts identical.

### 🔑 Dedup optimization (viral image problem)
- If 5 people share the same viral image, don't upload 5×.
- Device first sends a **hash** of the image: *"do you already have this?"*
  - Already there → just send the id to recipient (skip upload).
  - Not there → upload normally.
- **Collision safety:** use **5 different hash algorithms**; only treat as duplicate if **all 5 match**.

---

## 🧩 Supporting Services

### 👤 User Service
- Owns profile: name, user id, profile pic, preferences.
- **MySQL cluster** (relational) + **Redis cache**.
- Cache miss → read from a MySQL **slave** → populate Redis → respond.

### 👨‍👩‍👧 Group Service
- Maintains group ↔ user mapping (many-to-many: `user_id, group_id, joined_at, role/admin`).
- **MySQL cluster** (geo-distributed, read slaves) + **Redis**.
- Powers APIs like *"get users in a group"* → used in group message fan-out.

### 🕐 Last Seen Service
- Stores `user_id → last_seen_time`.
- **Cassandra** (or Redis if data is small/finite).
- **Why Cassandra, not MySQL?** App pings *"I'm alive"* **every ~5 seconds per user** → massive write throughput. MySQL master can't absorb that write load; Redis struggles at that update volume. Cassandra spreads writes across nodes easily.
- **Event source nuance:** two kinds of events —
  - **App-spawned** (e.g., WebSocket auto-connect on internet-on) → **NOT** a user activity → **ignored** by Last Seen.
  - **User-spawned** (opens/closes app, any in-app action) → **these** update Last Seen.

### 📊 Analytics
- Every user action / message → **Analytics Service → Kafka** (messages can go directly to Kafka).
- **Spark Streaming consumer** does real-time inference: classify messages (sports/politics/general), tag users, trending topics.
- Also dumps to **Hadoop** → batch analytics queries (e.g., "who talks about sports a lot").
- ⚠️ Impossible if messages are **end-to-end encrypted**.

---

## 🗄️ Datastore Choices — Quick Justification Cheatsheet

| Data | Store | Reason |
|---|---|---|
| Messages | **Cassandra** | Billions of writes/day; query pattern (by user, unread) fits; horizontally scalable |
| Connection registry | **Redis** | Ephemeral, ultra-fast lookups |
| User / Group data | **MySQL + Redis** | Relational (many-to-many groups), read-heavy → cache |
| Media blobs | **S3 + CDN** | Cheap blob storage + edge delivery |
| Last seen | **Cassandra** | Insane write throughput (every 5s/user) |
| Group fan-out & analytics pipe | **Kafka** | Decoupled async fan-out, buffering, back-pressure |

---

## 📈 Scaling, Monitoring & Alerting

- **Everything is horizontally scalable** — add nodes as users grow.
- **Monitor (→ Grafana):**
  - CPU / memory of services
  - Disk utilization of DBs and Kafka
  - Web-service throughput & latency
  - **Kafka lag** (critical — high lag → add more consumers)
- **Alerting → automation:** thresholds trigger alerts; can auto-scale (e.g., AWS script adds/removes nodes on throughput changes).
- Matters *a lot* here because the core NFR is **real-time**; lag → bad UX → user churn.

---

## 🎯 Rapid-Fire Recall (last-minute glance)

- **Transport:** WebSocket (bi-directional, over TCP) → enables server push.
- **Registry:** WebSocket Manager + Redis = `user ↔ handler` phone-book.
- **Handlers:** stateless, lightweight, globally distributed, cache connected users + recent routes (30s TTL).
- **Messages:** Message Service → Cassandra; status `sent→received→read`.
- **Offline recipient:** stored, fetched on reconnect.
- **Offline sender:** device SQLite queue, drained on reconnect.
- **Race condition:** periodic bulk pull of `sent`-status messages fixes it.
- **Groups:** Message Service → Kafka → Group Message Handler → (Group Service members) → per-user 1:1 flow.
- **Media:** compress → Asset Service → S3/CDN; dedup via **5 hashes**; send image id as normal msg.
- **Support:** User (MySQL+Redis), Group (MySQL+Redis), Last Seen (Cassandra, 5s pings), Analytics (Kafka→Spark→Hadoop).
- **NFRs:** low latency · no lag · high availability.
