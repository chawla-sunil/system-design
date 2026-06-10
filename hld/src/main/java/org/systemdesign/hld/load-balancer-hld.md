# Load Balancer — High-Level Design (HLD Interview)

> **Interview Simulation**: A 1-hour system design interview where the candidate (6-7 YOE) explains Load Balancers end-to-end.

---

## 📌 How I Would Approach This in an Interview

### Step 1: Clarify the Scope (2-3 minutes)

**Me:** "Before I dive in, I'd like to clarify the scope. Are we designing a load balancer from scratch, or discussing how load balancers fit into a distributed system architecture? Also, should I focus on Layer 4 (Transport) or Layer 7 (Application) load balancing, or both?"

**Interviewer:** "Cover both. Explain what a load balancer is, where it sits, the algorithms, and how you'd choose one for a real system."

**Me:** "Perfect. I'll cover:
1. What & Why — the problem it solves
2. Where it sits in a system architecture
3. Types of Load Balancers (L4 vs L7)
4. Load Balancing Algorithms (deep dive)
5. Health Checks & Failover
6. Advanced topics — Sticky Sessions, SSL Termination, Global LB
7. Real-world trade-offs and how I'd choose"

---

## 1. What is a Load Balancer & Why Do We Need It? (5 minutes)

### The Problem

In any non-trivial system, a single server cannot handle:
- High traffic volume (throughput bottleneck)
- High availability requirements (single point of failure)
- Low latency SLAs across geographies

### The Solution

A **Load Balancer (LB)** is a component that distributes incoming network traffic across multiple backend servers (also called a "server pool" or "upstream cluster").

```
                    ┌─────────────┐
                    │   Client    │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │Load Balancer│
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Server 1 │ │ Server 2 │ │ Server 3 │
        └──────────┘ └──────────┘ └──────────┘
```

### Key Benefits

| Benefit | Description |
|---------|-------------|
| **High Availability** | If one server dies, traffic is routed to healthy ones |
| **Scalability** | Add/remove servers without downtime |
| **Performance** | Distribute load so no single server is overwhelmed |
| **Flexibility** | Perform SSL termination, caching, compression at the LB layer |
| **Security** | Hide internal server topology; DDoS mitigation at LB layer |

---

## 2. Where Does a Load Balancer Sit? (5 minutes)

### Typical Multi-Tier Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           INTERNET                                         │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │   DNS (Route 53 /     │
                    │   Cloudflare DNS)      │    ← Global Load Balancing
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │   CDN / Edge Layer    │    ← Static content, caching
                    │   (CloudFront/Akamai) │
                    └───────────┬───────────┘
                                │
                                ▼
              ┌─────────────────────────────────────┐
              │   L7 Load Balancer (ALB / Nginx /   │   ← Application-level routing
              │   HAProxy / Envoy)                   │
              └─────────────────┬───────────────────┘
                                │
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
    │  Web/App     │   │  Web/App     │   │  Web/App     │
    │  Server 1    │   │  Server 2    │   │  Server 3    │
    └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
           │                   │                   │
           └───────────────────┼───────────────────┘
                               ▼
              ┌─────────────────────────────────────┐
              │   L4 Load Balancer (NLB / LVS /     │   ← TCP/UDP level
              │   Internal LB)                       │
              └─────────────────┬───────────────────┘
                                │
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
    │  Database    │   │  Database    │   │  Database    │
    │  Primary     │   │  Replica 1   │   │  Replica 2   │
    └──────────────┘   └──────────────┘   └──────────────┘
