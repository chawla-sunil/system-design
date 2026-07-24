# 🛒 Amazon / Flipkart — E-commerce System Design 

This folder is made from the summary of the following sources:
- [Github Design Images](https://github.com/codekarle/system-design/blob/master/system-design-prep-material/architecture-diagrams/Amazon%20System%20Design.pngg)
- [Blog](https://www.codekarle.com/system-design/Amazon-system-design.html)
- [YouTube](https://www.youtube.com/watch?v=EpASu_1dUdE&list=PLhgw50vUymycJPN6ZbGTpVKAJ0cL4OEH3&index=8)

> **Last-minute interview revision note.** Read the "30-Second Pitch" + "Cheat Sheet", glance at the diagrams, and you have the whole design in your head.

---

## ⚡ 30-Second Pitch (say this first in an interview)

- **Two flows:** (1) **Search/Home flow** (read-heavy, high availability) and (2) **Checkout/Order flow** (write-heavy, strong consistency).
- **Search must show serviceability (TAT):** never show a product we can't deliver to the user's pincode.
- **Order = state machine** (`CREATED → PLACED / CANCELLED`) backed by **MySQL** for ACID transactions; **inventory is blocked before payment** using a DB constraint (`count >= 0`).
- **Redis TTL + expiry callback** handles the "user abandoned payment" case → auto-cancel + release inventory.
- **Everything interesting is pushed to Kafka** → Spark/Hadoop for analytics + ML recommendations.
- **Cold orders are archived** MySQL → Cassandra to keep the hot order DB small.

**One-liner:** *"Split into a highly-available search side and a strongly-consistent order side, glued together by Kafka, with Redis guarding the payment window and Cassandra archiving old orders."*

---

## 🎯 Requirements

### Functional
1. **Search** products + show **whether/when** we can deliver (serviceability + ETA) right on the search page.
2. **Cart / Wishlist** — add items to buy later.
3. **Checkout** — make payment, place order (payment gateway details out of scope).
4. **Order history** — view past orders (delivered / in-transit / cancelled).

### Non-Functional (the key trade-off insight 🔑)
> "High availability + High consistency + Low latency all together is too much." → **Split responsibilities.**

| Concern | Priority | Applies to |
|---|---|---|
| **High Consistency** (over availability) | 🟥 | Payment, Inventory count |
| **High Availability** (over consistency at times) | 🟩 | Search, Recommendations |
| **Low Latency** | ⚡ | All user-facing components |

---

## 🎨 Diagram Color Convention

| Color | Meaning |
|---|---|
| 🟩 **Green** | UI — browser / mobile app |
| ⬛ **Black bar (LB)** | Load Balancer + Reverse Proxy + Auth (authN/authZ) layer |
| 🟦 **Blue** | Our services (REST services, Kafka consumers, Spark jobs) |
| 🟥 **Red** | Databases / clusters / 3rd-party (MySQL, Mongo, ES, Kafka, Hadoop, Redis, Cassandra) |

---

# PART 1 — Search & Home Screen (Read Side)

## 🗺️ Flow Diagram

```
                          SUPPLIERS
                             │
                     ┌───────▼────────┐
                     │ Inbound Service│  (abstracts all supplier systems)
                     └───────┬────────┘
                             │ new item
                          ┌──▼──┐
                          │KAFKA│
                          └──┬──┘
             ┌───────────────┼────────────────┐
             ▼               ▼                 ▼
      ┌────────────┐  ┌──────────────┐   (other consumers)
      │Item Service│  │Search Consumer│
      └─────┬──────┘  └──────┬───────┘
            │ (source of      │ writes searchable docs
            │  truth, bulk    ▼
            │  GET API)  ┌──────────────┐
         ┌──▼───┐        │ Elastic Search│ (text/fuzzy search)
         │Mongo │        └──────┬───────┘
         │(ITEM)│               │
         └──────┘        ┌──────▼───────┐
                         │Search Service│◄── User Search Flow (green) ─ via LB
                         └──┬───┬───┬───┘
                            │   │   └──────────────┐
             ┌──────────────┘   │                  │
             ▼                   ▼                  ▼
   ┌──────────────────┐   ┌───────────┐    ┌───────────────┐
   │Serviceability/TAT│   │User Service│   │  → Kafka       │
   │  Service         │   │(MySQL+Redis)│  │ (search event  │
   └───┬──────────┬───┘   └───────────┘    │  = buy intent) │
       ▼          ▼                        └───────────────┘
 ┌──────────┐ ┌──────────┐
 │Warehouse │ │Logistics │  (queried offline to pre-build the
 │ Service  │ │ Service  │   pincode×warehouse serviceability graph)
 └──────────┘ └──────────┘

Also from search page:  Wishlist Service ──► Wishlist MySQL
                        Cart Service     ──► Cart MySQL
                        (both also emit events to Kafka)
```

## 🔩 Component Notes

### Inbound Service → Kafka
- Abstracts all supplier/seller integrations. New/updated item info flows in → **Kafka** → multiple consumers process it into the user world.

### Item Service (on **MongoDB**)
- **Source of truth** for all items. APIs: get by ID, add, remove, update, **bulk GET** (many item IDs → details).
- **Why Mongo?** Item data is **unstructured** — attributes differ per product type (shirt→size/color, TV→screen size, bread→weight/type). MySQL is a poor fit for such variable schemas.

### Search Consumer → **Elastic Search**
- Reads incoming items, reshapes them into search format, writes to **Elastic Search** (NoSQL, great at **text + filter + fuzzy** search on name/description/attributes).
- **Fixed contract** between Search Consumer (writer) and Search Service (reader).
- Also **removes out-of-stock items** from listings (see order flow).

### Search Service
- Public interface for the frontend. APIs to search by string / filter by attributes.
- On each search → **emits an event to Kafka** (search = intent to buy → recommendation input).

### Serviceability & TAT Service ⭐ (the special part of this design)
- Filters out products we **cannot deliver** to the user's pincode, and returns **ETA** (e.g. "12h / 24h").
- Logic: locate product's warehouse(s) → is there a delivery route warehouse→pincode? → what product types can that route carry? (some routes can't carry big items like fridges).
- **Pre-computes everything** — no runtime calculation. For **N pincodes × M warehouses** it precomputes all combinations and stores in **cache**. (Similar to the Google Maps shortest-path design.)
- Queries **Warehouse Service** (items in warehouse) & **Logistics Service** (pincodes, courier partners) **offline** to build this graph.
- Search may call **User Service** to fetch the user's default address → pass to Serviceability.

### Cart & Wishlist Services (each on its **own MySQL**)
- Wishlist = save for later; Cart = shopping bag to checkout. APIs: add / get / delete item.
- **Almost identical** functionally, but kept on **separate hardware** so each can scale independently.
- Add-to-cart / add-to-wishlist → **events to Kafka** (more buy-intent signals).

### User Service (**MySQL + Redis cache**)
- Source of truth for users. Read path: **check Redis → miss → read MySQL slave → populate Redis → return** (cache-aside).

---

# PART 2 — Analytics & Recommendations (from search side)

```
 Kafka (search/cart/wishlist events)
   │
   ▼
 Spark Streaming Consumer ──► real-time reports
   │        (e.g. most bought / most wishlisted item in last 30 min, top item per category)
   ▼
 Hadoop  ──► ML jobs (ALS etc.)
   │         • "what else might THIS user like"
   │         • "users similar to this user bought X → recommend X"
   ▼
 Recommendation Service ──► stores per-user + per-category recommendations
```

- **Home page** shows **general** recommendations; drilling into a category shows **category-specific** ones.
- New user → generic recommendations.

---

# PART 3 — Order Management / Checkout (Write Side) ⭐⭐

This is the **most important interview section.**

## 🗺️ Order Flow Diagram

```
 User Purchase Flow (green) ── via LB ──► Order Taking Service ──► MySQL (OMS)
                                               │  (ACID / transactions)
        On "place order", 3 steps happen:
        ┌───────────────────────────────────────────────────────┐
        │ 1. Create order record  → status = CREATED (id=O1,10:00)│
        │ 2. Put key in REDIS     → O1, TTL expires at 10:05      │
        │ 3. Call Inventory Service → BLOCK inventory (count--)   │
        └───────────────────────────────────────────────────────┘
                                               │
                                               ▼
                                     ┌──────────────────┐
                                     │  Payment Service  │ (abstracts gateways)
                                     └──────────────────┘
                                               │
              ┌────────────────────────────────┼────────────────────────────┐
              ▼ SUCCESS                         ▼ FAILURE                     ▼ NO RESPONSE
        status = PLACED               status = CANCELLED             (user closed window)
        delete Redis key              increment inventory back       Redis TTL expiry callback
        emit event → Kafka            delete Redis key                 at ~10:05 fires
                                      Reconciliation Service           → Order Taking Service
                                      double-checks counts             → mark CANCELLED
                                                                       → increment inventory back
```

## 🔩 Component Notes

### Order Taking Service (part of OMS, on **MySQL**)
- **Why MySQL?** An order spans many tables (order, customer, item) with many updates → need **atomic transactions (ACID)** to avoid partial writes. MySQL gives this out of the box.

### The 3 steps on "Place Order"
1. **Create order** → generate `orderId`, status **`CREATED`** (e.g. `O1 @ 10:00`).
2. **Redis entry** with **TTL** (e.g. created 10:00, expires 10:05) — this is the **payment-window guard**.
3. **Block inventory** via **Inventory Service** (decrement count) *before* sending user to payment.

### Inventory Service — the concurrency trick 🔑
- **Block inventory before payment** so two users don't buy the last unit.
- Enforced by a **DB constraint: `count` cannot go negative.**
- 1 TV, 3 concurrent buyers → only **one** decrement succeeds; the other two get a **Constraint Violation** → "out of stock". Clean, DB-enforced concurrency control.

### Payment Service → 3 outcomes

| Outcome | Actions |
|---|---|
| ✅ **Success** (e.g. 10:01) | status → **PLACED**; **delete Redis key**; emit order-placed event → Kafka |
| ❌ **Failure** | status → **CANCELLED**; **roll back inventory** (increment back); delete Redis key |
| ⚠️ **No response** (window closed) | Redis TTL **expiry callback** at ~10:05 → Order Taking Service marks **CANCELLED** + increments inventory back |

### Reconciliation Service
- Periodically verifies inventory counts are correct (catches missed/failed inventory updates). Safety net.

### ⚠️ Race Conditions (Redis expiry vs payment) — interviewers love this

- **Optimization:** On **any** payment success/failure event, **delete the Redis key immediately** so the expiry callback never fires unnecessarily (also saves RAM).
- **Case A — success then expiry:** always would happen naturally; solved by the delete-on-payment optimization above.
- **Case B — expiry first, then late success** (expiry 10:05, payment 10:07): order already cancelled + inventory restored. Two options:
  1. **Refund** the customer, or
  2. **Create a fresh order**, attach the payment, put it directly in **PLACED**.
- **Redis expiry is NOT precise** — Redis checks keys periodically, so callback may fire at 10:06/10:07, not exactly 10:05. Fine here; for **mission-critical** cases use a **queue polled every second** instead. *(Mention this trade-off to the interviewer.)*

### Removing sold-out items from Search
- When inventory hits 0, an event → Kafka → **Search Consumer removes** those item docs from Elastic Search so they stop appearing.

---

# PART 4 — Archival (keeping the order DB small)

**Problem:** Millions of orders/day → order MySQL bloats (must retain years of data for audit).
**Insight:** ACID is only needed for **live/changing** orders. **Terminal** orders (DELIVERED / CANCELLED) don't need it → move them to **Cassandra**.

```
                    ┌────────────────────────┐
   MySQL (hot) ◄────│ Order Processing Service│  (full lifecycle of live orders; Get APIs)
                    └───────────┬─────────────┘
                                │  query terminal orders
                    ┌───────────▼─────────────┐
                    │    Archival Service      │  (cron: every 12–24h, idempotent)
                    └───────────┬─────────────┘
                                │  1. read terminal orders from Order Processing
                                │  2. insert into Historical Order Service (Cassandra)
                                │  3. on success → delete from Order Processing (MySQL)
                                │  (fails midway → safely retry, it's idempotent)
                    ┌───────────▼─────────────┐
                    │ Historical Order Service │ ──► Cassandra (cold storage)
                    └──────────────────────────┘
```

### Order History View
- A backend service merges **live** orders (Order Processing Service / MySQL) + **completed** orders (Historical Order Service / Cassandra) → returns combined list to the app.

### Why Cassandra for history? 🔑
- **Few query patterns, huge data.** Queries are limited & known:
  1. get order by **orderId**, 2) all orders by **userId**, 3) all orders by **sellerId**.
