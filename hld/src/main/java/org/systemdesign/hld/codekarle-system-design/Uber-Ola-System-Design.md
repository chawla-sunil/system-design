# 🚕 Uber / Ola / Lyft — System Design Interview Notes

This folder is made from the summary of the following sources:
- [Github Design Image](https://github.com/codekarle/system-design/blob/master/system-design-prep-material/architecture-diagrams/Uber%20System%20Design.png)
- [Blog](https://www.codekarle.com/system-design/Uber-system-design.html)
- [YouTube](https://www.youtube.com/watch?v=Tp8kpMe-ZKw&list=PLhgw50vUymycJPN6ZbGTpVKAJ0cL4OEH3&index=9)

---

## ⚡ 30-Second Interview Cheat Sheet (Read This First!)

> **The core problem:** *"Given a customer's lat/long, find the 2–3 closest available drivers, fast, at global scale."*

- 🧭 **Segments (geohash-like squares) are the KEY IDEA** — divide the world into rectangular cells. Search only in the customer's cell + neighboring cells, not the whole world.
- 🔌 **WebSockets** are used everywhere between apps and backend (drivers push location every 5–10s; backend pushes trip assignment back).
- 🗄️ **Poly-storage** — MySQL for transactional (User/Driver/Live Trips) · Redis for cache & driver→segment map · Cassandra for high-write (location pings, archived trips).
- 📬 **Kafka + Spark + Hadoop** power all analytics: heat maps, driver priority ML model, fraud detection, ETA improvement.
- ⚖️ **CAP nuance:** the system is **NOT uniformly AP or CP**. Read paths (see nearby cabs) → **AP**. Booking / payments / trip creation → **CP**.
- 🧠 **Mnemonic:** *"**S**egments **W**in **L**ocation **T**rips **P**erfectly"* → **S**egment, **W**ebSocket, **L**ocation Svc, **T**rip Svc, **P**riority Engine.

---

## 1. Requirements

### ✅ Functional
| # | Feature | Notes |
|---|---------|-------|
| 1 | **See Cabs** nearby (real-time map view) | Live cab pins around customer |
| 2 | **ETA + Price estimate** for A → B | Before booking |
| 3 | **Book a cab** (one type only; premium/pool skipped for simplicity) | Core flow |
| 4 | **Location tracking** of driver (billing, audit, safety) | Route persisted |

### ⚡ Non-Functional
- 🌍 **Global** — servers in every geography (low-latency access)
- ⏱ **Low latency** — not mission critical but must be snappy
- 🟢 **High Availability** — outage strands users; unacceptable
- 🎯 **High Consistency** on money-critical flows (trip, payment)
- ⚖️ **CAP trade-off**: Different components pick different sides — *not the whole system*

### 📊 Scale (Uber-ish numbers)
```
Active users (monthly) : 100 M
Rides / day            : 14 M
Location pings         : every 5–10 s from every online driver
```

---

## 2. 🔑 THE Key Idea — Segment-Based Nearest Neighbor Search

Divide every city (globe) into rectangular **segments** (like a grid / geohash).

```
   +--------+--------+--------+
   |   S1   |   S2   |   S3   |
   +--------+--------+--------+
   |   S4   |   S5   |   S6   |     ← customer C is in S5
   |        |   C    |   D2   |     ← driver D2 in S6 (neighbor!)
   +--------+--------+--------+
   |   S7   |   S8   |   S9   |
   +--------+--------+--------+
```

**Why segments?**
- Given a lat/long, computing "which segment am I in?" is O(1) coordinate math.
- To find nearby drivers → only scan **customer's segment + 8 surrounding segments**.
- A driver just across the border may still be the closest — that's why we ALSO query neighbors.

**Dynamic sizing** (Map Service handles this):
- Segment has too many drivers? → **Split** into 4 or 6 smaller ones.
- Segment has very few drivers? → **Merge** with neighbors.

> 💡 *Interview tip:* You can equivalently describe this as **Geohashing** or **QuadTree**. Segments are the intuition; geohash is the industry term.

---

## 3. High-Level Architecture

```
                          ┌───────────────────┐              ┌──────────────────┐
        Customer App ───▶ │  User Service     │◀────────────▶│  Trip Service    │
        (WebSocket)       │  (MySQL + Redis)  │              │  MySQL (live)    │
              │           └───────────────────┘              │  Cassandra (old) │
              ▼                                              └──────────────────┘
       ┌───────────────────┐                                        ▲
       │ Cab Request Svc   │─────────┐                              │ Trip Archiver
       │ (WebSocket to     │         │                              │ (cron 12h)
       │  Customer)        │         ▼
       └───────────────────┘  ┌─────────────────┐        ┌───────────────────┐
                              │  Cab Finder     │───────▶│  Driver Priority  │
                              │  (matching)     │        │  Engine (ML)      │
                              └─────────────────┘        └───────────────────┘
                                      │ │ │
                        ┌─────────────┘ │ └────────────────────────┐
                        ▼               ▼                          ▼
              ┌─────────────────┐ ┌────────────────┐    ┌───────────────────┐
              │ Location Svc    │ │ WebSocket Mgr  │    │ Map Service       │
              │ (Cassandra +    │ │ (Redis: driver │    │ - segment mgmt    │
              │  Redis: S→[D])  │ │  ↔ host map)   │    │ - ETA / distance  │
              └─────────────────┘ └────────────────┘    │ - neighbor lookup │
                        ▲                  ▲            └───────────────────┘
                        │ location pings   │ trip push
                        │ every 5–10s      │
              ┌─────────┴──────────────────┴─────────┐
              │  WebSocket Handler 1 │ 2 │ 3 │ ... N │  ← 100s of servers
              └──────────────────────────────────────┘
                        ▲
                        │ WebSocket
                Driver App (D1, D2, D3, ...)


        ┌──────────── Kafka (all events) ────────────┐
        │                                            │
        ▼                                            ▼
  Payment Service                          Spark Streaming
  (MySQL + Payment Gateway)                     │
                                                ▼
                                          Hadoop (batch ML)
                                          - User profiling
                                          - Driver profiling
                                          - Fraud detection
                                          - Heat maps
                                          - ETA improvement
```

---

## 4. Component Deep-Dive

### 4.1 User Service (Customer profile)
- Purpose: **CRUD on customer profile** + **proxy/facade** for user-scoped calls (e.g. "my trips" → forwards to Trip Service).
- Storage: **MySQL cluster** (source of truth) + **Redis cache** (read-through).
- Read flow: `Redis → miss → MySQL slave → populate Redis → return`.

### 4.2 Driver Service (Driver profile)
- Mirror of User Service, but for drivers.
- Powers driver app screens: payment history, past trips, etc.
- Storage: MySQL + Redis. Talks to Payment Service, Trip Service internally.

### 4.3 Map Service ⭐ (Abstraction)
- Owns the **segment grid** globally.
- APIs:
  - `getSegment(lat, long)` → returns segment id.
  - `getSurroundingSegments(segmentId)` → neighbor list.
  - `getETA(A, B)` + `getRoute(A, B)` + `getDistance(A, B)` (road distance, not aerial).
- Splits/merges segments dynamically based on driver density.
- 🎯 *Treat as black box — details covered in Google Maps design.*

### 4.4 Location Service ⭐ (The high-write beast)
- Receives **every driver location ping (every 5–10s)** via the WebSocket layer.
- Two writes per ping:
  1. **Cassandra** → store live location + trip route (audit, billing, replay).
  2. **Redis** → `segment_id → [list of driverIds]` (only when driver's segment changes).
- Also exposes: "give me all drivers currently in segment S5".
- Why Cassandra? → Massive write volume from millions of drivers pinging every 5s. Cassandra is write-optimized (LSM tree).

### 4.5 WebSocket Layer (Handler + Manager)
- **WebSocket Handlers** = pool of 100s of servers holding **persistent socket connections** to driver apps.
- **WebSocket Manager** = tracks `driverId ↔ hostId` bidirectionally in Redis (with disk persistence for durability).
- When driver D3 connects to Handler H2:
  - H2 tells Manager: *"I own D3 now."*
  - Manager stores both `D3 → H2` and `H2 → [D1, D2, D3, …]`.
- When Cab Finder wants to notify D3: asks Manager → Manager returns H2 → Cab Finder tells H2 → H2 pushes via WebSocket to D3.

> **Why WebSocket?**
> - Location pings every 5s → creating fresh HTTP conn each time is expensive.
> - Server needs to **push** trip assignment to driver — WebSocket allows bidirectional.

### 4.6 Trip Service
- **Source of truth** for all trips.
- Storage strategy — **hot + cold split**:
  - **MySQL** → active / in-progress / soon-to-start trips. Needs **transactions** across many tables (trip, driver, customer, events, payment).
  - **Cassandra** → completed trips (read-only, huge volume).
- **Trip Archiver** cron (~every 12h) moves MySQL → Cassandra.
- Search by driver_id → queries **both** MySQL + Cassandra → merges results.

### 4.7 Cab Request Service (Customer-facing gateway)
- Holds the WebSocket to the **customer app**.
- Sends live "cabs nearby" pins to the map.
- On booking request: forwards to **Cab Finder**, awaits driver assignment, pushes result back to customer.

### 4.8 Cab Finder ⭐ (The matching brain)
The most important service — orchestrates matching:

1. Get customer's segment (via Location Service → Map Service).
2. Get surrounding segments (via Map Service).
3. Fetch drivers in all those segments (Location Service `S→[D]` map).
4. Ask Map Service to compute **road distance** for each driver → customer, return **closest ~10 drivers**.
5. Send those 10 to **Driver Priority Engine** → get them stack-ranked.
6. Apply **matching mode**:
   - **Best-driver mode** → pick top-ranked (used for premium customers).
   - **Broadcast mode** → notify all 10, first-to-accept wins (used for regular customers).
7. Notify chosen driver via **WebSocket Manager → Handler**.
8. Confirm to customer via **Cab Request Service**.
9. Create trip in **Trip Service**.
10. Emit event to **Kafka** (`trip_started`, or `no_driver_found`).

### 4.9 Driver Priority Engine
- Stack-ranks a candidate driver list.
- Uses ML model trained on: ⭐ rating, customer feedback, ETA accuracy (promised vs actual), cancellation rate, etc.
- Model trained offline (Hadoop/Spark). Served real-time.

---

## 5. 🎬 End-to-End Booking Flow

```
1. Customer opens app                    → WebSocket to Cab Request Svc
2. App shows nearby cabs                 → Cab Request Svc queries Location Svc periodically
3. Customer taps "Book" (A→B)            → Cab Request Svc → Cab Finder
4. Cab Finder                            → Location Svc (customer segment + neighbors + drivers)
5. Cab Finder                            → Map Svc (distance to each driver)
6. Cab Finder                            → Driver Priority Engine (rank top 10)
7. Cab Finder picks 1 driver (mode)      → WS Manager → WS Handler → Driver App (push!)
8. Driver accepts                        → WS Handler → Cab Finder
9. Cab Finder                            → Trip Service (create trip in MySQL)
10. Cab Finder                           → Cab Request Svc → Customer App (driver details)
11. Kafka event emitted                  → payment/analytics pipelines
```

---

## 6. 📬 Async / Analytics Pipeline (Kafka → Spark → Hadoop)

**Everything interesting goes into Kafka:** trip events, location pings, no-driver-found events, cancellations…

### Real-time (Spark Streaming)
- **Heat maps** — clusters of "no driver found" events → show surge zones on Driver App to attract drivers.
- **Live surge pricing** hooks.

### Batch (Hadoop / Spark jobs on ML models)
| Job | What it does | Consumed by |
|-----|--------------|-------------|
| **User Profiling** | Classify customer as premium / regular | Cab Finder mode selection |
| **Driver Profiling** | Score drivers (rating, ETA accuracy, feedback) | Driver Priority Engine |
| **Fraud Detection** | Same customer ↔ same driver always? Two phones same person? | Ops team / auto-ban |
| **Traffic estimation** | Aggregate driver speeds per road → traffic feed | Map Service |
| **ETA improvement** | Historical actual times → refine ETA model | Map Service |

### Payment Service
- Kafka consumer on `trip_completed` events.
- Writes to Payment MySQL: driver_id, trip_id, amount, distance, time, etc.
- Either instantly transacts with Payment Gateway, or aggregates daily/weekly via cron.

---

## 7. 🗄️ Storage Cheat Sheet

| Data | Store | Why |
|------|-------|-----|
| User / Driver profiles | **MySQL + Redis** | Transactional, low volume, hot cache |
| **Live driver location** | **Cassandra** | Massive writes (M drivers × 5s) |
| **segment → drivers** map | **Redis** | Real-time lookup for Cab Finder |
| **driver → host** map | **Redis (persisted)** | Fast routing for push notifications |
| **Live trips** | **MySQL** | Multi-table transactions |
| **Archived trips** | **Cassandra** | Huge, read-mostly |
| **Events** | **Kafka** | Buffer + fan-out to analytics |
| **Analytics / ML** | **Hadoop (HDFS)** | Batch, cheap storage |
| **Payments** | **MySQL** | ACID, financial data |

---

## 8. 🎯 CAP Theorem — Where Each Component Sits

| Component | Choice | Reason |
|-----------|--------|--------|
| See nearby cabs | **AP** | Slight staleness is fine; must be up |
| Location pings storage | **AP** (Cassandra) | Availability + write-volume wins |
| Booking / trip creation | **CP** | Cannot double-book a driver |
| Payments | **CP** | ACID mandatory |
| Trip history read | **AP** | Read from cache/Cassandra OK |

> 🎤 **Interview soundbite:** *"CAP is not chosen at system level — it's chosen at API level. Uber uses AP for location and discovery, CP for booking and money."*

---

## 9. 🎤 Talking Points for the Interview

1. **Start with segments** — geographic partitioning is the "aha" of this design.
2. **Neighbor search** — always mention "we query customer segment + surrounding segments" (interviewers love this catch).
3. **WebSocket over HTTP** — because of location ping frequency + server push.
4. **Redis for segment→driver map** — updated only when a driver *changes* segment, not on every ping.
5. **Kafka is the backbone** — decouples online path from analytics/ML.
6. **Cassandra vs MySQL trade-off** — Cassandra for high-write append (locations, archived trips); MySQL for transactional (users, live trips, payments).
7. **Driver Priority Engine + modes** — shows you know matching isn't just "nearest driver".
8. **Dynamic segment sizing** — auto split/merge based on density.
9. **CAP is per-service, not per-system.**
10. **Trip Archiver** — small elegant piece; shows awareness of data lifecycle.

---

## 10. 🖼️ Reference Diagram
See `Uber-Ola-System-Design.png` in this folder for the full architecture diagram.

---

## 11. ❓ Likely Follow-Up Questions

| Q | Short answer |
|---|--------------|
| How do you handle **surge pricing**? | Spark Streaming detects supply/demand imbalance per segment → multiplier applied at price quote time. |
| **Two customers book the same driver simultaneously?** | Distributed lock (Redis / ZooKeeper) on driver_id inside Cab Finder before assignment. |
| **Driver goes offline mid-trip?** | Trip stays in MySQL as "in-progress"; WebSocket disconnect event triggers reassignment / support flow. |
| **How to shard MySQL?** | User Service → shard by user_id hash. Trip Service → shard by trip_id (or geography). |
| **Global deployment?** | Regional clusters; customer routed via geo-DNS/load balancer to nearest region. |
| **How is ETA calculated?** | Delegated to Map Service (Google Maps design covers this — road graph + Dijkstra/A* + historical traffic). |
| **Failure of WebSocket Manager?** | Redis is replicated + persisted; on restart, Handlers re-register their driver sets. |
| **How to prevent stale driver locations?** | Redis TTL on segment→driver entries; also driver marked offline on WebSocket disconnect. |

---

*End of notes — good luck! 🍀*