```

### Load Balancers at Multiple Layers

| Layer | Example | Purpose |
|-------|---------|---------|
| **DNS Level** | Route 53, Cloudflare | Geographic routing, failover between regions |
| **Edge/CDN** | CloudFront, Akamai | Cache static assets close to users |
| **L7 (Application)** | ALB, Nginx, HAProxy, Envoy | HTTP routing, path-based, header-based |
| **L4 (Transport)** | NLB, LVS, IPVS | Raw TCP/UDP forwarding, very high throughput |
| **Service Mesh** | Istio/Envoy sidecar | Microservice-to-microservice load balancing |

---

## 3. Types of Load Balancers (8 minutes)

### 3.1 Layer 4 (Transport Layer) Load Balancer

Operates at the **TCP/UDP** level. It sees:
- Source IP, Destination IP
- Source Port, Destination Port
- Protocol (TCP/UDP)

**It does NOT inspect** the HTTP payload, headers, cookies, or URL.

**How it works:**
1. Client opens a TCP connection to the LB's VIP (Virtual IP)
2. LB selects a backend server based on algorithm
3. LB either:
   - **NAT mode**: Rewrites dest IP and forwards (most common)
   - **DR (Direct Return)**: Modifies MAC, server replies directly to client
   - **Tunneling (IP-in-IP)**: Encapsulates packet

**Pros:**
- Extremely fast (no payload inspection)
- Low latency (~microseconds of overhead)
- Handles any TCP/UDP protocol (not just HTTP)

**Cons:**
- No content-based routing (can't route `/api` vs `/static`)
- No SSL termination (each backend must handle TLS)
- Limited observability

**Real-world:** AWS NLB, Linux IPVS/LVS, F5 BIG-IP (L4 mode)

---

### 3.2 Layer 7 (Application Layer) Load Balancer

Operates at the **HTTP/HTTPS** level. It can inspect:
- URL path, query parameters
- HTTP headers (Host, Cookie, Authorization)
- HTTP method (GET, POST)
- Request body (advanced)

**How it works:**
1. Client establishes TCP + TLS connection with the LB
2. LB terminates TLS (SSL Termination)
3. LB reads the HTTP request
4. Based on routing rules, selects a backend
5. LB opens a **separate** connection to backend (connection pooling)
6. Forwards request, receives response, sends back to client

**Pros:**
- Content-based routing (microservices, A/B testing, canary)
- SSL termination (offload crypto from backends)
- Request manipulation (add headers, rewrite URLs)
- Better observability (HTTP metrics, access logs)
- WebSocket support with upgrade handling

**Cons:**
- Higher latency (TLS termination + HTTP parsing)
- More resource-intensive
- Terminates the TCP connection (two separate connections)

**Real-world:** AWS ALB, Nginx, HAProxy, Envoy Proxy, Traefik

---

### 3.3 Comparison Table

| Feature | L4 LB | L7 LB |
|---------|--------|--------|
| OSI Layer | Transport (TCP/UDP) | Application (HTTP) |
| Speed | Very fast | Moderate |
| Content Routing | ❌ | ✅ |
| SSL Termination | ❌ | ✅ |
| Sticky Sessions | IP-based only | Cookie-based |
| WebSocket | Pass-through | Aware |
| Use Case | High-throughput TCP | HTTP microservices |

---

## 4. Load Balancing Algorithms — Deep Dive (20 minutes)

### 4.1 Round Robin

```
Request 1  → Server A
Request 2  → Server B
Request 3  → Server C
Request 4  → Server A  (cycle repeats)
```

**How it works:** Sequentially distribute requests in circular order.

**Pros:**
- Simplest to implement
- Even distribution when servers are homogeneous
- No state required

**Cons:**
- Ignores server capacity differences
- Ignores current server load
- A slow request on Server A doesn't affect routing

**When to use:** Stateless services with identical server specs.

---

### 4.2 Weighted Round Robin

```
Weights: A=5, B=3, C=2  (total=10)

Requests 1-5   → Server A
Requests 6-8   → Server B
Requests 9-10  → Server C
(cycle repeats)
```

**How it works:** Like Round Robin, but servers with higher weights get proportionally more requests.

**Pros:**
- Accounts for heterogeneous hardware
- Simple to configure

**Cons:**
- Static weights — doesn't adapt to runtime load
- Weights must be manually tuned

**When to use:** Mixed hardware fleet (e.g., 8-core vs 16-core machines).

---

### 4.3 Least Connections

```
Server A: 10 active connections
Server B: 3 active connections
Server C: 7 active connections

