# Microservices Design Patterns — HLD Interview (1 Hour)

> **Simulated Interview Format**
> Interviewer asks broad questions → Candidate (you, a 6-7 YoE engineer) walks through microservices architecture and the design patterns that make it work in production.

---

## Table of Contents

1. [Opening — Clarify the Question (~3 min)](#1-opening--clarify-the-question-3-min)
2. [Monolith vs Microservices — Why Patterns Matter (~5 min)](#2-monolith-vs-microservices--why-patterns-matter-5-min)
3. [Decomposition Patterns (~7 min)](#3-decomposition-patterns-7-min)
4. [Communication Patterns (~10 min)](#4-communication-patterns-10-min)
5. [Data Management Patterns (~10 min)](#5-data-management-patterns-10-min)
6. [Resilience Patterns (~10 min)](#6-resilience-patterns-10-min)
7. [Observability Patterns (~5 min)](#7-observability-patterns-5-min)
8. [Deployment & Cross-Cutting Patterns (~5 min)](#8-deployment--cross-cutting-patterns-5-min)
9. [End-to-End Reference Architecture (~3 min)](#9-end-to-end-reference-architecture-3-min)
10. [Anti-Patterns & Wrap-up (~2 min)](#10-anti-patterns--wrap-up-2-min)

---

## 1. Opening — Clarify the Question (~3 min)

### Interviewer's Question
> "Walk me through the design patterns you'd use when building a large-scale microservices system. Cover decomposition, communication, data, resilience, and observability."

### Candidate's Response

I'll structure this around the **5 problem areas** microservices introduce, and the patterns we use to address each:

| Area              | Problem                                         | Patterns                                |
|-------------------|-------------------------------------------------|------------------------------------------|
| Decomposition     | How to split a monolith                          | DDD, Strangler Fig, Bounded Context     |
| Communication     | How services talk                                | API Gateway, BFF, Service Mesh, Async   |
| Data              | How to handle data across services               | DB per Service, CQRS, Event Sourcing, Saga |
| Resilience        | How to survive failures                          | Circuit Breaker, Bulkhead, Retry, Timeout |
| Observability     | How to understand the system                     | Centralized Logs, Tracing, Metrics      |

Let me cover each.

---

## 2. Monolith vs Microservices — Why Patterns Matter (~5 min)

### Trade-off

| Aspect              | Monolith                  | Microservices                  |
|---------------------|--------------------------|-------------------------------|
| Deployability       | Single artifact          | Independent per service       |
| Scaling             | Scale entire app          | Scale per service             |
| Tech stack          | Uniform                  | Polyglot                       |
| Failure isolation   | Fault → full crash        | Localized faults              |
| Operational complexity | Low                    | High (lots of moving parts)   |
| Data transactions   | ACID (easy)              | Distributed (hard)            |
| Team autonomy       | Coordinated              | Independent (one team/service)|

> **Microservices solve organizational and scaling problems but introduce distributed-systems complexity. Patterns are the proven recipes.**

### When to use microservices
- Large teams (Conway's Law).
- Independent scalability needs.
- Different tech stacks per domain.
- Continuous deployment needed.

### When NOT
- Small team.
- Greenfield startup (build modular monolith first).
- Tight coupling between domains.

---

## 3. Decomposition Patterns (~7 min)

### 3.1 Decompose by Business Capability

Each service = one business capability (Orders, Payments, Inventory, Shipping).

### 3.2 Decompose by Subdomain (Domain-Driven Design)

Use DDD **Bounded Contexts** as service boundaries.

```
   ┌─────────────────────────────────────────────────┐
   │              E-commerce Domain                   │
   │                                                  │
   │  ┌──────────────┐    ┌──────────────────┐       │
   │  │ Order        │    │ Inventory        │       │
   │  │ Context       │    │ Context          │       │
   │  │              │    │                  │       │
   │  │ Entities:    │    │ Entities:        │       │
   │  │  Order        │    │  Product          │       │
   │  │  OrderLine    │    │  Stock            │       │
   │  └──────────────┘    └──────────────────┘       │
   │                                                  │
   │  ┌──────────────┐    ┌──────────────────┐       │
   │  │ Payment       │    │ Shipping         │       │
   │  │ Context       │    │ Context           │       │
   │  └──────────────┘    └──────────────────┘       │
   └─────────────────────────────────────────────────┘
```

**Each context owns its data and language.** "Order" might mean different things in Order vs Shipping context.

### 3.3 Strangler Fig Pattern (migration)

Gradually replace monolith functionality with microservices.

```
   ┌──────────┐                          ┌──────────┐
   │ Client    │                          │  Client   │
   └─────┬────┘                          └─────┬────┘
         │                                      │
         ▼                                      ▼
   ┌──────────┐                          ┌──────────────┐
   │ Monolith  │                          │ Routing Proxy│
   │ (V1)      │                          └──┬─────────┬─┘
   └──────────┘                             │         │
                                            ▼         ▼
                                       ┌─────────┐ ┌──────────┐
                                       │ New     │ │ Monolith │
                                       │ Service │ │ (legacy) │
                                       └─────────┘ └──────────┘

   Over time: more routes go to new services → monolith shrinks → dies.
```

### 3.4 Sidecar Pattern (helper)

Deploy auxiliary functionality (logging, TLS, config) as a sidecar container alongside the main service.

```
   ┌─────────────────────────────┐
   │           Pod                │
   │  ┌──────────┐ ┌──────────┐  │
   │  │ App      │ │ Sidecar  │  │
   │  │ Container│ │ (Envoy,  │  │
   │  │          │ │  Logger) │  │
   │  └──────────┘ └──────────┘  │
   │     shared network/volume    │
   └──────────────────���──────────┘
```

Common in service meshes (Istio, Linkerd).

---

## 4. Communication Patterns (~10 min)

### 4.1 API Gateway

Single entry point for all clients. Handles auth, rate-limiting, routing, aggregation.

```
   ┌──────┐ ┌──────┐ ┌──────┐
   │Web   │ │Mobile│ │3rd   │
   └──┬───┘ └──┬───┘ └──┬───┘
      │        │        │
      └────────┼────────┘
               ▼
       ┌──────────────────┐
       │   API Gateway     │  ← Auth, rate limit, routing,
       │  (Kong, AWS APIGW)│    request transformation,
       └────────┬─────────┘    SSL termination
                │
   ┌────────────┼─────────────┐
   │            │             │
   ▼            ▼             ▼
 ┌─────┐    ┌──────┐    ┌────────┐
 │Order│    │User  │    │Catalog │
 └─────┘    └──────┘    └────────┘
```

**Benefits:** Centralized concerns. Client doesn't need to know N services.
**Risks:** Can become a bottleneck or god-object.

### 4.2 Backend-for-Frontend (BFF)

Separate gateway per client type (Web, Mobile, IoT) → tailored APIs.

```
   ┌──────┐    ┌──────────────┐    ┌──────┐
   │ Web   │ → │ Web BFF      │ → │ ...  │
   └──────┘    └──────────────┘    └──────┘
   ┌──────┐    ┌──────────────┐    ┌──────┐
   │Mobile │ → │ Mobile BFF   │ → │ ...  │
   └──────┘    └──────────────┘    └──────┘
```

### 4.3 Service Mesh (Istio, Linkerd)

Network infrastructure layer for service-to-service communication.

**Sidecar proxy handles:**
- mTLS encryption.
- Retries, timeouts, circuit breaking.
- Traffic routing (canary, A/B).
- Observability (metrics, traces).

```
   Service A ──► Envoy A ──[mTLS]──► Envoy B ──► Service B
                  │                    │
                  └─►  Control Plane (Istio) ◄─┘
                        (policies, certs, telemetry)
```

### 4.4 Sync vs Async Communication

**Synchronous (REST, gRPC)**
- Request/response, simple, low latency.
- ❌ Temporal coupling — if B is down, A's request fails.

**Asynchronous (Kafka, RabbitMQ, SQS)**
- Producer publishes event, consumer processes later.
- ✅ Decouples services in time + space.
- ❌ Eventual consistency, harder to debug.

### Event-Driven Architecture

```
   Order Service ─── publishes ──► [ Kafka Topic ]
                                          │
              ┌───────────────────────────┼───────────────────────────┐
              ▼                           ▼                           ▼
       Inventory Service          Notification Service        Analytics Service
       (reserve stock)            (send email)                (track event)
```

> Rule of thumb: **Use async for "I'll get to it eventually" / events. Use sync for "I need an answer now" / queries.**

### Communication Pattern Decision

```
                  ┌──────────────────────────┐
                  │ Need immediate response? │
                  └──────────┬───────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
            YES              │             NO
              │              │              │
              ▼              │              ▼
        ┌──────────┐         │       ┌─────────────┐
        │ External │         │       │ Use async   │
        │  client? │         │       │ Kafka/SQS   │
        └────┬─────┘         │       │ Pub/Sub     │
             │               │       └─────────────┘
       ┌─────┼─────┐         │
       │           │         │
      YES         NO         │
       │           │         │
       ▼           ▼         │
     REST       gRPC         │
     (JSON)   (binary)       │
```

---

## 5. Data Management Patterns (~10 min)

### 5.1 Database per Service

Each service owns its data. No shared DB. Other services access via API only.

```
   ┌──────────┐   ┌──────────┐   ┌──────────┐
   │ Order Svc │   │ User Svc │   │Inv. Svc  │
   └─────┬────┘   └─────┬────┘   └─────┬────┘
         │              │              │
         ▼              ▼              ▼
    ┌────────┐    ┌────────┐    ┌─────────┐
    │ MySQL   │    │ Postgres│   │ MongoDB │
    └────────┘    └────────┘    └─────────┘
```

**Benefits:** Independent schema evolution, polyglot persistence, true autonomy.
**Cost:** No cross-service joins, no distributed ACID transactions.

### 5.2 Saga Pattern (for distributed transactions)

Sequence of local transactions + compensating actions on failure.

> See [`distributed-transactions.md`](./distributed-transactions.md) for full Saga deep-dive (orchestration vs choreography).

```
   T1 (Charge) → T2 (Reserve) → T3 (Ship) → T4 (Confirm)
                                     ↓ fail
                  C3 ← C2 ← C1  (compensate in reverse)
```

### 5.3 CQRS (Command Query Responsibility Segregation)

Separate **write model** (commands) from **read model** (queries).

```
                ┌────────────┐
                │   Client    │
                └──┬─────┬────┘
                   │     │
       ┌───────────┘     └───────────┐
       │ (commands)        (queries) │
       ▼                              ▼
  ┌──────────┐                  ┌──────────┐
  │ Command  │                  │ Query    │
  │ Service  │                  │ Service  │
  └────┬─────┘                  └────┬─────┘
       │ writes                       │ reads
       ▼                              ▼
  ┌──────────┐  events  ┌──────────────────┐
  │  Write    │─────────►│  Read DB         │
  │  DB (RDB) │          │  (denormalized,  │
  └──────────┘          │   Elasticsearch) │
                        └──────────────────┘
```

**When to use:**
- Read and write workloads vastly different.
- Complex reads (joins, aggregations).
- Want to scale reads independently.

### 5.4 Event Sourcing

Don't store current state — store the **sequence of events** that produced it. Replay events to reconstruct state.

```
   Events Log:
   ┌──────────────────────────┐
   │ OrderCreated  (t=1)       │
   │ ItemAdded     (t=2)       │
   │ ItemRemoved   (t=3)       │
   │ PaymentDone   (t=4)       │
   │ Shipped       (t=5)       │
   └──────────────────────────┘
            │
            ▼
   Project to read model:
   ┌──────────────────────────┐
   │  Order #123              │
   │   status: Shipped         │
   │   items: [...]            │
   │   paid: true              │
   └──────────────────────────┘
```

**Benefits:** Full audit log, temporal queries, easy debugging.
**Cost:** Complex queries, snapshotting needed, schema evolution painful.

### 5.5 Transactional Outbox

Solves dual-write: atomic write of business data + event to publish.

```
   BEGIN TX
     INSERT order ...
     INSERT outbox (event_type, payload)
   COMMIT
            │
            ▼
   Debezium / Poller → Kafka
```

Critical for reliable event-driven architectures.

### 5.6 API Composition (vs distributed query)

Service composing data from multiple services for a single query.

```
   GET /order/{id}/full
        │
        ▼
   ┌──────────────────┐
   │ Composer / BFF    │
   └─┬────┬────┬────┬─┘
     │    │    │    │
     ▼    ▼    ▼    ▼
   Order User Cart Ship
```

Simpler than CQRS but doesn't scale for complex queries → switch to CQRS at scale.

---

## 6. Resilience Patterns (~10 min)

### 6.1 Circuit Breaker (Netflix Hystrix / Resilience4j)

Stop calling a failing service to give it time to recover.

```
   States:
                ┌──────────┐
                │  CLOSED  │  (normal — requests pass)
                └────┬─────┘
                     │ failures > threshold
                     ▼
                ┌──────────┐
                │   OPEN    │  (fail-fast — no requests sent)
                └────┬─────┘
                     │ timeout elapsed
                     ▼
                ┌──────────────┐
                │  HALF-OPEN   │  (let limited requests through)
                └─┬──────────┬─┘
        success │           │ failure
                ▼           ▼
            CLOSED        OPEN
```

```java
CircuitBreaker cb = CircuitBreaker.ofDefaults("paymentService");
Supplier<Result> decorated = CircuitBreaker.decorateSupplier(cb,
    () -> paymentService.charge(req));
Result r = Try.ofSupplier(decorated).recover(t -> fallback()).get();
```

### 6.2 Retry with Exponential Backoff + Jitter

```
   attempt = 0
   while attempt < maxAttempts:
       try call()
       except RetryableError:
           delay = min(maxDelay, base * 2^attempt) + random_jitter
           sleep(delay)
           attempt++
```

**Why jitter?** Prevents thundering herd when many clients retry simultaneously.

### 6.3 Timeout

Always set timeouts. **Default infinite timeout = bug waiting to happen.**

```java
RestTemplate rt = new RestTemplateBuilder()
    .setConnectTimeout(Duration.ofMillis(500))
    .setReadTimeout(Duration.ofSeconds(2))
    .build();
```

### 6.4 Bulkhead

Isolate resources so failure in one area doesn't sink the ship.

```
   Without bulkhead:
     One shared thread pool → 100 slow PaymentSvc calls
     block all other requests too.

   With bulkhead:
     ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
     │ Pool: Payment│  │ Pool: User   │  │ Pool: Order  │
     │  max=20      │  │  max=20      │  │  max=20      │
     └──────────────┘  └──────────────┘  └──────────────┘
     Payment failure doesn't starve User or Order calls.
```

### 6.5 Rate Limiting

Protect services from being overwhelmed.

Algorithms: **Token bucket**, **Leaky bucket**, **Fixed window**, **Sliding window**.

```
   Token Bucket:
     - Bucket holds N tokens, refilled at rate R/sec.
     - Each request consumes 1 token.
     - Empty bucket → reject (429 Too Many Requests).
```

### 6.6 Idempotency

Make operations safely repeatable. Critical for retries.

> See [`idempotency.md`](./idempotency.md) for full deep-dive.

### 6.7 Fallback / Graceful Degradation

When dependency fails, degrade gracefully:
- Cached data instead of fresh.
- Default values.
- Limited features.
- Friendly error.

### 6.8 Health Checks (liveness vs readiness)

- **Liveness**: am I alive? Restart container if not.
- **Readiness**: can I serve traffic? LB removes me if not.

```http
GET /health/live   → 200 OK
GET /health/ready  → 200 OK / 503 Service Unavailable
```

---

## 7. Observability Patterns (~5 min)

The "three pillars": Logs, Metrics, Traces.

### 7.1 Centralized Logging

All services ship logs to one place (ELK, Loki, Splunk, Datadog).

- **Structured JSON logs** with consistent fields.
- **Correlation ID** (traceId, requestId) on every log entry.

```json
{
  "timestamp": "2026-06-12T10:00:00Z",
  "service": "order-service",
  "level": "INFO",
  "traceId": "abc-123",
  "userId": "u-42",
  "message": "Order created"
}
```

### 7.2 Distributed Tracing

Trace a request as it hops through services. **OpenTelemetry, Jaeger, Zipkin, AWS X-Ray.**

```
   Trace ID: abc-123

   [ API Gateway        ] 5ms ────────────────────────┐
     [ Order Service     ] 30ms ─────────────────┐    │
       [ Payment Service ] 100ms ──────┐          │    │
       [ Inventory       ] 20ms        │          │    │
       [ DB query         ] 8ms                    │    │
                                                    │    │
                                       returns ────┘    │
                              returns ─────────────────┘
```

Each span has: name, start, duration, parentSpanId, tags.

### 7.3 Metrics & Dashboards (Prometheus + Grafana)

Track RED metrics per service:
- **R**ate — requests/sec
- **E**rrors — failures/sec
- **D**uration — latency p50, p95, p99

And USE for infrastructure:
- **U**tilization
- **S**aturation
- **E**rrors

### 7.4 Alerting (SLO-based)

Don't alert on "CPU > 80%". Alert on **user-impacting SLO violations**:
- Error rate > 1% for 5 min.
- p99 latency > 1s for 5 min.
- Availability < 99.9% in rolling window.

---

## 8. Deployment & Cross-Cutting Patterns (~5 min)

### 8.1 Service Discovery

Services dynamically find each other (since IPs change).

- **Client-side**: client queries registry (Eureka, Consul) → picks instance.
- **Server-side**: client → load balancer → picks instance (Kubernetes Service).

### 8.2 Externalized Configuration

Config NOT in code. Use Spring Cloud Config, Consul, AWS Parameter Store, Vault.

### 8.3 Blue-Green Deployment

```
   Blue (current production) ◄── 100% traffic
   Green (new version)        ◄── 0% traffic

   → flip LB → 100% to Green → instant rollback by flipping back.
```

### 8.4 Canary Release

Roll out to small % of users first.

```
   v1: 95% traffic
   v2:  5% traffic ─── monitor metrics ─── if healthy → 25% → 50% → 100%
```

### 8.5 Feature Flags

Toggle features at runtime (LaunchDarkly, Unleash). Decouple deployment from release.

### 8.6 Infrastructure as Code (Terraform, Pulumi, CloudFormation)

Versioned, reviewed, reproducible infra.

### 8.7 Container Orchestration (Kubernetes)

- Self-healing (restart crashed pods).
- Horizontal Pod Autoscaler (HPA).
- Rolling updates with zero downtime.
- Service discovery, secrets, config built-in.

---

## 9. End-to-End Reference Architecture (~3 min)

```
                       ┌─────────────────────┐
                       │      Clients         │
                       └──────────┬──────────┘
                                  │
                       ┌──────────▼──────────┐
                       │       CDN            │
                       └──────────┬──────────┘
                                  │
                       ┌──────────▼──────────┐
                       │  Load Balancer       │
                       └──────────┬──────────┘
                                  │
                       ┌──────────▼──────────┐
                       │ API Gateway / BFF    │  ← auth, rate-limit, routing
                       └──────────┬──────────┘
                                  │
                       ┌──────────▼──────────────────────────────┐
                       │     Service Mesh (Istio, sidecars)      │  ← mTLS,
                       │                                          │    retries,
                       │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐    │    tracing
                       │  │Order │ │User  │ │Inv.  │ │Pay.  │    │
                       │  └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘    │
                       │     │        │        │        │         │
                       │   each has own DB                        │
                       │   ┌─────┐ ┌────────┐ ┌────────┐ ┌─────┐ │
                       │   │MySQL│ │Postgres│ │MongoDB │ │Redis│ │
                       │   └─────┘ └────────┘ └────────┘ └─────┘ │
                       └──────────┬─────────────────────────────┘
                                  │
                       ┌──────────▼──────────┐
                       │  Kafka (events)      │
                       └──────────┬──────────┘
                                  │
                ┌─────────────────┼─────────────────┐
                ▼                 ▼                 ▼
         ┌──────────┐     ┌──────────┐      ┌──────────────┐
         │Saga Orch.│     │Analytics │      │ Notification │
         │(Temporal)│     │(Spark)   │      │ Service       │
         └──────────┘     └──────────┘      └──────────────┘

                       ┌──────────────────────────────┐
                       │ Observability                │
                       │ - Logs:   ELK / Loki         │
                       │ - Metrics: Prometheus + Grafana│
                       │ - Traces: Jaeger / OTel      │
                       │ - Alerts: PagerDuty           │
                       └──────────────────────────────┘
```

---

## 10. Anti-Patterns & Wrap-up (~2 min)

### Common Anti-Patterns

1. **Distributed Monolith** — services so tightly coupled they must deploy together. Worst of both worlds.
2. **Shared database** across services — breaks data ownership, schema changes ripple everywhere.
3. **Chatty interfaces** — N+1 calls for one user request → latency blow-up.
4. **Synchronous chains** — A→B→C→D, any failure cascades. Prefer async or aggregation.
5. **No observability** — you can't fix what you can't see.
6. **Premature microservices** — small team + greenfield → start with modular monolith.
7. **No versioning** of APIs — break clients silently.

### Summary

```
┌────────────────────────────────────────────────────────────────┐
│        MICROSERVICES PATTERNS CHEAT SHEET                      │
├────────────────────────────────────────────────────────────────┤
│ Decomposition: DDD Bounded Context, Strangler Fig, Sidecar     │
│                                                                │
│ Communication: API Gateway, BFF, Service Mesh                  │
│   - Sync: REST/gRPC for query                                   │
│   - Async: Kafka/SQS for events                                 │
│                                                                │
│ Data: DB-per-service, Saga, CQRS, Event Sourcing,              │
│       Transactional Outbox, API Composition                    │
│                                                                │
│ Resilience: Circuit Breaker, Retry+Jitter, Timeout,            │
│             Bulkhead, Rate Limit, Idempotency,                 │
│             Fallback, Health Checks                            │
│                                                                │
│ Observability: Centralized logs, distributed tracing (OTel),   │
│                RED+USE metrics, SLO-based alerting             │
│                                                                │
│ Deployment: Service Discovery, Externalized Config, Blue-Green,│
│             Canary, Feature Flags, IaC, Kubernetes             │
│                                                                │
│ Avoid: distributed monolith, shared DB, chatty calls,          │
│        sync chains, premature decomposition                    │
└────────────────────────────────────────────────────────────────┘
```

### Common Follow-up Questions

**Q: How do you handle a microservice that needs data from 5 other services?**
> Two options: API Composition for simple cases (composer service aggregates); CQRS for complex/heavy queries (materialized view via events).

**Q: How do you version APIs?**
> URI versioning (`/v1/users`) for major breaking changes, header versioning for minor. Maintain at least 2 versions during migration. Use contract testing (Pact) to detect breakage.

**Q: How small should a microservice be?**
> "Small enough that one team owns it end-to-end, large enough that splitting causes pain." Bounded contexts are a better guide than LOC.

**Q: How to handle cross-cutting concerns (auth, logging)?**
> Service mesh sidecars or shared libraries. Prefer infrastructure over code for things like mTLS, retries.

**Q: How do you test microservices?**
> Unit tests, integration tests with Testcontainers, contract tests (Pact), end-to-end on a staging env. Keep E2E small (smoke tests).

---

*This document simulates a complete 1-hour HLD interview on Microservices Design Patterns, covering decomposition, communication, data, resilience, observability, and deployment.*

