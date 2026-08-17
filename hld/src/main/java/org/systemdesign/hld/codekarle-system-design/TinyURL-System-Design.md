# TinyURL / URL Shortener — System Design (Last‑Minute Revision Note)

> Source:
- [Github Design Images](https://github.com/codekarle/system-design/blob/master/system-design-prep-material/architecture-diagrams/TinyURL%20System%20Design.png)
- [Blog](https://www.codekarle.com/system-design/TinyUrl-system-design.html)
- [YouTube](https://www.youtube.com/watch?v=AVztRY77xxA&list=PLhgw50vUymycJPN6ZbGTpVKAJ0cL4OEH3&index=11)

---

## 🔑 30-Second Interview Pitch (read this first)

> "A URL shortener maps a **long URL → a unique short 7-char code** and redirects back on lookup.
> The hard part is **generating a unique short code with no collisions, at scale, with no single point of failure**.
> I solve uniqueness with a **Token Service that hands out disjoint number ranges** to each app instance; each instance
> converts numbers **base-10 → base-62** locally (no coordination, no DB check per request).
> Store mappings in **Cassandra** (huge write scale), redirect on read, and pipe request metadata to **Kafka → analytics** asynchronously.
> Non-functional goals: **high availability + very low latency.**"

**Key phrases to drop in interview:** *collision-free*, *base-62 encoding*, *token range allocation*, *no single point of failure*, *single-threaded token counter*, *async/batched analytics*, *lose-tokens-is-fine tradeoff*.

---

## 🧭 How I Approach This (my thought flow)

1. **Clarify FR/NFR** → shorten + redirect; HA + low latency.
2. **Do the math** → decide short-URL **length** from traffic × retention (10 yrs) using base-62.
3. **Naive design** → Short URL Service + DB. Find the flaw → **collisions** across instances.
4. **Reject bad fixes** → DB-check-then-retry (slow), single Redis counter (SPOF), multi-Redis (range mgmt pain).
5. **Final design** → **Token Service** gives each instance a unique **range**; generate locally → no collision, no SPOF.
6. **Pick DB** → Cassandra (scale). Token Service on MySQL (low scale).
7. **Add Analytics** → Kafka (async + batched) → Hadoop/Hive or Spark Streaming.
8. **Discuss tradeoffs** → lost tokens on crash = OK; lost analytics events = OK.

---

## ✅ Requirements

| Type | Requirement |
|------|-------------|
| **Functional** | 1) Long URL → Short URL.  2) Short URL → redirect to Long URL. |
| **Non-Functional** | **Highly Available** (platform component, always up) + **Very Low Latency** (bad UX otherwise). |
| **Assumption** | Store each mapping for at least **10 years**. |

---

## 📐 Step 1 — How Long Should the Short URL Be?

Length depends on **scale**. Estimate total unique URLs over the retention window:

```
Y = X (req/sec) × 60 × 60 × 24 × 365 × 10   ← total unique URLs in 10 years
```

**Character set:** `[a–z] + [A–Z] + [0–9] = 62 chars` (ask interviewer).

Need the smallest `n` where combinations cover `Y`:

```
62^n  ≥  Y     ⇒     n = log₆₂(Y)   (round up)
```

Handy reference numbers:

| Length n | Combinations (62^n) |
|----------|---------------------|
| 6 | ~56–58 **billion** |
| **7** | ~3.5 **trillion**  ✅ (standard choice) |
| 8+ | use if Y is even larger |

➡️ **Pick length = 7** (supports ~3.5 trillion URLs).

---

## 🏗️ Step 2 — Naive Design (and why it breaks)

```
        ┌────────────┐        ┌──────────────────┐        ┌──────────┐
 User → │  Long→Short│  ───►  │ Short URL Service│  ───►  │ Database │
        │     UI     │        │ (many instances) │  ◄───  │          │
        └────────────┘        └──────────────────┘        └──────────┘
```

- Shorten: UI → Service generates code → save in DB → return short URL.
- Redirect: hit short URL → Service fetches long URL → 302 redirect.

**❌ Problem: COLLISION.** With many instances, two instances can both generate `111` for two
different long URLs → one short code pointing to two long URLs. Not allowed.

> We work in **base-10 numbers** for reasoning, then convert to base-62 at the end.

---

## ❌ Rejected Solutions (know why each fails)

| Idea | Why it fails |
|------|--------------|
| **DB check + retry** on collision | Correct but **inefficient** (extra read + retries per request). |
| **Single Redis** `INCR` counter | Every instance hits Redis → **huge load** + **single point of failure** (no backup). Can't scale past 1 machine's latency. |
| **Multiple Redis** | Duplicate numbers if they start from same index. Give each a **series** → works, but adding a 3rd Redis needs a **series manager** → complex. If you need a manager anyway, drop Redis. |

➡️ We want a **predictable, collision-free** generator with **no SPOF**.

---

## ⭐ Step 3 — Final Design: Token Service (THE core idea)

Give every Short URL Service instance a **unique, non-overlapping range of numbers**.
Each instance generates codes **locally** from its range — zero coordination per request.

```
                          ┌─────────────────────────────┐
   Long→Short UI          │        TOKEN SERVICE        │
        │                 │  (single-threaded counter)  │
        ▼                 │   backed by MySQL           │
 ┌──────────────┐         │  range record + assigned?   │
 │ Load Balancer│         └─────────────┬───────────────┘
 └──────┬───────┘        ask range on   │  hands out disjoint ranges
        │                start / when    │  e.g. 1–1000, 1001–2000, 5001–6000 ...
        ▼                near range-end   ▲
 ┌───────────────────────────────────────┴──────┐
 │           Short URL Service (many)            │
 │  in-memory range → next num → base62 → save   │
 └───────────────────────┬───────────────────────┘
                         ▼
                   ┌───────────┐
                   │ Cassandra │  (short ↔ long mapping)
                   └───────────┘
```

### How it works
1. On **startup** (or when **running low**), an instance asks Token Service for a range.
2. Token Service runs **single-threaded**, keeps ranges as MySQL records with an **`assigned` flag**,
   and returns the **first unassigned range** on a **transaction** basis → guaranteed unique.
   - e.g. Instance A gets `1–1000`, B gets `1001–2000`, next request gets `5001–6000`, etc.
3. Per shorten request: instance takes next number in its range → **base-10 → base-62** → **save in Cassandra** → return short URL.
4. When range nears exhaustion (e.g. near `2000`), instance pre-fetches the **next range**.

### Why it's good
- **No collisions** — ranges never overlap (enforced by MySQL/single-thread).
- **No SPOF** — Token Service is called rarely (once every few hours), can run **multiple instances across data centers/geographies**.
- **Low DB load on Token Service** — it's low-scale by design; enlarge ranges (millions, not thousands) to reduce calls.

### ⚠️ Key tradeoff — lost tokens on crash (say this out loud)
If an instance holding `5001–6000` uses a few tokens then **crashes (OOM/kill)**, the **unused tokens are lost forever**
(range is tracked in-memory only). On restart it just grabs a **new range** (e.g. `9001–10000`).

➡️ **That's fine.** Out of ~3.5 trillion tokens, losing a few thousand is *"a bucket out of an ocean."*
Tracking every token would make the system far more complex. **Simplicity > perfect utilization.**

### Redirect (read) path
```
Client → (short URL) → Short URL Service → Cassandra (lookup long URL) → 302 redirect → long URL
```

---

## 🗄️ Database Choice

| Store | Used For | Why |
|-------|----------|-----|
| **Cassandra** | short ↔ long mappings | Handles **trillions** of records + high write throughput easily. |
| **MySQL** | Token Service ranges | Very low scale (few calls/hour); transactions guarantee unique range assignment. |

- MySQL for the *mapping* would struggle at this record count → would need heavy **sharding**. Cassandra avoids that.
- The Token Service's MySQL can be **shared** with the mapping store if you shard MySQL instead.

---

## 📊 Step 4 — Analytics

**Why:** Give URL owners insights (geography, hits, device/user-agent, referring platform) and let *us*
make **data-driven decisions** (e.g. pick primary vs standby data centers by traffic origin).

On each redirect, the request carries useful attributes:
- **Origin/referrer header** (posted on Facebook, LinkedIn, …)
- **User-Agent** (Android / iOS / browser)
- **Source IP** (→ derive country)

### Evolving the analytics write (latency-safe)

```
                       ┌──────────────────────────────────────────────┐
 Redirect request ──►  │            Short URL Service                  │
                       │  1. lookup long URL, return to user (FAST)    │
                       │  2. emit metadata → Kafka  (do NOT block)     │
                       └───────────────────┬──────────────────────────┘
                                           ▼
                                       ┌───────┐
                                       │ Kafka │
                                       └───┬───┘
                          ┌────────────────┴─────────────────┐
                          ▼                                   ▼
                  Hadoop + Hive queries            Spark Streaming (e.g. every 10 min)
                  (batch aggregates)               (windowed aggregates)
                          └────────────────┬─────────────────┘
                                           ▼
                                 Aggregated data store → dashboards
```

**Three levels of optimization (mention the progression):**
1. **Sync write to Kafka on every request** → ❌ adds latency, breaks NFR.
2. **Async write** (separate thread) → ✅ user isn't blocked. Risk: a failed Kafka write loses an event → *OK, no payments involved.*
3. **Batch/aggregate locally then flush** → best. Buffer counts in an in-memory queue/map; flush to Kafka when
   **size threshold** or **time window** (e.g. every 10s) hits → fewer I/O/CPU/network ops → **more throughput per machine**.
   Risk: crash loses a **batch** of events (10–30) → *acceptable, confirm with interviewer.*

**Processing options after Kafka:** Hadoop + **Hive** (batch) *or* **Spark Streaming** (near-real-time windows) → dump aggregates to a store powering user dashboards.

---

## 🧠 One-Glance Cheat Sheet

| Question | Answer |
|----------|--------|
| Short URL length? | **7 chars** (base-62 → ~3.5 trillion) |
| Char set? | `a–z A–Z 0–9` = **62** |
| How to guarantee uniqueness? | **Token Service** hands out disjoint **number ranges** |
| Encoding? | **base-10 → base-62** locally per instance |
| Why not Redis counter? | **SPOF** + load + scaling/series-management pain |
| Mapping DB? | **Cassandra** (trillions of records, write-heavy) |
| Token Service DB? | **MySQL** (low scale, transactional range assignment) |
| Token Service concurrency? | **Single-threaded** → guarantees unique ranges |
| Crash loses tokens? | **Yes, and it's OK** (tiny fraction of 3.5T) |
| Analytics pipeline? | **Async + batched → Kafka → Hadoop/Hive or Spark** |
| Analytics loss OK? | **Yes** (no payments) — confirm with interviewer |
| Core interview rules honored | **No single point of failure**, **low latency**, **HA** |

---

## 📌 Talking Points to Remember
- Always **do the capacity math first** — length is derived, not guessed.
- Name the **collision problem explicitly**, then walk through rejected fixes before the Token Service.
- Emphasize **"no single point of failure"** and **"predictable, collision-free generation."**
- Be ready to **defend the lost-token and lost-analytics tradeoffs** — simplicity vs perfection.
- Keep the **redirect path fast**; push all heavy/optional work (analytics) **off the critical path**.
