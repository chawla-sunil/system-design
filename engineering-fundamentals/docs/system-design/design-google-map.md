# Designing Google Maps — System Design Summary

A revision-friendly summary of how to design a navigation app like Google Maps.

---

## 1. Requirements

### Functional
1. Given point **A → B**, return the **route, distance, and ETA**.
2. Optionally return **2–3 alternate routes** (min distance vs min time).
3. **Pluggable model** — easy to add traffic, weather, accidents, road blocks without rewriting the core.
4. **Road discovery** — combine government-sourced road data with organic data from user movement patterns (to detect new roads, lane counts, one-ways, etc.).

### Non-Functional
- **Highly available** (never down).
- **Good-enough accuracy** — best route not required, but not terrible.
- **Latency** — a few seconds is acceptable; 15s is not.

### Scale
- ~1 billion MAU → **5–10 billion route requests/month**.
- ~5 million companies (Uber, etc.) also using it via API.

### Why it's hard
- ~50–100M+ roads worldwide → graph of ~50M vertices, hundreds of millions of edges.
- Attributes like traffic, weather, road quality are **hard to quantify**.
- Accidents, closures are **unpredictable**.

---

## 2. Core Modeling Concepts

### Segment
A **small area** (e.g., 1km × 1km) with 4 corner lat/longs and a unique ID. The whole globe is divided into segments.
- Given a lat/long, we can quickly find which segment it belongs to.
- Distance between two segments ≈ aerial distance between their centers.

### Road as a Graph
- **Vertex** = junction, **Edge** = road between junctions.
- Edges carry **multiple weights**: distance, ETA, average speed, etc.
- Edges are **directed** — A→B and B→A may have different ETAs; infinite ETA = one-way.

---

## 3. Route Finding — Dynamic Programming Approach

Solve at increasing scales: **within a segment → across segments → across mega-segments**.

### 3.1 Within a Segment
- Run **Dijkstra / Bellman-Ford** for shortest path between two junctions.
- Precompute **all-pairs shortest paths** with **Floyd-Warshall** and cache the results as "calculated edges".
- Also cache shortest paths from every junction to each **segment exit point** (`S1E1`, `S1E2`, …).

### 3.2 If the point is not a junction
Point X sits on a road between junctions A and B (distances `i` and `j`).
Distance(X → C) = min(`i + dist(A,C)`, `j + dist(B,C)`).
Similarly for X → Y where Y is mid-road on another segment.

### 3.3 Across Multiple Segments
Running Dijkstra on the whole city is too slow.
1. Compute aerial distance A→B (e.g., 10 km). Add buffer → search only ~20 segments in each direction.
2. Build a **runtime graph** using **only entry/exit points** of segments + cached exit-to-exit distances. We don't care what happens inside each intermediate segment.
3. Run Dijkstra on this small graph (a few hundred edges).

### 3.4 Across Cities / Countries — Mega-Segments
- Group many segments into a **mega-segment** with its own entry/exit points.
- Repeat the same trick recursively; **3 levels of nesting** is usually enough.
- To go country-to-country: connect exit points of mega-segments → drill down as needed.

---

## 4. Weights, ETA & Traffic

### What are edge weights?
- **Distance** (fixed).
- **ETA** under normal conditions.
- **Average speed** = distance / ETA.

### Golden Rule: Traffic/weather are NOT weights
They are **inputs that adjust average speed**. This keeps the graph algorithm untouched when adding new signals (weather, accidents, construction).
```
avg_speed = f(traffic, weather, accidents, roadblocks, ...)
ETA       = distance / avg_speed
```

### Quantifying unquantifiable signals — Tiers
Traffic = {low, medium, high}, Weather = {good, bad}, etc.
Each transition adjusts avg_speed by a percentage (e.g., low → medium reduces speed by 20%).

### Preferred source: Real user data
For areas with active users, traffic ETA is derived from **real users' movement times** (they follow a normal distribution → use mean ± 1 std dev). Third-party traffic sources (Waze, etc.) are only needed where user data is missing.

### Historical ETA
Cache ETA by **day-of-week × hour-of-day** (e.g., Monday 5pm vs Sunday 5pm) as a fallback predictor.

---

## 5. Propagating Weight Changes (Bubble-Up)

When a real road's ETA changes:
1. Update the raw edge.
2. Update every **cached calculated edge** in that segment that used this road (small delta = just add the diff).
3. Update the **segment's exit-to-exit** cached values.
4. If change is large (e.g., **>30%** configurable threshold), **rerun Floyd-Warshall** in that segment — the best path may have completely changed.
5. Bubble up recursively into **mega-segment** cached routes that include this segment's affected paths.

