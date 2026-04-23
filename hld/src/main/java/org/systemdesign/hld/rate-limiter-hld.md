# Rate Limiter — High-Level Design (HLD Interview)

> **Simulated Interview Duration:** 1 hour  
> **Candidate Experience Level:** 6–7 years  
> **Interviewer Prompt:** "Design a Rate Limiter / API Rate Limiting System."

---

## Table of Contents

1. [Step 1 — Clarify Requirements (5–7 min)](#step-1--clarify-requirements)
2. [Step 2 — Back-of-the-Envelope Estimation (3–5 min)](#step-2--back-of-the-envelope-estimation)
3. [Step 3 — Where to Place the Rate Limiter? (5 min)](#step-3--where-to-place-the-rate-limiter)
4. [Step 4 — Rate Limiting Algorithms Deep Dive (12–15 min)](#step-4--rate-limiting-algorithms-deep-dive)
5. [Step 5 — High-Level Architecture (10–12 min)](#step-5--high-level-architecture)
6. [Step 6 — Rate Limiting Rules & Configuration (5 min)](#step-6--rate-limiting-rules--configuration)
7. [Step 7 — Detailed Component Design (8–10 min)](#step-7--detailed-component-design)
8. [Step 8 — Distributed Rate Limiting (7–8 min)](#step-8--distributed-rate-limiting)
9. [Step 9 — Handling Edge Cases & Race Conditions (5 min)](#step-9--handling-edge-cases--race-conditions)
10. [Step 10 — Monitoring, Alerting & Analytics (3–5 min)](#step-10--monitoring-alerting--analytics)
11. [Step 11 — Summary & Trade-offs (3–5 min)](#step-11--summary--trade-offs)

---

## Step 1 — Clarify Requirements

> **"Before I start designing, let me clarify the scope and constraints with you."**

### What is a Rate Limiter?

A rate limiter controls the rate of traffic sent by a client or a service. It limits the number of client requests allowed over a specified period. If the API request count exceeds the threshold, all excess calls are throttled or dropped.

**Examples in real world:**
- A user can write no more than 2 posts per second.
- A user can create a maximum of 10 accounts per day from the same IP.
- A user can claim rewards no more than 5 times per week from the same device.
- GitHub API → 5,000 requests/hour per authenticated user.
- Twitter API → 300 tweets per 3 hours.

### Questions I'd Ask the Interviewer

| # | Question | Expected Answer |
|---|----------|-----------------|
| 1 | Is this a client-side or server-side rate limiter? | **Server-side** (client-side is unreliable — can be bypassed) |
| 2 | What should we throttle on? (User ID, IP, API key?) | **Flexible** — support multiple strategies |
| 3 | What's the scale? How many users/requests? | **Large scale** — millions of requests per second |
| 4 | Is the system distributed (multiple servers/data centers)? | **Yes**, distributed environment |
| 5 | Is this a standalone service or embedded in app code? | Could be either — **middleware / separate service** preferred |
| 6 | Should throttled users be informed? | **Yes** — return appropriate HTTP status (429) with headers |
| 7 | Do we need different rate limits for different APIs? | **Yes** — configurable rate limiting rules |

### Functional Requirements

| # | Requirement | Notes |
|---|-------------|-------|
| FR-1 | **Limit excessive requests** — Accurately throttle requests exceeding a defined rate | Core feature |
| FR-2 | **Configurable rules** — Support per-user, per-IP, per-API-endpoint rate limits | Core feature |
| FR-3 | **Inform users** — Return HTTP `429 Too Many Requests` with rate limit headers | Core feature |
| FR-4 | **Multiple throttling strategies** — Support multiple identification keys (user ID, IP, API key) | Core feature |
| FR-5 | **Flexible time windows** — Support second, minute, hour, day-level windows | Configurable |

### Non-Functional Requirements

| # | Requirement | Target |
|---|-------------|--------|
| NFR-1 | **Low Latency** | Rate limiter should NOT add significant latency. Decision < 1–2 ms |
| NFR-2 | **High Availability** | 99.99% — If rate limiter is down, system should still work (fail-open / fail-close decision) |
| NFR-3 | **High Throughput** | Must handle millions of requests/sec |
| NFR-4 | **Memory Efficient** | Minimal memory footprint per user/key |
| NFR-5 | **Distributed** | Must work consistently across multiple servers and data centers |
| NFR-6 | **Fault Tolerant** | Failure in rate limiter should not bring down the entire API |
| NFR-7 | **Accurate** | Should not allow significantly more or fewer requests than the defined threshold |

### Out of Scope

- DDoS protection at the network layer (L3/L4) — that's typically handled by CDN/WAF
- Business logic for billing or quota management
- Client-side rate limiting

---

## Step 2 — Back-of-the-Envelope Estimation

> **"Let me do quick math to understand the scale we're dealing with."**

### Traffic Estimates

| Metric | Value |
|--------|-------|
| Total API servers | ~100 servers |
| Requests per second (total) | **~1 million RPS** |
| Unique users per day | ~10 million |
| Unique IPs per day | ~50 million |
| Rate limit checks per second | **= Total RPS = ~1 million checks/sec** |

### Memory Estimates

Each rate limiter entry needs to store:

| Field | Size |
|-------|------|
| User/Client Key (hash) | 8 bytes |
| Counter(s) | 8 bytes |
| Timestamp / Window start | 8 bytes |
| Expiration / TTL metadata | 8 bytes |
| **Total per entry** | **~32 bytes** |

| Metric | Value |
|--------|-------|
| Active users in a window | ~10 million |
| Memory per user | ~32 bytes |
| Total memory | 10M × 32B = **~320 MB** |
| With overhead (2x) | **~640 MB** |

> ✅ This fits comfortably in a **single Redis instance** (typically 16–64 GB). Even with multiple rules per user, we're well within memory bounds. Redis can handle **100K–500K operations/sec** per instance, so we'll need a small Redis cluster.

### Bandwidth Estimates

| Metric | Value |
|--------|-------|
| Data per rate limit check (request) | ~256 bytes |
| Incoming bandwidth | 1M × 256B = **~256 MB/sec** |

---

## Step 3 — Where to Place the Rate Limiter?

> **"An important design decision is WHERE to put the rate limiter in the architecture."**

### Option 1: Client-Side

```
[Client] → (rate limit logic here) → [Server]
```

❌ **Not recommended** — Client-side can be forged or bypassed. Unreliable since we don't control client behavior.

### Option 2: Server-Side (In Application Code)

```
[Client] → [Server (rate limit logic embedded)]
```

⚠️ **Tightly coupled** — Every service needs to implement its own rate limiting logic. Hard to maintain and change rules.

### Option 3: Middleware / API Gateway (✅ Recommended)

```
[Client] → [API Gateway / Rate Limiter Middleware] → [Server]
```

✅ **Best approach** for most systems:
- Rate limiter sits as a **middleware** layer between client and server.
- Can be part of an **API Gateway** (AWS API Gateway, Kong, Envoy, Nginx, Zuul).
- **Centralized** — One place to manage all rate limiting rules.
- **Decoupled** — Application code doesn't need rate limiting logic.

### Guidance for Choosing

| Factor | API Gateway | Custom Middleware |
|--------|-------------|-------------------|
| Already using an API Gateway? | ✅ Add it there | Build your own |
| Need fine-grained control? | Limited | ✅ Full control |
| Engineering resources? | ✅ Managed service | Need dedicated team |
| Algorithm flexibility? | Limited to what gateway supports | ✅ Full flexibility |
| Microservices architecture? | ✅ Perfect fit | Also works |

> **My recommendation:** If we already have an API Gateway, plug rate limiting there. Otherwise, build a lightweight **rate limiter middleware service** that sits in front of application servers.

---

## Step 4 — Rate Limiting Algorithms Deep Dive

> **"Now let me walk through the core algorithms. There are 5 major algorithms, each with different trade-offs."**

---

### Algorithm 1: Token Bucket 🪣

**How it works:**
- A bucket holds tokens with a **maximum capacity**.
- Tokens are added at a **fixed refill rate** (e.g., 10 tokens/sec).
- Each request **consumes one token**.
- If the bucket is **empty** → request is **rejected**.
- If tokens are available → request is **allowed**, token is consumed.

```
Bucket Capacity = 4 tokens
Refill Rate = 2 tokens/sec

Time 0s: [●●●●] → 4 tokens (full)
Request 1: [●●●○] → allowed (3 left)
Request 2: [●●○○] → allowed (2 left)
Request 3: [●○○○] → allowed (1 left)
Request 4: [○○○○] → allowed (0 left)
Request 5: [○○○○] → ❌ REJECTED (no tokens)

Time 1s: [●●○○] → 2 tokens refilled
Request 6: [●○○○] → allowed (1 left)
```

**Data structure per key:**
```
{
  "key": "user:123",
  "tokens": 3,           // current available tokens
  "last_refill_ts": 1690000000  // last refill timestamp
}
```

**Pseudocode:**
```
function allowRequest(key):
    bucket = getBucket(key)
    now = currentTime()
    
    // Refill tokens based on elapsed time
    elapsed = now - bucket.last_refill_ts
    tokens_to_add = elapsed * refill_rate
    bucket.tokens = min(bucket.tokens + tokens_to_add, max_capacity)
    bucket.last_refill_ts = now
    
    if bucket.tokens >= 1:
        bucket.tokens -= 1
        return ALLOW
    else:
        return REJECT
```

| Pros | Cons |
|------|------|
| ✅ Simple to implement | ⚠️ Two parameters to tune (capacity + refill rate) |
| ✅ Memory efficient (2 values per key) | |
| ✅ Allows **bursts** up to bucket capacity | |
| ✅ Used by **Amazon** and **Stripe** | |

---

### Algorithm 2: Leaking Bucket 🚿

**How it works:**
- Requests are added to a **FIFO queue** of fixed size.
- Requests are **processed at a fixed rate** (leak rate) from the queue.
- If the queue is **full** → new requests are **dropped**.
- Smooths out bursts into a steady flow.

```
Queue Capacity = 4, Processing Rate = 2/sec

Queue: [R1, R2, R3, R4]  → Full
New Request R5 → ❌ DROPPED (queue full)

Processing: R1 processed, R2 processed (2/sec)
Queue: [R3, R4, __, __]  → Space available
New Request R5 → [R3, R4, R5, __] → Accepted
```

**Data structure per key:**
```
{
  "key": "user:123",
  "queue_size": 2,          // current items in queue
  "last_leak_ts": 1690000000
}
```

| Pros | Cons |
|------|------|
| ✅ Smooth, steady output rate | ❌ Bursts fill up queue; recent requests may starve |
| ✅ Memory efficient (fixed queue) | ❌ Two parameters to tune (queue size + leak rate) |
| ✅ Good for systems needing **steady throughput** | ❌ Not ideal if bursts are desired |
| ✅ Used by **Shopify** | |

---

### Algorithm 3: Fixed Window Counter 🪟

**How it works:**
- Divide time into **fixed windows** (e.g., 0:00–1:00, 1:00–2:00).
- Each window has a **counter** starting at 0.
- Each request **increments** the counter for the current window.
- If counter > threshold → **reject**.

```
Window Size = 1 minute, Limit = 5

[00:00 - 01:00]: ●●●●● (5 requests) → All allowed
[00:00 - 01:00]: ●●●●●X (6th request) → ❌ REJECTED

[01:00 - 02:00]: Counter resets to 0
[01:00 - 02:00]: ●●● (3 requests) → All allowed
```

**⚠️ Boundary Problem:**

```
Window: [00:00 - 01:00] limit = 5
Window: [01:00 - 02:00] limit = 5

If 5 requests come at 00:59 and 5 more at 01:01:
→ 10 requests in 2 seconds! (crossing the boundary)
→ Actual rate = 2x the intended limit for a short period!
```

| Pros | Cons |
|------|------|
| ✅ Very simple, memory efficient | ❌ **Boundary burst problem** — spike at window edges |
| ✅ Easy to understand | ❌ Can allow 2x rate limit at window boundaries |
| ✅ Resetting counters is inexpensive | |

---

### Algorithm 4: Sliding Window Log 📜

**How it works:**
- Keep a **log (sorted set) of timestamps** for every request.
- When a new request arrives:
  1. Remove all timestamps **older than the window**.
  2. Count remaining timestamps.
  3. If count < limit → **allow** and add timestamp.
  4. If count ≥ limit → **reject**.

```
Limit = 3 requests per minute

Timestamps Log: [00:15, 00:35, 00:50]
New request at 01:05:
  → Remove timestamps before 00:05 (01:05 - 60s)
  → Remove 00:15? No, wait... remove before 00:05
  → Remaining: [00:15, 00:35, 00:50] → count = 3
  → 3 >= 3 → ❌ REJECTED

New request at 01:20:
  → Remove timestamps before 00:20
  → Remove 00:15
  → Remaining: [00:35, 00:50] → count = 2
  → 2 < 3 → ✅ ALLOWED
  → Log: [00:35, 00:50, 01:20]
```

**Data structure (Redis Sorted Set):**
```
ZADD  rate_limit:user:123  <timestamp>  <request_id>
ZREMRANGEBYSCORE  rate_limit:user:123  0  (now - window_size)
ZCARD  rate_limit:user:123  → count
```

| Pros | Cons |
|------|------|
| ✅ **Very accurate** — no boundary problem | ❌ **Memory heavy** — stores every timestamp |
| ✅ Sliding window is precise | ❌ For high-traffic users, log can grow very large |
| | ❌ Expensive cleanup of old entries |

---

### Algorithm 5: Sliding Window Counter 🏆 (Recommended)

**How it works:**
- **Hybrid** of Fixed Window Counter + Sliding Window Log.
- Keeps counters for the **current window** and the **previous window**.
- Uses a **weighted average** based on the overlap with the previous window.

```
Limit = 10 requests per minute
Previous window (00:00–01:00) counter = 8
Current window  (01:00–02:00) counter = 3
Current time: 01:15 (25% into current window)

Weighted count = prev_count × (1 - position_in_current_window) + curr_count
               = 8 × (1 - 0.25) + 3
               = 8 × 0.75 + 3
               = 6 + 3
               = 9

9 < 10 → ✅ ALLOWED
```

```
Visualization:

Previous Window          Current Window
[00:00 -------- 01:00]  [01:00 --- 01:15 --- 02:00]
   Count = 8                Count = 3
                              ↑ we are here (25%)

Overlap from prev = 75% of previous window overlaps
Weighted = 8 * 0.75 + 3 = 9
```

**Data structure per key:**
```
{
  "key": "user:123",
  "prev_count": 8,
  "prev_window_start": 1690000000,
  "curr_count": 3,
  "curr_window_start": 1690000060
}
```

| Pros | Cons |
|------|------|
| ✅ **Smooths boundary problem** of fixed window | ⚠️ Approximation — not 100% precise (but very close, ~0.003% error) |
| ✅ **Memory efficient** — only 2 counters per key | |
| ✅ **Best balance** of accuracy and performance | |
| ✅ Used by **Cloudflare** | |

---

### Algorithm Comparison Matrix

| Algorithm | Memory | Accuracy | Burst Handling | Complexity | Best For |
|-----------|--------|----------|----------------|------------|----------|
| **Token Bucket** | Low (2 values) | Good | ✅ Allows controlled bursts | Low | APIs needing burst tolerance (AWS, Stripe) |
| **Leaking Bucket** | Low (queue counter) | Good | ❌ Smooths out bursts | Low | Steady throughput systems (Shopify) |
| **Fixed Window Counter** | Very Low (1 counter) | ⚠️ Boundary issue | ❌ Spike at edges | Very Low | Simple use cases, non-critical |
| **Sliding Window Log** | High (all timestamps) | ✅ Exact | ✅ Precise | Medium | When accuracy is critical |
| **Sliding Window Counter** | Low (2 counters) | Very Good (~99.97%) | ✅ Good | Low | Production systems (Cloudflare) |

> **My choice for this design: Token Bucket + Sliding Window Counter**.  
> Token Bucket for its simplicity and burst tolerance at the API Gateway level.  
> Sliding Window Counter for per-user, per-endpoint fine-grained limiting.

---

## Step 5 — High-Level Architecture

> **"Let me draw the high-level architecture now."**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                        │
│                (Web / Mobile / Third-party APIs)                            │
└─────────────┬───────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        LOAD BALANCER (L7)                                   │
│                    (AWS ALB / Nginx / HAProxy)                              │
└─────────────┬───────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY / RATE LIMITER MIDDLEWARE                    │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                     Rate Limiter Service                               │ │
│  │                                                                        │ │
│  │  1. Extract client key (user_id / IP / API key)                       │ │
│  │  2. Fetch rate limit rules from Rules Engine                          │ │
│  │  3. Check counter in Redis Cache                                       │ │
│  │  4. ALLOW → Forward to API Server                                     │ │
│  │     REJECT → Return 429 + Rate Limit Headers                          │ │
│  │                                                                        │ │
│  └────────────┬──────────────────────┬────────────────────────────────────┘ │
│               │                      │                                      │
│       ┌───────▼───────┐     ┌────────▼────────┐                            │
│       │  Rules Engine │     │   Redis Cluster  │                            │
│       │  (Config DB)  │     │  (Counters/State)│                            │
│       │               │     │                  │                            │
│       │  YAML/JSON    │     │  - Token counts  │                            │
│       │  rules stored │     │  - Window counts │                            │
│       │  on disk/DB   │     │  - Timestamps    │                            │
│       └───────────────┘     └──────────────────┘                            │
│                                                                             │
└─────────────┬───────────────────────────────────────────────────────────────┘
              │
              │ (Request ALLOWED)
              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          API SERVERS                                         │
│              (Microservices: Auth, Orders, Payments, etc.)                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Request Flow (Step by Step)

```
1. Client sends HTTP request
         │
         ▼
2. Load Balancer routes to API Gateway
         │
         ▼
3. Rate Limiter Middleware intercepts the request
         │
         ├─── 3a. Extract throttle key:
         │         - Authorization header → user_id
         │         - X-Forwarded-For → IP address
         │         - API-Key header → API key
         │
         ├─── 3b. Fetch applicable rules from Rules Engine
         │         - GET /api/v1/orders → "100 req/min per user"
         │         - POST /api/v1/login → "5 req/min per IP"
         │
         ├─── 3c. Check Redis for current counter / token state
         │         │
         │         ├── Under limit? → ✅ Increment counter, ALLOW request
         │         │                      Set response headers:
         │         │                      X-RateLimit-Remaining: 95
         │         │                      X-RateLimit-Limit: 100
         │         │                      X-RateLimit-Reset: 1690000060
         │         │
         │         └── Over limit? → ❌ REJECT request
         │                             Return HTTP 429
         │                             X-RateLimit-Remaining: 0
         │                             X-RateLimit-Retry-After: 35
         │
         ▼
4. If ALLOWED → Forward request to API Server
5. API Server processes and returns response to client
```

### HTTP Response Headers

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1690000060
Retry-After: 35

{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded. Try again in 35 seconds.",
    "retry_after": 35
  }
}
```

When request is **allowed**:
```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1690000060
```

---

## Step 6 — Rate Limiting Rules & Configuration

> **"The rules engine is critical — it defines WHO gets limited, on WHICH endpoint, and HOW MUCH."**

### Rules Configuration (YAML)

```yaml
# rate-limit-rules.yaml

rules:
  # Global default
  - name: "global-default"
    key: "client_ip"
    endpoint: "*"
    limit: 1000
    window: 60          # seconds
    algorithm: "sliding_window_counter"

  # Authentication endpoints (stricter)
  - name: "login-rate-limit"
    key: "client_ip"
    endpoint: "POST /api/v1/auth/login"
    limit: 5
    window: 60
    algorithm: "sliding_window_counter"

  # User-level API rate limit
  - name: "user-api-limit"
    key: "user_id"
    endpoint: "/api/v1/*"
    limit: 100
    window: 60
    algorithm: "token_bucket"
    burst: 20           # token bucket capacity for bursts

  # Write-heavy endpoints
  - name: "create-order-limit"
    key: "user_id"
    endpoint: "POST /api/v1/orders"
    limit: 10
    window: 60
    algorithm: "token_bucket"
    burst: 5

  # Premium tier (higher limits)
  - name: "premium-user-limit"
    key: "user_id"
    endpoint: "/api/v1/*"
    limit: 5000
    window: 60
    tier: "premium"
    algorithm: "token_bucket"
    burst: 100
```

### Rules Priority

```
Most Specific Rule Wins:

1. Exact endpoint + exact user   → "POST /api/v1/orders" for user:123
2. Exact endpoint + user tier    → "POST /api/v1/orders" for premium users
3. Exact endpoint + any user     → "POST /api/v1/orders" for all users
4. Wildcard endpoint + user tier → "/api/v1/*" for premium users
5. Wildcard endpoint + any user  → "/api/v1/*" for all users
6. Global default                → "*" for all
```

### Rules Storage & Reload

```
┌─────────────────────┐      ┌──────────────────┐
│  Rules YAML/JSON    │      │  Config Service   │
│  (Git repo / S3)    │─────▶│  (etcd / Consul / │
│                     │      │   ZooKeeper)       │
└─────────────────────┘      └────────┬───────────┘
                                      │
                                      │ Watch/Poll (every 30s)
                                      ▼
                             ┌──────────────────┐
                             │  Rate Limiter     │
                             │  (in-memory cache │
                             │   of rules)       │
                             └──────────────────┘
```

- Rules are stored in a **config store** (etcd, Consul, or a database).
- Rate limiter services **poll or watch** for changes.
- Rules are cached **in-memory** on each rate limiter instance for speed.
- Hot reload without restarting the service.

---

## Step 7 — Detailed Component Design

> **"Let me go deeper into each component."**

### 7.1 — Redis Data Model

**For Token Bucket:**
```
Key:    rate_limit:token_bucket:{user_id}:{endpoint_hash}
Value:  HASH
        - tokens: 15          (current available tokens)
        - last_refill: 1690000050  (Unix timestamp of last refill)
TTL:    120 seconds (auto-cleanup)
```

**For Sliding Window Counter:**
```
Key:    rate_limit:swc:{user_id}:{endpoint_hash}:{window_start}
Value:  INTEGER (counter)
TTL:    2 * window_size (keep current + previous window)

Example:
  rate_limit:swc:user123:/api/orders:1690000000 → 8   (prev window)
  rate_limit:swc:user123:/api/orders:1690000060 → 3   (curr window)
```

### 7.2 — Redis Operations (Atomic with Lua Script)

> **Critical: All rate limiter operations must be ATOMIC to prevent race conditions.**

**Token Bucket Lua Script:**
```lua
-- token_bucket.lua
-- KEYS[1]: bucket key
-- ARGV[1]: max_tokens (capacity)
-- ARGV[2]: refill_rate (tokens per second)
-- ARGV[3]: current_timestamp
-- ARGV[4]: tokens_requested (usually 1)

local key = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- Initialize if first request
if tokens == nil then
    tokens = max_tokens
    last_refill = now
end

-- Refill tokens based on elapsed time
local elapsed = math.max(0, now - last_refill)
local new_tokens = math.min(max_tokens, tokens + (elapsed * refill_rate))

-- Check if request can be allowed
if new_tokens >= requested then
    new_tokens = new_tokens - requested
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate) * 2)
    return {1, new_tokens}  -- {allowed, remaining}
else
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate) * 2)
    return {0, new_tokens}  -- {rejected, remaining}
end
```

**Sliding Window Counter Lua Script:**
```lua
-- sliding_window_counter.lua
-- KEYS[1]: current window key
-- KEYS[2]: previous window key
-- ARGV[1]: limit
-- ARGV[2]: window_size (seconds)
-- ARGV[3]: current_timestamp

local curr_key = KEYS[1]
local prev_key = KEYS[2]
local limit = tonumber(ARGV[1])
local window_size = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local curr_window_start = now - (now % window_size)
local position = (now - curr_window_start) / window_size

local prev_count = tonumber(redis.call('GET', prev_key) or 0)
local curr_count = tonumber(redis.call('GET', curr_key) or 0)

-- Weighted count
local weighted = math.floor(prev_count * (1 - position) + curr_count)

if weighted < limit then
    redis.call('INCR', curr_key)
    redis.call('EXPIRE', curr_key, window_size * 2)
    return {1, limit - weighted - 1}  -- {allowed, remaining}
else
    return {0, 0}  -- {rejected, remaining}
end
```

### 7.3 — Rate Limiter Middleware (Java Pseudocode)

```java
@Component
public class RateLimiterFilter implements Filter {

    private final RedisTemplate<String, String> redis;
    private final RulesEngine rulesEngine;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 1. Extract throttle key
        String clientKey = extractClientKey(request);  // user_id, IP, or API key

        // 2. Find applicable rule
        RateLimitRule rule = rulesEngine.findRule(
            request.getMethod(),
            request.getRequestURI(),
            clientKey
        );

        if (rule == null) {
            chain.doFilter(req, res);  // No rule → allow
            return;
        }

        // 3. Check rate limit (Redis Lua script)
        RateLimitResult result = checkRateLimit(clientKey, rule);

        // 4. Set response headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTimestamp()));

        if (result.isAllowed()) {
            chain.doFilter(req, res);  // ✅ Forward request
        } else {
            // ❌ Reject
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfter()));
            response.getWriter().write(
                "{\"error\": \"Rate limit exceeded. Retry after " 
                + result.getRetryAfter() + " seconds.\"}"
            );
        }
    }

    private String extractClientKey(HttpServletRequest request) {
        // Priority: API Key > User ID > IP Address
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) return "apikey:" + apiKey;

        String userId = request.getHeader("X-User-Id");
        if (userId != null) return "user:" + userId;

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) ip = request.getRemoteAddr();
        return "ip:" + ip;
    }
}
```

---

## Step 8 — Distributed Rate Limiting

> **"In a distributed system with multiple servers and data centers, rate limiting becomes significantly more complex."**

### The Problem

```
                        ┌───────────────┐
            ┌──────────▶│  Server 1     │── Local counter: 3
            │           │  Rate Limiter │
Client ─────┤           └───────────────┘
(limit=5)   │
            │           ┌───────────────┐
            └──────────▶│  Server 2     │── Local counter: 3
                        │  Rate Limiter │
                        └───────────────┘

Total actual requests = 6! But each server thinks it's under limit.
```

### Solution 1: Centralized Redis (✅ Primary Approach)

```
┌────────────┐     ┌────────────┐     ┌────────────┐
│  Server 1  │     │  Server 2  │     │  Server 3  │
│  (Gateway) │     │  (Gateway) │     │  (Gateway) │
└─────┬──────┘     └─────┬──────┘     └─────┬──────┘
      │                  │                   │
      └──────────────────┼───────────────────┘
                         │
                         ▼
              ┌──────────────────┐
              │   Redis Cluster   │
              │  (Central State)  │
              │                  │
              │  Atomic Lua      │
              │  Scripts for     │
              │  check+increment │
              └──────────────────┘
```

✅ Single source of truth  
✅ Atomic operations via Lua scripts  
⚠️ Redis becomes a single point of dependency (mitigate with Redis Cluster + Sentinel)  
⚠️ Adds network latency (~1–2 ms per check)

### Solution 2: Multi-Data Center — Eventual Consistency

For **multi-region** deployments:

```
┌─────────────────────┐         ┌─────────────────────┐
│   Data Center 1     │         │   Data Center 2     │
│   (US-East)         │         │   (EU-West)         │
│                     │         │                     │
│  ┌───────────────┐  │         │  ┌───────────────┐  │
│  │ Rate Limiter  │  │         │  │ Rate Limiter  │  │
│  │ + Local Redis │  │◄───────▶│  │ + Local Redis │  │
│  └───────────────┘  │  Sync   │  └───────────────┘  │
│                     │ (async) │                     │
└─────────────────────┘         └─────────────────────┘
```

**Approaches:**

| Approach | How | Trade-off |
|----------|-----|-----------|
| **Sticky Sessions** | Route same user to same DC always | ✅ Accurate per user, ❌ Not truly distributed |
| **Relaxed Sync** | Each DC has local counter, sync periodically (every 5s) | ✅ Low latency, ⚠️ May slightly exceed limit |
| **Split Limit** | Divide limit across DCs (e.g., 50 + 50 = 100 total) | ✅ Simple, ❌ Under-utilizes if traffic is skewed |
| **Centralized Store** | All DCs talk to one Redis region | ✅ Accurate, ❌ Cross-region latency |

> **Recommendation:** For most systems, use a **centralized Redis Cluster** with replicas in each region. Accept slight inaccuracy (~5%) for cross-DC traffic. For strict limits (payment APIs), use sticky sessions.

### Solution 3: Sliding Window with Redis Cluster

```
Redis Cluster (6 nodes: 3 masters + 3 replicas)

Master 1 (slots 0-5460)     ──► Replica 1
Master 2 (slots 5461-10922) ──► Replica 2
Master 3 (slots 10923-16383)──► Replica 3

Key: rate_limit:user:123 → Hashed to slot 8745 → Master 2
Key: rate_limit:user:456 → Hashed to slot 2301 → Master 1

Each user's rate limit state lives on ONE master → No consistency issues!
```

---

## Step 9 — Handling Edge Cases & Race Conditions

> **"Let me cover the edge cases that would come up in production."**

### 9.1 — Race Condition: Concurrent Requests

**Problem:** Two requests arrive simultaneously, both read "count = 4" (limit = 5), both increment → count = 6!

**Solution:** Use **Redis Lua scripts** (atomic execution):
```
Lua scripts execute atomically on Redis — no race condition possible.
READ + CHECK + INCREMENT all happen in ONE atomic operation.
```

### 9.2 — What if Redis Goes Down?

**Decision: Fail-Open vs Fail-Close**

| Strategy | Behavior | When to Use |
|----------|----------|-------------|
| **Fail-Open** | If Redis is down, ALLOW all requests | ✅ Prioritize availability (most APIs) |
| **Fail-Close** | If Redis is down, REJECT all requests | ✅ Prioritize security (payment, auth APIs) |

**Recommendation:** Default to **Fail-Open** + alert the ops team immediately.

```java
try {
    result = redis.executeScript(rateLimitScript, keys, args);
} catch (RedisConnectionException e) {
    log.error("Redis down! Failing open.", e);
    alertOpsTeam("Rate limiter Redis is unreachable!");
    return ALLOW;  // Fail-open: let traffic through
}
```

### 9.3 — Clock Synchronization

In distributed systems, servers may have slightly different clocks.

**Solution:** 
- Use **NTP** (Network Time Protocol) on all servers.
- Use Redis `TIME` command instead of local server time for Lua scripts.
- Accept ~1 second drift for non-critical limiters.

### 9.4 — Hot Keys (Celebrity Problem)

A single user or IP generating massive traffic → One Redis shard gets overwhelmed.

**Solution:**
- Use **local in-memory pre-filtering** (e.g., Guava Cache or Caffeine):
  - If a user is already rate-limited, reject locally without hitting Redis.
  - Use a short TTL (5–10 seconds) for the local cache.
- Shard the key across multiple Redis keys:
  ```
  rate_limit:user:123:shard_0
  rate_limit:user:123:shard_1
  rate_limit:user:123:shard_2
  Total count = sum of all shards
  ```

### 9.5 — Dropped Requests Queue (Optional)

Instead of just dropping over-limit requests, put them in a **queue** for delayed processing:

```
Client → Rate Limiter → Over Limit?
                              │
                    ┌─────────┼──────────┐
                    │ YES               NO│
                    ▼                    ▼
            ┌──────────────┐    ┌──────────────┐
            │  Message      │    │  API Server  │
            │  Queue (SQS)  │    │  (process    │
            │  (process     │    │   immediately)│
            │   later)      │    └──────────────┘
            └──────────────┘
```

---

## Step 10 — Monitoring, Alerting & Analytics

> **"Monitoring is critical for a rate limiter — both to ensure it's working and to tune limits."**

### Key Metrics to Track

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `rate_limiter.requests.total` | Total requests processed | Baseline monitoring |
| `rate_limiter.requests.allowed` | Requests allowed through | - |
| `rate_limiter.requests.rejected` | Requests rejected (429) | > 5% rejection rate |
| `rate_limiter.latency.p99` | P99 latency of rate limit check | > 5 ms |
| `rate_limiter.redis.connection_errors` | Redis connection failures | > 0 per minute |
| `rate_limiter.redis.latency.p99` | Redis operation latency | > 3 ms |
| `rate_limiter.failopen.count` | Times fail-open was triggered | > 0 (critical alert) |

### Dashboard

```
┌─────────────────────────────────────────────────────────────┐
│                    Rate Limiter Dashboard                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Requests/sec    Rejection Rate    Redis Latency (p99)       │
│  ┌──────────┐   ┌──────────┐     ┌──────────┐              │
│  │▓▓▓▓▓░░░░░│   │▓░░░░░░░░░│     │▓░░░░░░░░░│              │
│  │ 850K/s   │   │ 2.1%     │     │ 0.8ms    │              │
│  └──────────┘   └──────────┘     └──────────┘              │
│                                                              │
│  Top Rejected Users     Top Rejected Endpoints               │
│  ┌──────────────────┐   ┌──────────────────────┐            │
│  │ user:5001 - 12K  │   │ POST /login  - 45K   │            │
│  │ ip:1.2.3.4 - 8K  │   │ POST /orders - 12K   │            │
│  │ user:8823 - 5K   │   │ GET /search  - 8K    │            │
│  └──────────────────┘   └──────────────────────┘            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Logging

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "event": "rate_limit_exceeded",
  "client_key": "user:5001",
  "endpoint": "POST /api/v1/orders",
  "rule": "create-order-limit",
  "limit": 10,
  "current_count": 11,
  "window": "60s",
  "action": "rejected",
  "client_ip": "203.0.113.42"
}
```

---

## Step 11 — Summary & Trade-offs

> **"Let me wrap up with a summary and the key trade-offs."**

### Architecture Summary

```
┌──────────┐     ┌──────────────┐     ┌─────────────┐     ┌────────────┐
│  Client  │────▶│ Load Balancer│────▶│ API Gateway  │────▶│ API Server │
└──────────┘     └──────────────┘     │ + Rate       │     └────────────┘
                                      │   Limiter    │
                                      │   Middleware │
                                      └──────┬───────┘
                                             │
                                      ┌──────▼───────┐
                                      │ Redis Cluster │
                                      │ (Counters)   │
                                      └──────────────┘
                                             │
                                      ┌──────▼───────┐
                                      │ Rules Config │
                                      │ (etcd/Consul)│
                                      └──────────────┘