Next request → Server B (fewest connections)
```

**How it works:** Route to the server with the fewest active connections.

**Pros:**
- Adapts to actual load in real-time
- Great for long-lived connections (WebSocket, database)
- Handles slow/fast request mix well

**Cons:**
- Requires tracking connection count per server (state)
- Doesn't account for connection "weight" (some are heavier)
- New server gets flooded (thundering herd)

**When to use:** Services with variable request processing times (e.g., video transcoding, database queries).

---

### 4.4 Weighted Least Connections

```
Server A: 10 connections, weight=5 → effective = 10/5 = 2.0
Server B: 3 connections, weight=3  → effective = 3/3 = 1.0
Server C: 7 connections, weight=2  → effective = 7/2 = 3.5

Next request → Server B (lowest effective ratio)
```

**How it works:** Combines least connections with server weights. Route to server with lowest (connections / weight) ratio.

**Pros:**
- Best of both worlds — capacity-aware + load-aware
- Production-grade for heterogeneous clusters

**Cons:**
- More complex state management
- Weight tuning still needed

**When to use:** Mixed fleet with variable workloads.

---

### 4.5 Least Response Time

```
Server A: avg response = 120ms, active conn = 10
Server B: avg response = 45ms, active conn = 3
Server C: avg response = 200ms, active conn = 7

Next request → Server B (fastest + least loaded)
```

**How it works:** Route to the server with the lowest combination of:
- Active connections
- Average response time

**Pros:**
- Most adaptive algorithm
- Naturally routes away from degraded servers
- Self-healing behavior

**Cons:**
- Requires continuous response time measurement
- Cold start problem (new servers have no measurements)
- Response time can be noisy

**When to use:** Performance-critical services where backend latency varies.

---

### 4.6 IP Hash (Source IP Affinity)

```
hash(client_ip) % num_servers = server_index

Client 192.168.1.1 → hash → Server A
Client 192.168.1.2 → hash → Server C
Client 192.168.1.1 → hash → Server A  (same client, same server!)
```

**How it works:** Hash the client's IP to deterministically map to a server.

**Pros:**
- Session persistence without cookies
- No session state at LB
- Deterministic — same client always hits same server

**Cons:**
- Uneven distribution if IP distribution is skewed
- Server addition/removal remaps many clients (use consistent hashing!)
- NAT/proxies can cause hot spots (many users behind one IP)

**When to use:** Stateful applications where session state lives on the server (legacy apps, gaming).

---

### 4.7 Consistent Hashing

```
Hash Ring:
        0°
        │
   ┌────┴────┐
   │  Ring    │    Servers placed at hash(server_id) positions
   │          │    Requests placed at hash(request_key) positions
   │  A   B   │    Request routes to NEXT server clockwise
   │     C    │
   └──────────┘