- Cassandra excels when you have a **finite set of query types** over a **very large dataset** (design tables around those queries).

---

# PART 5 — Notifications & Order-side Analytics

### Notification Service
- Notifies customer (order placed / cancelled by seller / delivery ETA), seller, etc.
- Abstraction over **SMS / Email / push** channels.

### Order-side Analytics (same pattern as search side)
```
 Order events → Kafka → Spark Streaming ──► real-time reports
                          │                 (top ordered items / top revenue category last 1h)
                          ▼
                       Hadoop ──► ALS ML jobs ──► Recommendation Service (Cassandra)
```
- Uses **real purchase data** (strong signal) for recommendations: "ordered X → likely to order Y" + user-similarity (e.g. bought **whiteboard → recommend marker**).

---

# 🧠 Cheat Sheet — Datastore Choices (memorize this table)

| Component | Datastore | **Why** |
|---|---|---|
| **Item Service** | **MongoDB** | Unstructured, per-type varying attributes |
| **Search** | **Elastic Search** | Fast text / fuzzy / filter search |
| **Cart / Wishlist** | **MySQL** (separate clusters) | Simple relational, scale independently |
| **User Service** | **MySQL + Redis** | Source of truth + cache-aside for latency |
| **Order (hot / live)** | **MySQL** | **ACID transactions**, atomic multi-table updates |
| **Order (cold / history)** | **Cassandra** | Few query types, massive data |
| **Payment window guard** | **Redis (TTL + expiry callback)** | Auto-cancel abandoned orders |
| **Inventory** | **MySQL (`count >= 0` constraint)** | DB-enforced concurrency, strong consistency |
| **Event backbone** | **Kafka** | Decouple services, feed analytics |
| **Analytics / ML** | **Spark + Hadoop** | Streaming reports + batch ML (ALS) |
| **Recommendations** | **Cassandra** | Per-user/per-category lookups |

---

# ✅ Interview Talking Points (quick recall)

1. **Two sides:** Search (HA) vs Order (strong consistency) — justify with NFR breakdown.
2. **Serviceability/TAT** precomputed (N×M) in cache — never show undeliverable items.
3. **Inventory blocked pre-payment** via non-negative `count` constraint = elegant concurrency control.
4. **Order state machine:** CREATED → PLACED / CANCELLED.
5. **Redis TTL + expiry callback** = abandoned-payment safety; discuss **race conditions** + **delete-on-payment** optimization + **imprecise expiry** (queue for mission-critical).
6. **Reconciliation Service** = eventual correctness of inventory.
7. **Archival** MySQL→Cassandra keeps hot DB small; **idempotent cron**.
8. **Kafka everywhere** → Spark/Hadoop → ML recommendations (search + purchase signals).
9. **Polyglot persistence:** right DB for each job (Mongo/ES/MySQL/Redis/Cassandra).

---