Stop bubbling at whichever level the road no longer participates in.

---

## 6. Architecture

**Color convention:** 🟢 user devices · ⚫ LB / auth / reverse proxy · 🔵 our services · 🔴 infra/DBs (Cassandra, Kafka, Hadoop, Redis).

### Flow A — Continuous Location Ingestion (all users)

```
User device ──WebSocket──► WebSocket Handler ──► Location Service ──► Cassandra (permanent user pings)
                                │                       │
                          WS Manager                    ▼
                          (Redis: user↔handler)       Kafka (raw pings)
                                                        │
                                                        ▼
                                             Spark Streaming Consumer
```

- **WebSocket** for bidirectional comms and to avoid reconnects. Stationary devices send pings less often to save battery.
- **WS Manager** tracks which user is on which handler (state in Redis).
- **Location Service** persists pings in Cassandra + publishes to Kafka.

### Spark Streaming Jobs (fed by Kafka)
| Job | What it does |
|---|---|
| **New Road Detector** | Detects user movement in unmapped areas → publishes to a Kafka topic. |
| **Average Speed Job** | Rolling 15–20 min window per segment → updates edge weights (real-time traffic proxy). |
| **Hotspot Identifier** | Sudden gatherings → predicts future traffic. |
| **Road Classifier** (ML on Hadoop) | Infers lanes, one-way vs two-way. |
| **Vehicle Identifier** | Infers 2-wheeler / 4-wheeler / bus (from stop patterns, acceleration, bumpiness). |

Each job writes results to a dedicated Kafka topic. **Map Update Service** and **Traffic Update Service** consume these topics and persist to Cassandra via the **Graph Processing Service**.

### Flow B — Navigation Request

```
User device ──► Map Service ──► Graph Processing Service ──► Segment Service (Cassandra)
                                        │                    Cassandra (graph + live traffic)
                                        ├─► Historical Data Service (Cassandra: day×hour ETA)
                                        └─► Third-Party Data Manager (pushes traffic/weather in)
                                        
                Area Search Service ──► Elasticsearch (fuzzy search for places → lat/long)
                Navigation Tracking Service ──► Kafka (route deviation, actual routes taken)
```

- **Area Search Service** — fuzzy place search (Elasticsearch), address-to-lat/long resolution.
- **Map Service** — thin interface layer; also handles rate-limiting for company API clients.
- **Graph Processing Service** — the brain:
  1. Look up segments for start & end from **Segment Service**.
  2. Same segment? Check cache → else run Dijkstra.
  3. Different segments? Pull sub-graph (roads, entry/exit points, live traffic) → run Dijkstra on exit-point graph.
  4. Fall back to **Historical Data Service** if live data missing.
- **Third-Party Data Manager** — pushes external traffic/weather signals into the graph (does not query at runtime).
- **Navigation Tracking Service** — alerts device on route deviation; logs actual routes to Kafka for analytics.

### Flow C — Analytics (on Kafka + Hadoop)
- **ETA accuracy** — compare predicted vs actual.
- **Third-party accuracy** — score each provider; drop bad ones.
- **Route quality** — are recommended routes being followed?
- **Hotspots, home/work locations, user profiling** (frequent pubs → social, frequent weekend trips → traveler).
- **Turn-by-turn voice guidance** ("turn right in 90m onto MG Road") — inferred from streaming location.

---

## 7. Special Case — Disputed Regions
Google shows **different country boundaries based on the requester's country** (e.g., Kashmir shown differently to users in India, Pakistan, China; disputed portions shown as dotted lines). Good to know, usually out of interview scope.

---

## 8. Quick Recall Cheatsheet

- **Segment** = small tile of the world; **Mega-Segment** = group of segments.
- Route search = **recursive Dijkstra** on exit-point graphs at increasing levels.
- Precompute **Floyd-Warshall** within each segment; cache exit-to-exit paths.
- **Traffic/weather adjust avg_speed**, never edge weights directly.
- Prefer **real user pings** for ETA; fall back to third-party and historical data.
- **Bubble up** edge changes; recompute segment only if change > threshold (~30%).
- **Cassandra** for graph + pings, **Redis** for WS state, **Kafka** for async, **Elasticsearch** for search, **Hadoop + Spark** for ML/analytics.