```

**How it works:**
1. Place servers on a hash ring using hash(server_id)
2. For each request, compute hash(key) and find the next server clockwise
3. When a server is added/removed, only K/N keys are remapped (K=keys, N=servers)

**Pros:**
- Minimal disruption on scale up/down
- Great for caches (only ~1/N cache invalidation on change)
- Used by Cassandra, DynamoDB, Memcached

**Cons:**
- Can be uneven without virtual nodes
- More complex implementation
- Doesn't consider server load

**Enhancement — Virtual Nodes:**
Each physical server gets multiple positions on the ring (e.g., 150 virtual nodes per server), ensuring even distribution.

**When to use:** Caching layers, distributed databases, CDN edge routing.

---

### 4.8 Random

```
Next request → random(Server A, Server B, Server C)
```

**How it works:** Randomly select a server for each request.

**Pros:**
- Zero state, zero coordination
- Statistically even over time (law of large numbers)
- Works well in distributed LB setups (no shared state needed)

**Cons:**
- No guarantee of short-term evenness
- Doesn't consider server health or load

**When to use:** Large server pools where statistical distribution is sufficient.

---

### 4.9 Power of Two Choices (P2C)

```
1. Randomly pick 2 servers: Server A (10 conn), Server C (3 conn)
2. Choose the less loaded one: Server C
```

**How it works:**
1. Randomly select TWO servers from the pool
2. Route to the one with fewer connections/lower load

**Pros:**
- Near-optimal distribution with minimal state
- Avoids thundering herd
- O(1) decision time
- Used by Envoy proxy, gRPC load balancing

**Cons:**
- Still probabilistic (not perfectly even)
- Requires at least connection count per server

**Why it's powerful:** Mathematically proven to exponentially reduce max load compared to pure random. Max load goes from O(log n / log log n) to O(log log n).

**When to use:** Service meshes, client-side load balancing, high-performance proxies.

---

### 4.10 Resource-Based (Adaptive)

```
Server A: CPU=90%, Memory=70%  → Score: unhealthy
Server B: CPU=30%, Memory=40%  → Score: healthy ✓
Server C: CPU=60%, Memory=55%  → Score: moderate
```

**How it works:** Backend servers report their resource utilization. LB routes based on actual resource availability.

**Pros:**
- Most accurate picture of server health
- Accounts for non-LB traffic (cron jobs, background tasks)

**Cons:**
- Requires agent on each server reporting metrics
- Stale data risk (reporting delay)
- Complex implementation

**When to use:** Systems with mixed workloads, GPU-intensive services, heterogeneous compute.

---

### Algorithm Selection Guide

| Scenario | Best Algorithm |
|----------|---------------|
| Identical servers, stateless requests | Round Robin |
| Mixed hardware specs | Weighted Round Robin |
| Long-lived connections (WebSocket) | Least Connections |
| Performance-critical API | Least Response Time |
| Session affinity needed (legacy) | IP Hash |
| Caching layer (Redis/Memcached) | Consistent Hashing |
| Service mesh (gRPC) | Power of Two Choices |
| Large-scale, zero coordination | Random |
| GPU/ML inference servers | Resource-Based |

---

## 5. Health Checks & Failover (7 minutes)

### Why Health Checks?

A load balancer is useless if it routes traffic to dead servers.

### Types of Health Checks

#### 1. Active Health Checks (LB → Server)

```
LB periodically sends probes:
  - TCP connect to port 8080 (is the port open?)
  - HTTP GET /health (does it return 200?)
  - Custom script (can it query the DB?)

If 3 consecutive failures → mark UNHEALTHY → remove from pool
If 2 consecutive successes → mark HEALTHY → add back to pool
```

**Configuration Example (Nginx):**
```nginx
upstream backend {
    server 10.0.0.1:8080;
    server 10.0.0.2:8080;
    server 10.0.0.3:8080;

    # Health check every 5s, 3 failures = down, 2 successes = up
    health_check interval=5s fails=3 passes=2;
}
```

#### 2. Passive Health Checks (Observe Real Traffic)

```
LB observes responses from backends:
  - If 5xx errors exceed threshold → mark unhealthy
  - If timeouts exceed threshold → mark unhealthy
  - Circuit breaker pattern
```

#### 3. Health Check Levels

| Level | Check | Catches |
|-------|-------|---------|
| L4 (TCP) | Port open? | Crashed process |
| L7 (HTTP) | GET /health → 200? | App errors, OOM |
| Deep | /health checks DB, cache, deps | Dependency failures |
| Liveness | Is process alive? | Deadlocks |
| Readiness | Can it serve traffic? | Warming up, draining |

### Failover Strategies

```
┌─────────────┐         ┌─────────────┐
│  Primary LB │ ──────► │ Secondary LB│   (Active-Passive with VRRP/Keepalived)
│  (Active)   │ heartbt │ (Standby)   │
└─────────────┘         └─────────────┘
         │                      │
    (VIP: 10.0.0.100)     (Takes over VIP if primary dies)