```

### Key Design Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| **Where?** | API Gateway Middleware | Centralized, decoupled from business logic |
| **Algorithm** | Token Bucket + Sliding Window Counter | Best balance of accuracy, performance, and burst tolerance |
| **Storage** | Redis Cluster | In-memory, fast, atomic Lua scripts, cluster mode for scaling |
| **Atomicity** | Redis Lua Scripts | Prevents race conditions in concurrent environments |
| **Rules** | YAML/JSON in Config Store (etcd) | Dynamic reload, version controlled |
| **Failure Mode** | Fail-Open (configurable) | Availability over strictness for most APIs |
| **Client Notification** | HTTP 429 + Rate Limit Headers | Industry standard (RFC 6585) |

### Trade-offs

| Trade-off | Decision | Alternative |
|-----------|----------|-------------|
| **Accuracy vs. Performance** | Sliding Window Counter (~99.97% accurate) | Sliding Window Log (100% accurate but memory-heavy) |
| **Centralized vs. Local** | Centralized Redis (accurate) | Local counters (faster but inconsistent) |
| **Fail-Open vs. Fail-Close** | Fail-Open (availability) | Fail-Close (security, for payment APIs) |
| **Strict vs. Relaxed (Multi-DC)** | Relaxed sync (accept ~5% over-limit) | Centralized store (accurate but cross-DC latency) |
| **Complexity vs. Simplicity** | Token Bucket (simple, proven) | Leaking Bucket (smoother but less flexible) |

### What I'd Add with More Time

1. **Adaptive Rate Limiting** — Dynamically adjust limits based on server load (CPU, memory, response time).
2. **User Tier Management** — Free / Pro / Enterprise tiers with different limits stored in a user database.
3. **Graceful Degradation** — Instead of hard 429, degrade quality (e.g., cached responses) for over-limit users.
4. **IP Reputation System** — Known bad IPs get stricter limits; verified users get relaxed limits.
5. **Circuit Breaker** — If a downstream service is overwhelmed, temporarily lower rate limits for its endpoints.
6. **Distributed Tracing** — Integrate with Jaeger/Zipkin to trace rate-limited requests across microservices.

### Technologies / Real-World Implementations

| Company | Approach |
|---------|----------|
| **Stripe** | Token Bucket per API key |
| **Cloudflare** | Sliding Window Counter, edge-level (200+ PoPs) |
| **AWS API Gateway** | Token Bucket, configurable per stage/method |
| **GitHub** | Sliding Window, 5000 req/hour per authenticated user |
| **Shopify** | Leaking Bucket per app, per store |
| **Google Cloud** | Token Bucket with quota management |
| **Twitter/X** | Fixed Window, tier-based limits |

---

### Final Words to Interviewer

> *"To summarize, I'd build a rate limiter as a middleware service sitting at the API Gateway layer. It uses Redis for fast, atomic counter operations with Lua scripts to avoid race conditions. The Token Bucket algorithm gives us burst tolerance for general APIs, and the Sliding Window Counter provides accurate per-user limits. Rules are stored in a config service like etcd for hot reloading. In a multi-DC setup, we'd use a centralized Redis Cluster with fail-open behavior for resilience. The system is designed to add less than 2ms of latency per request and can handle millions of requests per second with a small Redis cluster."*

---

> **Interview Score Rubric:**
> - ✅ Clarified requirements and scope
> - ✅ Back-of-the-envelope estimation
> - ✅ Discussed multiple algorithms with trade-offs
> - ✅ Clear architecture diagram
> - ✅ Deep dive into distributed challenges (race conditions, multi-DC, failure modes)
> - ✅ Discussed monitoring and operational concerns
> - ✅ Summarized trade-offs and justified decisions

