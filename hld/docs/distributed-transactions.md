# Handling Distributed Transactions — HLD Interview (1 Hour)

> **Simulated Interview Format**
> Interviewer asks broad questions → Candidate (you, a 6-7 YoE engineer) answers step by step, starting high-level and drilling into details. The flow mimics a real 60-minute system-design round.

---

## Table of Contents

1. [Opening — Clarify the Problem (~5 min)](#1-opening--clarify-the-problem-5-min)
2. [The Core Problem & ACID vs BASE (~5 min)](#2-the-core-problem--acid-vs-base-5-min)
3. [CAP & PACELC — The Theoretical Foundation (~5 min)](#3-cap--pacelc--the-theoretical-foundation-5-min)
4. [Two-Phase Commit (2PC) (~10 min)](#4-two-phase-commit-2pc-10-min)
5. [Three-Phase Commit (3PC) (~3 min)](#5-three-phase-commit-3pc-3-min)
6. [Saga Pattern — Choreography & Orchestration (~15 min)](#6-saga-pattern--choreography--orchestration-15-min)
7. [Transactional Outbox + CDC (~5 min)](#7-transactional-outbox--cdc-5-min)
8. [Idempotency, Retries & Exactly-Once (~5 min)](#8-idempotency-retries--exactly-once-5-min)
9. [Real-World Walkthrough: E-Commerce Order (~5 min)](#9-real-world-walkthrough-e-commerce-order-5-min)
10. [Wrap-up: Decision Framework & Trade-offs (~2 min)](#10-wrap-up-decision-framework--trade-offs-2-min)

---

## 1. Opening — Clarify the Problem (~5 min)

### Interviewer's Question
> "Design a system that handles distributed transactions across multiple microservices/databases. Walk me through your approach."

### Candidate's Response

Before jumping into the design, let me clarify the scope:

**Q: What's the use case?**
- Let's assume an **e-commerce order flow** as a running example:
  - `Order Service` → creates an order
  - `Payment Service` → charges the customer
  - `Inventory Service` → reserves stock
  - `Shipping Service` → schedules delivery
  - `Notification Service` → emails confirmation
- Each service has its **own database** (Database-per-Service pattern).
- A single business operation ("Place Order") spans 4-5 services.

**Q: What are our goals?**
- **Atomicity across services** — either all steps succeed, or the system ends up in a consistent state (via compensation).
- **High availability** — no single coordinator should block the entire flow.
- **Scalability** — millions of orders per day.
- **Eventual consistency is acceptable** — strong consistency across services is impractical at scale.

**Q: Non-functional requirements**

| Requirement       | Target                                          |
|-------------------|-------------------------------------------------|
| Throughput        | 10K+ TPS                                        |
| Latency (p99)     | < 2 seconds for end-to-end order placement      |
| Consistency       | Eventual (within seconds to minutes)            |
| Availability      | 99.99%                                          |
| Data loss         | Zero tolerance for committed transactions       |

**Q: Constraints?**
- Polyglot persistence — different services may use different DBs (PostgreSQL, MongoDB, DynamoDB).
- Network is unreliable — partitions, timeouts, retries are the norm.
- Services can be deployed/restarted independently.

---

## 2. The Core Problem & ACID vs BASE (~5 min)

### Why Are Distributed Transactions Hard?

In a **monolith with one DB**, you get ACID for free:
```sql
BEGIN;
  UPDATE accounts SET balance = balance - 100 WHERE id = 'A';
  UPDATE accounts SET balance = balance + 100 WHERE id = 'B';
COMMIT;
```
Either both succeed or both roll back. Easy.

In a **distributed system**, each service has its own DB:
```
Service A (DB-A)        Service B (DB-B)        Service C (DB-C)
   commit ✅              commit ✅              commit ❌ (network fail)
```
Now what? Service A and B already committed. Service C didn't. The system is **inconsistent**.

### ACID vs BASE

```
┌─────────────────────────────────────────────────────────────────┐
│   ACID (Traditional RDBMS)         BASE (Distributed Systems)    │
├─────────────────────────────────────────────────────────────────┤
│   Atomicity                         Basically Available           │
│   Consistency                       Soft state                     │
│   Isolation                         Eventually consistent          │
│   Durability                                                       │
│                                                                    │
│   Strong guarantees, scales         Weaker guarantees, scales     │
│   vertically, single DB.            horizontally, multiple DBs.    │
└─────────────────────────────────────────────────────────────────┘
```

In a distributed world, we **trade strict ACID for BASE** and use patterns to recover from failures.

---

## 3. CAP & PACELC — The Theoretical Foundation (~5 min)

### CAP Theorem (Brewer)

> In the presence of a **Network Partition**, you must choose between **Consistency** and **Availability**.

```
                    ┌───────────────┐
                    │   CAP THEOREM  │
                    │                │
                    │       C        │
                    │      ╱ ╲       │
                    │     ╱   ╲      │
                    │    ╱     ╲     │
                    │   A───────P    │
                    └───────────────┘

  C — Consistency: every read sees the latest write.
  A — Availability: every request gets a response.
  P — Partition Tolerance: system works despite network failures.

  In real distributed systems, P is non-negotiable (networks fail).
  So in practice: choose CP or AP.
```

### PACELC Extension

> **If Partition (P), choose A or C. Else (E), choose Latency (L) or Consistency (C).**

| System         | PACELC      | Notes                                  |
|----------------|-------------|----------------------------------------|
| PostgreSQL     | PC/EC       | Strong consistency always              |
| DynamoDB       | PA/EL       | Highly available, eventually consistent|
| Cassandra      | PA/EL       | Tunable, but AP-leaning                |
| MongoDB        | PA/EC       | Configurable                           |
| Spanner        | PC/EC       | Globally consistent (TrueTime)         |

**Implication for distributed transactions:**
- 2PC is **CP** → blocks during partition (sacrifices availability).
- Saga is **AP** → continues during partition (sacrifices strong consistency, uses compensation).

---

## 4. Two-Phase Commit (2PC) (~10 min)

### Concept

A **coordinator** orchestrates all participants. Commit happens in **two phases**: Prepare and Commit.

### Phase 1: Prepare (Voting)

```
   ┌──────────────┐
   │ Coordinator  │
   └──────┬───────┘
          │
          │ "Can you commit?"
          ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ Participant 1│  │ Participant 2│  │ Participant 3│
   │              │  │              │  │              │
   │ Lock rows    ���  │ Lock rows    │  │ Lock rows    │
   │ Write to WAL │  │ Write to WAL │  │ Write to WAL │
   │              │  │              │  │              │
   │ Reply: YES   │  │ Reply: YES   │  │ Reply: YES   │
   └──────────────┘  └──────────────┘  └──────────────┘
```

### Phase 2: Commit (Decision)

```
   ┌──────────────┐
   │ Coordinator  │  All YES? → COMMIT
   │              │  Any NO?  → ABORT
   └──────┬───────┘
          │
          │ "COMMIT" or "ABORT"
          ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ Participant 1│  │ Participant 2│  │ Participant 3│
   │ Commit ✅    │  │ Commit ✅    │  │ Commit ✅    │
   │ Release lock │  │ Release lock │  │ Release lock │
   └──────────────┘  └──────────────┘  └──────────────┘
```

### Pseudocode

```java
// Coordinator
public boolean executeTransaction(List<Participant> participants) {
    // Phase 1: PREPARE
    for (Participant p : participants) {
        if (!p.prepare()) {
            abortAll(participants);
            return false;
        }
    }

    // Phase 2: COMMIT
    for (Participant p : participants) {
        p.commit();   // If this fails, must retry indefinitely (blocking!)
    }
    return true;
}
```

### Pros & Cons

**Pros:**
- ✅ Strong consistency (ACID across nodes).
- ✅ Simple mental model.
- ✅ Supported by XA-compliant databases (PostgreSQL, MySQL, Oracle).

**Cons:**
- ❌ **Blocking** — if coordinator crashes after Phase 1, participants hold locks indefinitely.
- ❌ **Single point of failure** (coordinator).
- ❌ **Slow** — multiple network round-trips + locks held during entire transaction.
- ❌ **Not scalable** — locks reduce throughput. Bad for microservices.
- ❌ Doesn't work well across heterogeneous data stores (Mongo, Redis, etc. don't support XA well).

### When to Use 2PC
- Within a **single organization** and **trusted network**.
- Low transaction volume, strong consistency required.
- XA-compliant databases only.
- Examples: Banking core systems, legacy ERP.

**In modern microservices: AVOID 2PC.** Use Saga instead.

---

## 5. Three-Phase Commit (3PC) (~3 min)

3PC addresses the blocking problem of 2PC by adding a **Pre-Commit** phase:

```
Phase 1: CanCommit?  →  Phase 2: PreCommit  →  Phase 3: DoCommit
```

The pre-commit phase ensures all participants are ready and tells them to expect a commit, so they can make a decision **independently** if the coordinator crashes.

**Pros:** Non-blocking under coordinator failure.
**Cons:** Still slow (3 round-trips), doesn't handle network partitions safely, rarely used in practice.

**Verdict:** Mostly theoretical. Not used in production.

---

## 6. Saga Pattern — Choreography & Orchestration (~15 min)

> **The Saga pattern is the go-to solution for distributed transactions in microservices.**

### Core Idea

Break a distributed transaction into a **sequence of local transactions**. Each step:
1. Updates its local DB.
2. Publishes an event (or sends a command) to trigger the next step.

If any step fails, execute **compensating transactions** to undo previous steps.

```
   T1 → T2 → T3 → T4   ✅ Success path
                  ↓ (fail)
   C3 ← C2 ← C1        ✅ Compensation path (undo in reverse)
```

**Key insight:** There's no rollback. Compensation is a **semantic undo** (e.g., "Refund Payment" undoes "Charge Payment").

### 6.1 Choreography-Based Saga

Services communicate via **events** (no central coordinator). Each service knows what to do when it receives an event.

```
                     ┌──────────────┐
                     │   Customer   │
                     │   PlaceOrder │
                     └──────┬───────┘
                            ▼
                     ┌──────────────┐
                     │ Order Service │
                     │ CREATE order  │
                     │ (PENDING)     │
                     └──────┬───────┘
                            │
                            │ publish: OrderCreated
                            ▼
                     ┌──────────────────────────┐
                     │      Event Bus (Kafka)    │
                     └─────┬───────────────┬─────┘
                           │               │
                           ▼               ▼
                  ┌──────────────┐  ┌──────────────────┐
                  │Payment Service│  │Inventory Service │
                  │CHARGE customer│  │RESERVE stock     │
                  └──────┬───────┘  └────────┬─────────┘
                         │                   │
                         │ PaymentCompleted  │ StockReserved
                         ▼                   ▼
                     ┌──────────────────────────┐
                     │      Event Bus (Kafka)    │
                     └─────────────┬─────────────┘
                                   ▼
                         ┌──────────────────┐
                         │ Shipping Service │
                         │ SCHEDULE shipment│
                         └────────┬─────────┘
                                  │
                                  │ ShipmentScheduled
                                  ▼
                         ┌──────────────────┐
                         │  Order Service   │
                         │  UPDATE → CONFIRMED
                         └──────────────────┘
```

**On failure (e.g., Inventory out of stock):**

```
   InventoryReservationFailed event ──► Order Service: cancel order
                                  └──► Payment Service: refund payment
```

**Pros:**
- ✅ No single point of failure (no coordinator).
- ✅ Loose coupling — services don't know about each other.
- ✅ Highly scalable.
- ✅ Simple for small workflows (3-4 services).

**Cons:**
- ❌ **Hard to track** the overall workflow state.
- ❌ **Cyclic dependencies** can emerge — Service A subscribes to B's event, and vice versa.
- ❌ Debugging is painful — need distributed tracing (Jaeger, Zipkin).
- ❌ **Complexity grows fast** — adding a new step requires changes in multiple services.

**When to use:** Simple workflows with few steps (≤ 4 services).

---

### 6.2 Orchestration-Based Saga ⭐ Recommended for Complex Workflows

A central **Saga Orchestrator** explicitly defines the workflow and tells each service what to do.

```
                          ┌─────────────────────┐
                          │  Saga Orchestrator  │
                          │  (Order Saga)        │
                          │                      │
                          │  State Machine:      │
                          │   START              │
                          │   → CHARGE_PAYMENT   │
                          │   → RESERVE_STOCK    │
                          │   → SCHEDULE_SHIPMENT│
                          │   → CONFIRM_ORDER    │
                          │   → END              │
                          └──────────┬───────────┘
                                     │
            ┌────────────────────────┼────────────────────────┐
            │                        │                        │
            ▼ (1) ChargePayment      │                        │
   ┌──────────────┐                  │                        │
   │Payment Service│                  │                        │
   │ → PaymentDone │──┐                                       │
   └──────────────┘  │                                        │
                     │                                        │
                     ▼ (2) ReserveStock                       │
                ┌──────────────────┐                          │
                │Inventory Service │                          │
                │ → StockReserved  │──┐                       │
                └──────────────────┘  │                       │
                                      │                       │
                                      ▼ (3) ScheduleShipment  │
                                 ┌──────────────────┐         │
                                 │Shipping Service  │         │
                                 │ → ShipmentDone   │──┐      │
                                 └──────────────────┘  │      │
                                                       │      │
                                                       ▼ (4)  │
                                                  ┌──────────┐│
                                                  │Order Svc ││
                                                  │ → CONFIRM││
                                                  └──────────┘│
                                                              │
                                              All replies back to Orchestrator
```

### Saga Orchestrator State Machine

```
                  ┌─────────┐
                  │  START   │
                  └────┬─────┘
                       │
                       ▼
              ┌────────────────┐  fail   ┌──────────────────┐
              │CHARGE_PAYMENT  │────────►│COMPENSATE: none  │
              └───────┬────────┘         └──────────────────┘
                      │ success
                      ▼
              ┌────────────────┐  fail   ┌──────────────────┐
              │RESERVE_STOCK   │────────►│COMPENSATE:       │
              └───────┬────────┘         │  Refund payment   │
                      │ success          └──────────────────┘
                      ▼
              ┌────────────────┐  fail   ┌──────────────────┐
              │SCHEDULE_SHIP   │────────►│COMPENSATE:       │
              └───────┬────────┘         │  Release stock   │
                      │ success          │  Refund payment  │
                      ▼                  └──────────────────┘
              ┌────────────────┐
              │CONFIRM_ORDER   │
              └───────┬────────┘
                      ▼
                  ┌─────────┐
                  │   END    │
                  └─────────┘
```

### Pseudocode (Orchestrator)

```java
public class OrderSagaOrchestrator {

    public void execute(OrderRequest req) {
        SagaContext ctx = new SagaContext(req);
        saveSagaState(ctx, "STARTED");

        try {
            // Step 1: Charge payment
            PaymentResult p = paymentService.charge(req.userId, req.amount);
            ctx.paymentId = p.id;
            saveSagaState(ctx, "PAYMENT_CHARGED");

            // Step 2: Reserve inventory
            ReservationResult r = inventoryService.reserve(req.items);
            ctx.reservationId = r.id;
            saveSagaState(ctx, "STOCK_RESERVED");

            // Step 3: Schedule shipment
            ShipmentResult s = shippingService.schedule(req.address, req.items);
            ctx.shipmentId = s.id;
            saveSagaState(ctx, "SHIPMENT_SCHEDULED");

            // Step 4: Confirm order
            orderService.confirm(req.orderId);
            saveSagaState(ctx, "COMPLETED");

        } catch (Exception e) {
            compensate(ctx);   // Run undo steps in reverse
            saveSagaState(ctx, "COMPENSATED");
            throw e;
        }
    }

    private void compensate(SagaContext ctx) {
        // Reverse order — undo what was done
        if (ctx.shipmentId != null) shippingService.cancel(ctx.shipmentId);
        if (ctx.reservationId != null) inventoryService.release(ctx.reservationId);
        if (ctx.paymentId != null) paymentService.refund(ctx.paymentId);
    }
}
```

### Orchestration Pros & Cons

**Pros:**
- ✅ **Explicit workflow** — easy to understand and visualize.
- ✅ **Centralized error handling** and compensation logic.
- ✅ **Easy to add/modify steps** — only orchestrator changes.
- ✅ Excellent observability.
- ✅ Built-in support in tools like **Temporal**, **Camunda**, **AWS Step Functions**, **Netflix Conductor**.

**Cons:**
- ❌ Orchestrator is a **potential bottleneck** and SPOF (mitigate with clustering).
- ❌ Risk of **business logic leaking** into the orchestrator.
- ❌ Tighter coupling — orchestrator knows all services.

### Choreography vs Orchestration — Comparison

```
┌──────────────────────┬──────────────────────┬──────────────────────┐
│ Aspect               │ Choreography         │ Orchestration         │
├──────────────────────┼──────────────────────┼──────────────────────┤
│ Coordinator          │ None                 │ Central orchestrator  │
│ Coupling             │ Loose                │ Tighter               │
│ Visibility           │ Poor                 │ Excellent             │
│ Complexity (small)   │ Simple               │ Overhead              │
│ Complexity (large)   │ Hard to manage       │ Manageable            │
│ Failure handling     │ Distributed          │ Centralized           │
│ Best for             │ ≤4 services           │ 5+ services / complex │
│ Tools                │ Kafka, RabbitMQ      │ Temporal, Camunda     │
└──────────────────────┴──────────────────────┴──────────────────────┘
```

### Compensating Transactions — Rules

1. **Must be idempotent** — may be retried multiple times.
2. **Must be commutative when possible** — order should not matter.
3. **Cannot fail** — if it does, escalate (alert, manual intervention, dead letter queue).
4. **Semantic, not physical** — "Refund $100" not "Reverse the DB write".
5. **Some actions cannot be compensated** — e.g., sending an email. Handle via "pre-checks" or "warning emails".

---

## 7. Transactional Outbox + CDC (~5 min)

### The Dual-Write Problem

A common bug in microservices:

```java
// ❌ DANGEROUS — dual write problem
public void createOrder(Order o) {
    db.save(o);                    // Step 1: DB write
    kafka.publish("OrderCreated"); // Step 2: Message publish
}
```

**What if Step 1 succeeds but Step 2 fails?**
- Order is in DB but no event is published.
- Downstream services never know. Inconsistency.

**What if Step 1 fails but Step 2 succeeds?**
- Event is published but no order exists. Worse inconsistency.

### Solution: Transactional Outbox Pattern

Write the event to an **outbox table in the same DB transaction** as the business data. A separate process reads the outbox and publishes to Kafka.

```
┌────────────────────────────────────────────────────────────┐
│              Service DB (single transaction)               │
│                                                            │
│   ┌─────────────┐         ┌─────────────────────┐         │
│   │  orders     │         │  outbox             │         │
│   │             │         │                     │         │
│   │ id, status, │         │ id, event_type,     │         │
│   │ amount...   │         │ payload, created_at │         │
│   └─────────────┘         └─────────────────────┘         │
│                                                            │
│   BEGIN;                                                   │
│     INSERT INTO orders ...                                 │
│     INSERT INTO outbox (event_type, payload) VALUES (...); │
│   COMMIT;   ← Atomic! Both succeed or both fail.           │
└────────────────────────────────────────────────────────────┘
              │
              │  (separate process polls or uses CDC)
              ▼
   ┌────────────────────┐
   │ Outbox Relay /      │
   │ Debezium (CDC)      │
   │                     │
   │ Reads new outbox    │
   │ entries → publishes │
   │ to Kafka            │
   └─────────┬───────────┘
             ▼
   ┌────────────────────┐
   │   Kafka Topic       │
   │   "order.events"    │
   └─────────────────────┘
```

### Two Implementation Options

**1. Polling Publisher (simple):**
- Background job polls `outbox` table every 100ms.
- Publishes to Kafka, marks rows as `processed`.

**2. Change Data Capture (CDC) with Debezium (recommended):**
- Debezium tails the DB's WAL/binlog.
- Streams changes from `outbox` table directly to Kafka.
- No polling overhead. Near real-time.

### Pros
- ✅ **Atomicity guaranteed** by the DB transaction.
- ✅ **At-least-once delivery** to Kafka (combined with idempotency = exactly-once effect).
- ✅ Works with any RDBMS.
- ✅ Critical building block for Saga + event-driven architectures.

### Cons
- ⚠ Additional infrastructure (Debezium / Kafka Connect).
- ⚠ Slight latency (CDC lag, usually <100ms).
- ⚠ Outbox table grows — needs periodic cleanup.

---

## 8. Idempotency, Retries & Exactly-Once (~5 min)

### Why Idempotency Matters

In distributed systems, **everything will be retried**:
- Network timeouts → retry.
- Service crash mid-operation → retry on recovery.
- Saga compensation → retry until success.

If operations are not idempotent, you get **duplicate charges, double inventory deductions, etc.**

### Achieving Idempotency

**1. Idempotency Key (client-supplied unique ID):**

```java
public PaymentResult charge(String idempotencyKey, BigDecimal amount) {
    // Check if we've already processed this key
    PaymentResult existing = db.findByIdempotencyKey(idempotencyKey);
    if (existing != null) {
        return existing;   // Return cached result, don't re-charge
    }

    // Process and store with key
    PaymentResult result = processCharge(amount);
    db.save(idempotencyKey, result);
    return result;
}
```

**2. Database constraints (unique index on natural key).**

**3. Conditional updates:**
```sql
UPDATE inventory SET stock = stock - 5
WHERE product_id = 'X' AND version = 42;
-- If version doesn't match, no update happens (optimistic locking).
```

**4. Deduplicate at consumer (Kafka):**
- Track processed message IDs in a dedup table (e.g., Redis with TTL).

### Exactly-Once Semantics — The Truth

> **"Exactly-once delivery" doesn't really exist in distributed systems.**

What we actually achieve:
```
At-least-once delivery + Idempotent consumer = Effectively-once processing
```

Kafka offers "exactly-once semantics" within Kafka (producer→broker→consumer), but cross-system exactly-once requires idempotency on the consumer side.

### Retry Strategy

```
┌──────────────────────────────────────────────────────────────┐
│              RETRY STRATEGY (best practice)                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   1. Exponential backoff with jitter                         │
│      delay = min(maxDelay, base * 2^attempt) + random jitter│
│                                                              │
│   2. Bounded retries (e.g., 5 attempts max)                  │
│                                                              │
│   3. Circuit breaker — stop retrying if service is down      │
│      (Resilience4j, Hystrix)                                 │
│                                                              │
│   4. Dead Letter Queue (DLQ) — failed messages after        │
│      max retries go to a DLQ for manual investigation        │
│                                                              │
│   5. Distinguish retryable vs non-retryable errors           │
│      - Retry: timeouts, 5xx, connection errors               │
│      - Don't retry: 4xx (bad request), validation errors     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 9. Real-World Walkthrough: E-Commerce Order (~5 min)

### End-to-End Architecture

```
                       ┌──────────────┐
                       │   Customer   │
                       └──────┬───────┘
                              │ POST /orders
                              ▼
                       ┌──────────────┐
                       │ API Gateway   │
                       └──────┬───────┘
                              │
                              ▼
              ┌──────────────────────────────┐
              │   Order Service (HTTP)        │
              │                               │
              │  BEGIN TX                     │
              │    INSERT order (PENDING)     │
              │    INSERT outbox(OrderCreated)│
              │  COMMIT                       │
              └──────────────┬───────────────┘
                             │
                       (returns 202 Accepted to client immediately)
                             │
                             ▼
                ┌────────────────────────────┐
                │ Debezium (CDC)              │
                │ tails outbox table → Kafka  │
                └────────────┬────────────────┘
                             ▼
                ┌────────────────────────────┐
                │ Kafka: order.events topic   │
                └────────────┬────────────────┘
                             ▼
                ┌────────────────────────────┐
                │   Saga Orchestrator         │
                │   (Temporal Workflow)       │
                │                             │
                │   workflow: PlaceOrderSaga  │
                └─────────────┬───────────────┘
                              │
        ┌─────────────────────┼─────────────────────────────┐
        │                     │                             │
        ▼ (1) ChargePayment   │                             │
  ┌──────────────┐            │                             │
  │Payment Service│            │                             │
  │  Stripe API   │            │                             │
  └──────┬───────┘            │                             │
         │ paymentId          │                             │
         ▼                    │                             │
   (2) ReserveStock           │                             │
  ┌──────────────────┐        │                             │
  │Inventory Service │        │                             │
  └──────┬───────────┘        │                             │
         │ reservationId      │                             │
         ▼                    │                             │
   (3) ScheduleShipment       │                             │
  ┌──────────────────┐        │                             │
  │Shipping Service  │        │                             │
  └──────┬───────────┘        │                             │
         │ shipmentId         │                             │
         ▼                    │                             │
   (4) ConfirmOrder           │                             │
  ┌──────────────────┐        │                             │
  │ Order Service    │        │                             │
  │ UPDATE → CONFIRMED        │                             │
  └──────┬───────────┘        │                             │
         │                    │                             │
         ▼                    │                             │
   (5) SendNotification ◄─────┘                             │
  ┌──────────────────┐                                      │
  │Notification Svc  │                                      │
  │ Email / SMS      │                                      │
  └──────────────────┘                                      │
                                                            │
                                                            ▼
                                     ┌────────────────────────────┐
                                     │  On Failure: Compensation  │
                                     │   - Cancel shipment        │
                                     │   - Release inventory      │
                                     │   - Refund payment         │
                                     │   - Mark order CANCELLED   │
                                     └────────────────────────────┘
```

### Key Design Decisions

| Concern              | Solution                                              |
|----------------------|-------------------------------------------------------|
| Cross-service atomicity | Saga (orchestration) via Temporal                  |
| Reliable event publishing | Transactional Outbox + Debezium CDC               |
| Idempotent operations | Idempotency key on every API + dedup tables          |
| Retries              | Exponential backoff + jitter, max 5 attempts          |
| Failures             | Compensating transactions executed in reverse         |
| Observability        | Distributed tracing (OpenTelemetry + Jaeger)          |
| Saga state           | Persisted in Temporal's DB (survives crashes)         |
| Async response       | Return 202 to client immediately, notify on completion|

### Failure Scenarios & Recovery

```
┌─────────────────────────────────────────────────────────────┐
│ Scenario 1: Payment succeeds, Inventory out of stock        │
├─────────────────────────────────────────────────────────────┤
│ → Saga catches InventoryReservationFailedException          │
│ → Compensation: Refund payment via Stripe API               │
│ → Order marked CANCELLED                                    │
│ → Customer notified                                         │
├─────────────────────────────────────────────────────────────┤
│ Scenario 2: Saga orchestrator crashes mid-workflow          │
├─────────────────────────────────────────────────────────────┤
│ → Temporal persists workflow state to its DB                │
│ → On restart, workflow resumes from last completed step     │
│ → No double execution (idempotency + dedup)                 │
├─────────────────────────────────────────────────────────────┤
│ Scenario 3: Network timeout calling Payment Service          │
├─────────────────────────────────────────────────────────────┤
│ → Retry with exponential backoff                            │
│ → Use same idempotency key → no double charge               │
│ → If still failing after max retries → compensate          │
├─────────────────────────────────────────────────────────────┤
│ Scenario 4: Compensation itself fails (e.g., refund fails)  │
├─────────────────────────────────────────────────────────────┤
│ → Retry compensation indefinitely                           │
│ → If still failing → DLQ + alert ops team                   │
│ → Manual intervention (refund via admin tool)               │
└─────────────────────────────────────────────────────────────┘
```

---

## 10. Wrap-up: Decision Framework & Trade-offs (~2 min)

### Decision Flowchart

```
                  ┌─────────────────────────────────┐
                  │ Need atomic operation across     │
                  │ multiple services/databases?     │
                  └──────────────┬───────────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Single DB / Single service?  │
                  └──────────┬───────────────────┘
                             │
                ┌────────────┼────────────┐
                │            │            │
              YES          NO ▼          NO
                │            │            │
                ▼     ┌──────────────────────┐
          Use local   │ Strong consistency    │
          ACID TX     │ required + low TPS?   │
                      └──────┬────────────────┘
                             │
                  ┌──────────┼──────────┐
                  │          │          │
                YES          │         NO
                  │          │          │
                  ▼          │          ▼
            Use 2PC          │   ┌──────────────────┐
            (XA)             │   │ Simple workflow,  │
                             │   │ ≤4 services?      │
                             │   └──────┬────────────┘
                             │          │
                             │ ┌────────┼────────┐
                             │ │        │        │
                             │ YES      │       NO
                             │ │        │        │
                             │ ▼        │        ▼
                             │ Choreography│  Orchestration
                             │ (Kafka)    │  (Temporal / Camunda)
                             │            │
                             └────────────┴────────────────┐
                                                           │
                              In ALL event-driven cases:   │
                              Use Transactional Outbox     │
                              + Idempotency + Retries      │
```

### Final Recommendations Summary

```
┌────────────────────────────────────────────────────────────────┐
│             PRODUCTION RECIPE FOR DISTRIBUTED TX               │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│   1. Avoid 2PC in microservices. Period.                       │
│                                                                │
│   2. Default to Saga pattern.                                  │
│      - Orchestration for complex (5+ steps).                   │
│      - Choreography for simple (≤4 steps).                     │
│                                                                │
│   3. Use Transactional Outbox + Debezium (CDC) for             │
│      reliable event publishing.                                │
│                                                                │
│   4. Make ALL operations idempotent.                           │
│      - Idempotency keys on writes.                             │
│      - Dedup at message consumers.                             │
│                                                                │
│   5. Use a workflow engine (Temporal, Camunda) for             │
│      complex sagas — don't build orchestration from scratch.   │
│                                                                │
│   6. Retry with exponential backoff + jitter.                  │
│      Circuit breakers to prevent cascading failures.           │
│                                                                │
│   7. Embrace eventual consistency — explain it to              │
│      product and design UX around it (e.g., "Order pending").  │
│                                                                │
│   8. Observability is non-negotiable.                          │
│      - Distributed tracing (OpenTelemetry, Jaeger).            │
│      - Saga state dashboards.                                  │
│      - Alerts on stuck/failed sagas.                           │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Common Interview Follow-up Questions & Answers

**Q: Why not just use 2PC with Atomikos or Narayana?**
> 2PC blocks during coordinator failure → not acceptable for HA systems. It also doesn't work well with NoSQL databases (no XA support). Doesn't scale to microservices because of lock contention.

**Q: How do you handle a saga step that takes hours (e.g., manual approval)?**
> Use a workflow engine that supports long-running workflows (Temporal handles this natively). Workflow state is persisted; activities can wait days/weeks. Use timeouts + escalation.

**Q: What if a compensation cannot logically be performed (e.g., money already wired to external bank)?**
> Some operations are not compensatable. Handle via:
> - **Pre-checks** before commit (e.g., verify funds).
> - **Pessimistic reservations** (hold funds, release/capture later).
> - **Forward recovery** instead of backward (move to a different terminal state, e.g., "manual review queue").

**Q: How do you guarantee ordering of events in a saga?**
> - Kafka with a single partition per saga key (e.g., orderId) → ordered.
> - Orchestrator naturally enforces sequence (one step at a time).

**Q: How do you avoid the "saga zombie" problem (a saga stuck forever)?**
> - Each saga has a global timeout (e.g., 24h).
> - Periodic reconciliation job scans for stuck sagas.
> - Dashboards + alerts.
> - Manual intervention tooling for ops.

**Q: SQL vs NoSQL implications?**
> - SQL gives you ACID locally → outbox pattern works great.
> - NoSQL (DynamoDB, Mongo) — outbox is harder. Use:
>   - DynamoDB Streams for CDC.
>   - MongoDB Change Streams.
>   - Or write events via a transaction (Mongo 4.x+ supports multi-doc TX).

---

## Appendix: Quick Reference Card

```
┌────────────────────────────────────────────────────────────────┐
│              DISTRIBUTED TRANSACTIONS CHEAT SHEET              │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  2PC          → Strong consistency, blocking, slow. AVOID in   │
│                 microservices. Only for trusted XA setups.     │
│                                                                │
│  3PC          → Theoretical. Not used in practice.             │
│                                                                │
│  Saga         → THE pattern for microservices.                 │
│   ├─ Choreography  → Event-driven, no coordinator. Simple.    │
│   └─ Orchestration → Central workflow engine. Complex flows.  │
│                                                                │
│  Outbox       → Solves dual-write problem. Atomic DB+event.    │
│                                                                │
│  CDC          → Debezium tails WAL/binlog, streams to Kafka.   │
│                                                                │
│  Idempotency  → Keys + dedup tables + conditional updates.    │
│                                                                │
│  Retries      → Exponential backoff + jitter + circuit breaker.│
│                                                                │
│  Compensation → Semantic undo, idempotent, can't fail.        │
│                                                                │
│  Tools:                                                        │
│   - Workflow:  Temporal, Camunda, AWS Step Functions, Conductor│
│   - Events:    Kafka, RabbitMQ, AWS SNS+SQS                    │
│   - CDC:       Debezium, Maxwell, AWS DMS                      │
│   - Tracing:   OpenTelemetry, Jaeger, Zipkin                   │
│                                                                │
│  Golden rule: Embrace eventual consistency.                    │
│               Make everything idempotent.                       │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

*This document simulates a complete 1-hour HLD interview on Handling Distributed Transactions, covering theory (CAP, ACID/BASE), patterns (2PC, Saga, Outbox), real-world implementation, and operational concerns.*