```

**Active-Passive:** Standby LB takes over via floating IP (VRRP).
**Active-Active:** Both LBs serve traffic. DNS or upstream LB distributes between them.

---

## 6. Advanced Topics (10 minutes)

### 6.1 SSL/TLS Termination

```
Client ──── HTTPS (TLS 1.3) ────► LB ──── HTTP (plaintext) ────► Backend
```

**Why terminate at LB?**
- Offload CPU-intensive crypto from app servers
- Centralized certificate management
- Single place to enforce TLS policies

**SSL Passthrough (alternative):**
```
Client ──── HTTPS ────► LB (no inspection) ────► HTTPS ────► Backend
```
Used when end-to-end encryption is required (compliance, PCI-DSS).

---

### 6.2 Sticky Sessions (Session Affinity)

**Problem:** User logs in on Server A, session stored in memory. Next request goes to Server B → session lost!

**Solutions:**

| Method | How | Trade-off |
|--------|-----|-----------|
| Cookie-based | LB sets `SERVERID=A` cookie | Best; LB controls affinity |
| IP Hash | hash(client_ip) → server | Breaks with NAT/mobile |
| URL parameter | `?server=A` | Ugly, not recommended |
| External session store | Redis/Memcached stores sessions | Best practice — eliminates need for stickiness |

**My recommendation:** "In a well-designed system, avoid sticky sessions. Use a shared session store (Redis) so any server can handle any request. This allows true horizontal scaling."

---

### 6.3 Connection Draining (Graceful Shutdown)

```
1. Server signals "I'm shutting down"
2. LB stops sending NEW requests to that server
3. Existing in-flight requests are allowed to complete (30-60s timeout)
4. After draining, server is removed from pool
```

Essential for zero-downtime deployments (rolling updates, blue-green).

---

### 6.4 Global Server Load Balancing (GSLB)

```
User in India                          User in USA
     │                                      │
     ▼                                      ▼
┌─────────┐                          ┌─────────┐
│  DNS    │ resolves to nearest DC   │  DNS    │
└────┬────┘                          └────┬────┘
     │                                    │
     ▼                                    ▼
┌──────────────┐                   ┌──────────────┐
│ Mumbai DC    │                   │ Virginia DC  │
│ Regional LB  │                   │ Regional LB  │
│ → Servers    │                   │ → Servers    │
└──────────────┘                   └──────────────┘
```

**Methods:**
- **GeoDNS:** Resolve to nearest datacenter IP based on client location
- **Anycast:** Same IP advertised from multiple DCs; BGP routes to nearest
- **Latency-based:** Route to DC with lowest measured latency (Route 53)

---

### 6.5 Rate Limiting & DDoS Protection at LB

Load balancers are the first line of defense:
- Per-IP rate limiting
- Connection limits
- Request rate throttling
- SYN flood protection (SYN cookies)
- Slowloris protection (connection timeouts)

---

### 6.6 Service Discovery Integration

In microservices/Kubernetes:
```
┌──────────────────────────────────────────────────────┐
│  Service Registry (Consul / etcd / Kubernetes DNS)   │
└───────────────────────────┬──────────────────────────┘
                            │ watches for changes
                            ▼
                    ┌───────────────┐
                    │ Load Balancer │ ← dynamically updates server pool
                    │ (Envoy/Nginx) │
                    └───────────────┘
```

No manual server list management — LB discovers backends automatically.

---

## 7. Real-World Architecture Example (5 minutes)

### E-Commerce Platform

```
┌──────────────────────────────────────────────────────────────┐
│                      GLOBAL LAYER                              │
│  Route 53 (Latency-based routing across regions)              │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌───────────────────────────────┼──────────────────────────────┐
│                      EDGE LAYER                                │
│  CloudFront CDN (static assets, cache API responses)          │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌───────────────────────────────┼──────────────────────────────┐
│                      REGIONAL LAYER                            │
│  AWS ALB (L7) — path-based routing:                           │
│    /api/users/*      → User Service (3 instances)             │
│    /api/orders/*     → Order Service (5 instances)            │
│    /api/payments/*   → Payment Service (3 instances)          │
│    /static/*         → S3 (redirect to CDN)                   │
│                                                               │
│  Algorithm: Least Outstanding Requests (ALB default)          │
│  Health Check: HTTP GET /actuator/health every 10s            │
│  Stickiness: Disabled (sessions in Redis)                     │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌───────────────────────────────┼──────────────────────────────┐
│                      INTERNAL LAYER                            │
│  AWS NLB (L4) — for internal service-to-service:             │
│    gRPC traffic (HTTP/2, requires L4 pass-through)            │
│    Database connections (TCP)                                  │
│                                                               │
│  Service Mesh: Envoy sidecars (P2C algorithm)                 │
└──────────────────────────────────────────────────────────────┘
```

---

## 8. How I'd Answer Trade-off Questions (5 minutes)

### "How do you handle the Load Balancer itself being a SPOF?"

"Great question. We address this at multiple levels:
1. **Hardware LBs:** Active-passive pair with VRRP (shared VIP)
2. **Cloud LBs (ALB/NLB):** Managed service — AWS handles redundancy across AZs
3. **DNS Level:** Multiple LB endpoints, DNS failover
4. **Anycast:** Multiple LBs share the same IP, network routes around failures"

### "When would you NOT use a load balancer?"

"In a single-server architecture (small apps), or when using a service mesh where client-side load balancing happens via sidecar proxies. Also, for event-driven architectures using message queues (SQS/Kafka), the queue itself acts as the work distributor."

### "How do you handle WebSocket connections?"

"WebSockets are long-lived. I'd use:
- L7 LB with connection upgrade support
- Least Connections algorithm (since connections persist)
- Connection draining with long timeout for deploys
- Or L4 pass-through if LB doesn't support WebSocket well"

---

## 9. Summary & Key Takeaways

```
┌─────────────────────────────────────────────────────────────────┐
│                    LOAD BALANCER CHEAT SHEET                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  TYPES:        L4 (fast, TCP)  vs  L7 (smart, HTTP)             │
│                                                                   │
│  TOP ALGORITHMS:                                                  │
│    • Round Robin         → Simple, stateless                     │
│    • Least Connections   → Adaptive, connection-aware            │
│    • Consistent Hashing  → Caching, minimal remapping            │
│    • Power of Two (P2C)  → Service mesh, near-optimal            │
│                                                                   │
│  MUST-HAVES:                                                      │
│    • Health checks (active + passive)                            │
│    • Connection draining                                         │
│    • SSL termination (L7)                                        │
│    • Auto-scaling integration                                    │
│                                                                   │
│  AVOID:                                                           │
│    • Sticky sessions (use Redis instead)                         │
│    • Single LB without redundancy                                │
│    • Ignoring health checks                                      │
│                                                                   │
│  REAL WORLD:                                                      │
│    • AWS: ALB (L7) + NLB (L4) + Route 53 (DNS)                 │
│    • Self-hosted: Nginx / HAProxy / Envoy                        │
│    • K8s: Ingress Controller + Service (kube-proxy/IPVS)         │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 10. Interview Tips

1. **Start with WHY** — always explain the problem before the solution
2. **Draw diagrams** — show where LB sits in the architecture
3. **Compare trade-offs** — never say "X is the best"; say "X is best WHEN..."
4. **Give real examples** — "In my previous project, we used ALB with..."
5. **Mention failure modes** — shows production experience
6. **Connect to other concepts** — auto-scaling, service discovery, circuit breakers
7. **Ask clarifying questions** — scope the problem before solving

---

## References & Further Reading

- [Nginx Load Balancing Docs](https://docs.nginx.com/nginx/admin-guide/load-balancer/http-load-balancer/)
- [AWS ELB Documentation](https://docs.aws.amazon.com/elasticloadbalancing/)
- [Envoy Proxy Load Balancing](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/upstream/load_balancing/overview)
- [Google SRE Book — Load Balancing at the Frontend](https://sre.google/sre-book/load-balancing-frontend/)
- [The Power of Two Choices Paper](https://www.eecs.harvard.edu/~michaelm/postscripts/mythesis.pdf)
- [Consistent Hashing — Karger et al.](https://www.cs.princeton.edu/courses/archive/fall09/cos518/papers/chash.pdf)

