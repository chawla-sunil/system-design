# Designing Idempotent APIs — HLD Interview (1 Hour)

> **Simulated Interview Format**
> Interviewer asks broad questions → Candidate (you, a 6-7 YoE engineer) walks through what idempotency means, why it matters, and how to design idempotent systems end-to-end.

---

## Table of Contents

1. [Opening — Clarify the Question (~3 min)](#1-opening--clarify-the-question-3-min)
2. [What is Idempotency? (~5 min)](#2-what-is-idempotency-5-min)
3. [Why Idempotency Matters (~5 min)](#3-why-idempotency-matters-5-min)
4. [HTTP Methods & Idempotency (~5 min)](#4-http-methods--idempotency-5-min)
5. [The Idempotency Key Pattern ⭐ (~10 min)](#5-the-idempotency-key-pattern--10-min)
6. [Storage Design for Idempotency (~10 min)](#6-storage-design-for-idempotency-10-min)
7. [Idempotency in Distributed Systems (~10 min)](#7-idempotency-in-distributed-systems-10-min)
8. [Real-World Example: Payment API (~7 min)](#8-real-world-example-payment-api-7-min)
9. [Edge Cases & Failure Scenarios (~3 min)](#9-edge-cases--failure-scenarios-3-min)
10. [Wrap-up & Cheat Sheet (~2 min)](#10-wrap-up--cheat-sheet-2-min)

---

## 1. Opening — Clarify the Question (~3 min)

### Interviewer's Question
> "Design an idempotent API. Walk me through what idempotency means, how you'd implement it for, say, a Payment service, and how you'd handle edge cases."

### Candidate's Response

Great question. Let me clarify the scope:

- I'll use a **Payment API** as the running example (most commonly asked).
- Goal: ensure that **even if the client retries** (network failure, timeout, crash), the customer is **charged exactly once**.
- I'll cover:
  1. Definitions and HTTP method semantics.
  2. The **Idempotency Key** pattern (industry standard — Stripe, AWS, etc.).
  3. Storage design.
  4. Distributed-system concerns (concurrency, expiry, sagas).
  5. Edge cases and failure modes.

---

## 2. What is Idempotency? (~5 min)

### Definition

> An operation is **idempotent** if performing it multiple times has the **same effect as performing it once**.

```
   f(x) is idempotent ↔ f(f(x)) == f(x)
```

### Examples

**Idempotent:**
- `SET balance = 100` (always ends up at 100, no matter how many times called).
- `DELETE order WHERE id=42` (after first call, subsequent are no-ops).
- `mailbox.markAsRead(msgId)` (already read → stays read).

**NOT idempotent:**
- `balance = balance + 10` (each call changes state by +10).
- `INSERT order ...` (each call creates a new row).
- `chargeCard($50)` (each call charges $50 again).

### Idempotent vs Safe vs Pure

- **Safe**: doesn't modify state (e.g., GET). Always idempotent.
- **Idempotent**: may modify state, but repeated calls = same end state.
- **Pure**: no side effects, output depends only on input.

```
   Pure ⊂ Safe ⊂ Idempotent (in terms of guarantees)
```

---

## 3. Why Idempotency Matters (~5 min)

### Networks Are Unreliable

```
   Client  → POST /payments  → Server   (charges card ✅)
   Client                         │
                                   │ (response lost ✗ network issue)
                                   ▼
   Client  doesn't get response → retries
   Client  → POST /payments  → Server   (charges card AGAIN ❌❌)
```

**Customer is charged twice.** Disaster.

### The Two-Generals Problem

In distributed systems, the **sender can never be 100% sure** the receiver got the message.

- Did the server fail before processing? → Safe to retry.
- Did the server process but the response was lost? → Retry causes duplicate.

> **Without idempotency, the client has to choose between "retry and risk duplicates" or "don't retry and risk failure". With idempotency, retry is always safe.**

### Where Retries Happen

1. **HTTP clients** (5xx, timeouts → automatic retry).
2. **Message queues** (Kafka, SQS — at-least-once delivery).
3. **Sagas / Workflow engines** (Temporal, Camunda).
4. **Mobile networks** (poor connectivity → user taps button again).
5. **Load balancers** (failover to another instance, may retry).

### Cost of Non-Idempotency

| Service        | Duplicate impact                         |
|----------------|------------------------------------------|
| Payment        | Double charge → chargebacks, lost trust  |
| Inventory      | Double deduction → overselling           |
| Email          | Double send → spam complaints            |
| Database write | Duplicate rows → data corruption         |
| Banking xfer   | Double transfer → audit + legal disaster |

---

## 4. HTTP Methods & Idempotency (~5 min)

### Standard Semantics (RFC 7231)

```
┌────────┬─────────────┬─────────────┬───────────────────────────┐
│ Method │ Safe        │ Idempotent  │ Notes                     │
├────────┼─────────────┼─────────────┼───────────────────────────┤
│ GET    │ Yes         │ Yes         │ Cacheable                 │
│ HEAD   │ Yes         │ Yes         │ Like GET, no body         │
│ OPTIONS│ Yes         │ Yes         │                           │
│ PUT    │ No          │ Yes         │ Full replace = same result│
│ DELETE │ No          │ Yes         │ Already deleted? no-op    │
│ POST   │ No          │ NO          │ Each call creates new     │
│ PATCH  │ No          │ Maybe       │ Depends on body           │
└────────┴─────────────┴─────────────┴───────────────────────────┘
```

### The POST Problem

POST is **not idempotent by default**. To make it idempotent:

1. **Use PUT** with a client-generated ID (`PUT /orders/{clientUUID}`).
2. **Use the Idempotency-Key header** (industry-standard pattern → next section).

### PATCH Idempotency

```http
PATCH /accounts/42
{ "balance": 100 }     ← idempotent (sets to 100)

PATCH /accounts/42
{ "increment": 10 }    ← NOT idempotent (+10 each call)
```

### A Note on Status Codes

For idempotent retries:
- **First call** → `201 Created` (or `200 OK`).
- **Retry with same key** → `200 OK` returning **the same response body** (not 409).

Returning 409 Conflict on retry breaks the idempotency contract.

---

## 5. The Idempotency Key Pattern ⭐ (~10 min)

This is the **industry standard** used by Stripe, AWS, Square, PayPal, GitHub.

### How It Works

The **client** generates a unique key per logical operation and sends it with the request:

```http
POST /v1/payments HTTP/1.1
Host: api.example.com
Idempotency-Key: 7a3f9c2e-4b1d-4e8a-9f6c-2d1e8b4c5a3f
Content-Type: application/json

{
  "amount": 5000,
  "currency": "USD",
  "card_id": "card_xyz"
}
```

The **server** uses the key to detect duplicates and return the cached response.

### Server Logic

```
   1. Receive request with Idempotency-Key K.
   2. Look up K in the idempotency store.
   3. If K exists AND completed → return stored response.
   4. If K exists AND in-progress → wait or return 409.
   5. If K doesn't exist:
        a. Lock K (with TTL).
        b. Process the request.
        c. Store (K → response) atomically.
        d. Release lock.
        e. Return response.
```

### Pseudocode

```java
public Response handlePayment(PaymentRequest req, String idempotencyKey) {
    // Step 1: Check the idempotency store
    IdempotencyRecord existing = idempotencyStore.find(idempotencyKey);

    if (existing != null) {
        // Validate request payload matches the original
        if (!existing.requestHash.equals(hash(req))) {
            throw new ConflictException("Idempotency-Key reused with different payload");
        }

        switch (existing.status) {
            case COMPLETED:
                return existing.cachedResponse;  // ← idempotent retry success
            case IN_PROGRESS:
                throw new ConflictException("Request still in progress, retry later");
            case FAILED:
                // Allow retry by removing the record (or based on policy)
                idempotencyStore.delete(idempotencyKey);
                break;
        }
    }

    // Step 2: Acquire a lock for this key (atomic insert)
    boolean acquired = idempotencyStore.tryInsert(
        idempotencyKey, IN_PROGRESS, hash(req), Duration.ofMinutes(5)
    );
    if (!acquired) {
        // Another concurrent request is processing → return 409 or wait
        throw new ConflictException("Concurrent request in progress");
    }

    try {
        // Step 3: Actually process the payment
        Response response = paymentProcessor.charge(req);

        // Step 4: Persist the result
        idempotencyStore.update(idempotencyKey, COMPLETED, response, Duration.ofHours(24));
        return response;
    } catch (Exception e) {
        idempotencyStore.update(idempotencyKey, FAILED, null, Duration.ofMinutes(10));
        throw e;
    }
}
```

### Idempotency Key — Best Practices

| Practice                              | Why                                            |
|---------------------------------------|------------------------------------------------|
| Client generates UUID v4              | Globally unique, no coordination needed         |
| Key per logical operation             | Don't reuse across different intent             |
| Validate request payload matches      | Catch buggy clients sending different data      |
| Store request hash for validation     | Compact way to compare                          |
| TTL: 24h-72h typical                  | Cover network retries; clean up old data         |
| Status: PENDING / DONE / FAILED       | Handle in-progress, success, retry separately   |
| Scope key to user/account             | Avoid global collisions                         |
| Return cached response (not new one)  | True idempotency contract                       |

### What the Key Should NOT Be

- Don't derive it from request body alone — legitimate "retry to refresh" calls would deduplicate.
- Don't use timestamps — collisions, non-unique.
- Don't make it sequential — leaks volume info, race conditions.

---

## 6. Storage Design for Idempotency (~10 min)

### Schema (PostgreSQL example)

```sql
CREATE TABLE idempotency_keys (
    key                VARCHAR(64) PRIMARY KEY,
    user_id            VARCHAR(64) NOT NULL,
    endpoint           VARCHAR(100) NOT NULL,
    request_hash       VARCHAR(64) NOT NULL,
    status             VARCHAR(20) NOT NULL,    -- PENDING | DONE | FAILED
    response_code      INT,
    response_body      JSONB,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMP NOT NULL,
    locked_by          VARCHAR(64),             -- worker ID holding lock
    locked_until       TIMESTAMP
);

CREATE INDEX idx_expires ON idempotency_keys(expires_at);
CREATE INDEX idx_user_endpoint ON idempotency_keys(user_id, endpoint);
```

### Atomic Insert (Postgres)

```sql
INSERT INTO idempotency_keys (key, user_id, endpoint, request_hash,
                              status, expires_at)
VALUES ('K123', 'u-42', '/v1/payments', '<hash>', 'PENDING', NOW() + INTERVAL '5 min')
ON CONFLICT (key) DO NOTHING
RETURNING *;
-- If no row returned → key already exists → check status
```

### Storage Options

```
┌──────────────────────┬──────────────────────────────────────────┐
│ Store                │ Pros / Cons                              │
├──────────────────────┼──────────────────────────────────────────┤
│ Relational DB (PG)   │ ACID, atomic insert. Good for low-volume │
│                      │ but can become bottleneck.               │
├──────────────────────┼──────────────────────────────────────────┤
│ Redis                │ Very fast, TTL built-in.                 │
│                      │ Use SET NX EX for atomic insert.         │
│                      │ Need persistence (AOF) for durability.   │
├──────────────────────┼──────────────────────────────────────────┤
│ DynamoDB             │ Scalable, TTL built-in.                  │
│                      │ Use conditional PutItem (attribute_not_exists)│
├──────────────────────┼──────────────────────────────────────────┤
│ Same DB as business  │ ✅ TRANSACTIONAL — write idempotency row │
│ data (recommended!)  │ + business data in SAME transaction.     │
│                      │ No dual-write problem.                   │
└──────────────────────┴──────────────────────────────────────────┘
```

### Redis Pattern

```
SET idem:K123 "PENDING" NX EX 300
   → returns OK on first insert, nil on duplicate
   → NX = only set if not exists
   → EX 300 = TTL 5 minutes (lock)

# After processing
SET idem:K123 '{"status":"DONE","response":...}' EX 86400
   → store result with 24h TTL
```

### Transactional Approach (preferred for critical ops)

```sql
BEGIN;
   INSERT INTO idempotency_keys (key, ...) VALUES (...);
   INSERT INTO payments (id, amount, ...) VALUES (...);
   INSERT INTO outbox (event_type, payload) VALUES ('PaymentCreated', ...);
COMMIT;
```

If the insert into `idempotency_keys` conflicts (duplicate), the whole TX rolls back, and you return the cached response from the existing row.

### Cleanup

Run a background job (or use DB TTL features) to delete expired rows.

```sql
DELETE FROM idempotency_keys WHERE expires_at < NOW();
```

Redis / DynamoDB handle TTL automatically.

---

## 7. Idempotency in Distributed Systems (~10 min)

### 7.1 Achieving Idempotency Without a Key

Sometimes you can't / don't want a client-supplied key. Strategies:

#### a. Natural Unique Key
Use a real-world unique constraint:
```sql
CREATE UNIQUE INDEX ON orders (user_id, item_id, order_date);
```
Second insert fails → catch the violation → return existing record.

#### b. Conditional Updates (Optimistic Concurrency)
```sql
UPDATE accounts SET balance = balance - 100, version = version + 1
WHERE id = 'A' AND version = 5;
```
Only one of N retries succeeds; others see `0 rows affected` and stop.

#### c. State Machine Transitions
```
   if order.status == 'PENDING':
       order.status = 'CONFIRMED'
       save(order)
   else:
       return order   # already confirmed, no-op
```

### 7.2 Idempotency in Message Consumers (Kafka)

Kafka offers **at-least-once delivery** by default. Consumers see duplicates when:
- Consumer crashes after processing but before committing offset.
- Rebalances during message processing.

#### Dedup at Consumer

```java
public void onMessage(Event evt) {
    if (dedup.exists(evt.eventId)) {
        return;  // already processed
    }
    process(evt);
    dedup.add(evt.eventId, TTL_24H);  // store in Redis / DB
}
```

#### "Effectively-once" Recipe
```
At-least-once delivery + Idempotent consumer = Effectively-once processing
```

### 7.3 Idempotent Compensations in Sagas

Compensating transactions **must be idempotent** — Saga orchestrators may retry them on failure.

```java
public void refundPayment(String paymentId) {
    Payment p = paymentRepo.find(paymentId);
    if (p.status == REFUNDED) return;        // ← already done
    stripe.refund(p.chargeId);
    p.status = REFUNDED;
    paymentRepo.save(p);
}
```

### 7.4 The "Exactly-Once" Myth

> True "exactly-once delivery" is impossible in distributed systems (FLP, Two-generals).

What we achieve in practice:
- **At-most-once**: may lose messages (no retry).
- **At-least-once**: may duplicate (with retry).
- **At-least-once + idempotency = effectively-once**.

Kafka offers "exactly-once semantics" within Kafka (producer→broker→consumer in transactions). Cross-system EoS = at-least-once + idempotency.

### 7.5 Concurrency Race — Two Concurrent Requests with Same Key

```
   T1: insert key K (status PENDING) ──┐
   T2: insert key K (status PENDING) ──┘  → ONE succeeds, other gets conflict

   T1: processes payment, marks DONE
   T2 (got conflict): polls or returns 409 "in progress"
        → on next retry: sees DONE → returns cached response ✅
```

This is why the **atomic insert** (ON CONFLICT, SET NX, conditional put) is critical.

### 7.6 Idempotency + Read-Modify-Write

```
   ❌ Bad:
       account = db.fetch(id)              // version=5, balance=100
       account.balance += 50               // 150 in memory
       db.save(account)                    // overwrites concurrent updates

   ✅ Good (optimistic locking):
       UPDATE accounts SET balance = balance + 50, version = version + 1
       WHERE id = ? AND version = 5;
       -- check rows affected
```

---

## 8. Real-World Example: Payment API (~7 min)

### API Design

```http
POST /v1/payments HTTP/1.1
Host: api.example.com
Authorization: Bearer <token>
Idempotency-Key: 7a3f9c2e-4b1d-4e8a-9f6c-2d1e8b4c5a3f
Content-Type: application/json

{
  "amount": 5000,
  "currency": "USD",
  "source": "card_xyz",
  "description": "Order #12345"
}
```

### End-to-End Flow

```
   Client ─── POST /payments + Idempotency-Key ──► API Gateway
                                                     │
                                                     ▼
                                              ┌─────────────────┐
                                              │ Payment Service  │
                                              └────────┬────────┘
                                                       │
                       ┌───────────────────────────────┘
                       │ 1. Check idempotency_keys (Postgres)
                       ▼
              ┌─────────────────────┐
              │ Idempotency table   │
              │ (in same DB)        │
              └─────────┬───────────┘
                       │
              ┌────────┴────────┐
              │                 │
        EXISTS+DONE         NOT EXISTS
              │                 │
              ▼                 ▼
         Return cached    BEGIN TX
         response         INSERT idempotency_keys (PENDING)
              │           INSERT payment (PENDING)
              │           COMMIT
              │                 │
              │                 ▼
              │           Call Stripe API
              │           (with deterministic
              │            stripe_idempotency_key = our key)
              │                 │
              │                 ▼
              │           BEGIN TX
              │           UPDATE payment (DONE)
              │           UPDATE idempotency_keys (DONE, store response)
              │           INSERT outbox (PaymentCompleted event)
              │           COMMIT
              │                 │
              ▼                 ▼
         Response          Response (201)
```

### Critical Points

1. **Use the same Idempotency-Key for downstream calls** (Stripe also supports `Idempotency-Key` header) → end-to-end idempotency.
2. **Single DB transaction** for idempotency row + business write → no dual-write problem.
3. **Outbox pattern** for event publishing → no lost events.
4. **TTL 24h** on idempotency rows → balance between covering retries and storage cost.

### Stripe API as Reference

Stripe's docs explicitly require an `Idempotency-Key` header for all POST requests; if you retry with the same key, you get the **exact same response** as the original attempt — even the status code.

### Mobile App Use Case

```
   User taps "Pay Now"
        │
        ▼
   App generates Idempotency-Key (UUID v4) — stores LOCALLY
        │
        ▼
   POST /payments with key
        │
        ├─ Network timeout? → retry with SAME key
        ├─ 5xx error?       → retry with SAME key
        └─ 2xx response?    → discard key, mark order DONE
```

This way even if the user double-taps "Pay" (UI bug) → second tap reuses the key → dedup'd.

---

## 9. Edge Cases & Failure Scenarios (~3 min)

### 9.1 Key Reuse with Different Payload

Client sends same key but different body (bug / malicious):
- Server detects via `request_hash` mismatch.
- Return **422 Unprocessable** with clear error: "Idempotency-Key has been reused with a different request payload."

### 9.2 Long-Running Request

If processing takes longer than the lock TTL:
- Client retries → sees PENDING → returns 409 "in progress, retry later".
- Or: server extends lock periodically (heartbeat).
- Or: server uses a workflow engine (Temporal) for long-running ops.

### 9.3 Crash Mid-Processing

```
   Step 1: INSERT idempotency_keys (PENDING)  ✅
   Step 2: Call Stripe                         ✅ (card charged!)
   Step 3: UPDATE idempotency_keys (DONE)     ✗ (server crashed)

   Client retries:
   → sees idempotency_keys row in PENDING state
   → Need recovery logic: query Stripe by our Idempotency-Key
       → if Stripe says "charged", finalize our state
       → if Stripe says "no record", re-charge
```

**Resolution:** use the same key end-to-end. Stripe's idempotency layer ensures no double charge.

### 9.4 Idempotency Store Failure

If the idempotency DB is down:
- **Fail closed** for safety-critical ops (reject the request).
- **Fail open** + log for non-critical ops (accept, may risk duplicates).

### 9.5 TTL Too Short

Customer retries a payment 25h later, but we set TTL=24h.
- Idempotency row is gone → we process as a new request → potential duplicate charge.
- **Mitigation:** TTL ≥ longest client retry window. Stripe uses 24h.

### 9.6 Distributed Lock Issue

If you use Redis SETNX as the lock and Redis fails over with replication lag:
- Two requests could both acquire the lock.
- **Use Redlock** or DB-based locking for stronger guarantees.

---

## 10. Wrap-up & Cheat Sheet (~2 min)

### Summary

```
┌────────────────────────────────────────────────────────────────┐
│                IDEMPOTENCY CHEAT SHEET                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│ Definition: f(f(x)) = f(x). Repeated calls = single effect.    │
│                                                                │
│ Why: Networks are unreliable. Clients/queues/sagas RETRY.       │
│      Without idempotency: duplicate charges, inventory loss,   │
│      double emails.                                            │
│                                                                │
│ HTTP method semantics:                                          │
│   GET, HEAD, PUT, DELETE  → idempotent.                        │
│   POST, PATCH              → not by default.                   │
│                                                                │
│ Pattern: Idempotency-Key header (Stripe, AWS, Square).         │
│   - Client generates UUID v4 per logical op.                   │
│   - Server stores (key → response) with TTL 24h.               │
│   - Retry returns cached response (same status, same body).    │
│                                                                │
│ Storage:                                                       │
│   - Same DB as business data → single transaction.             │
│   - Or Redis (SET NX EX) for high throughput.                  │
│   - Atomic insert is critical (ON CONFLICT, NX, conditional). │
│                                                                │
│ Validate: hash(request) matches original → catch buggy clients.│
│                                                                │
│ Alternatives when no key:                                       │
│   - Natural unique constraint.                                 │
│   - Optimistic concurrency (version column).                   │
│   - State-machine transitions (only act on PENDING→DONE).      │
│                                                                │
│ Downstream calls: PASS THE SAME KEY (Stripe accepts            │
│   Idempotency-Key). End-to-end idempotency.                    │
│                                                                │
│ Compensations in Sagas: MUST be idempotent (may retry).       │
│                                                                │
│ Exactly-once delivery is a myth.                                │
│ At-least-once + idempotency = effectively-once. ✅              │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Common Interview Follow-up Questions

**Q: How long should the idempotency window be?**
> At least as long as your maximum client retry window. Stripe uses 24h. Less than 24h risks losing the dedup window for mobile clients in airplane mode; longer is fine but costs storage.

**Q: What if the request body changes between retries?**
> Reject with 422. Store a hash of the original request and compare. This catches bugs where clients mutate the payload before retry.

**Q: How do you implement idempotency for batch operations?**
> Two options:
> - **Single key for whole batch** — all-or-nothing semantics.
> - **One key per item** + a batch-level key — finer granularity, more complex.

**Q: How does this interact with eventual consistency?**
> Idempotency is about uniqueness of effect. If your write goes to replica A, and a retry hits replica B before propagation, you may not see the original. Solutions:
> - Use a single source of truth for idempotency state (e.g., primary node, dedicated dedup service).
> - Use strong reads for the idempotency check (e.g., `consistentRead=true` in DynamoDB).

**Q: How do you handle idempotency for non-deterministic operations (e.g., generating a UUID)?**
> Store the result of non-deterministic operations in the idempotency record so retries return the SAME generated values, not regenerated ones.

**Q: GraphQL — same patterns?**
> Yes. Use a per-mutation idempotency key, often as a field in the input. e.g., `mutation Pay($input: PayInput!) { pay(input: $input) { ... } }` where `input.clientMutationId` serves as the key (Relay convention).

**Q: How do you test idempotency?**
> - Unit test: call the handler twice with same key → assert state changed only once.
> - Integration test: kill the service between processing and committing → restart → retry → assert no duplicate.
> - Chaos test: inject random failures into the network path.

---

## Appendix: Pseudo-code Template

```java
@PostMapping("/v1/payments")
public ResponseEntity<PaymentResponse> createPayment(
        @RequestHeader("Idempotency-Key") String idemKey,
        @RequestBody PaymentRequest req,
        @AuthenticationPrincipal User user) {

    Objects.requireNonNull(idemKey, "Idempotency-Key required");
    if (!isValidUuid(idemKey)) {
        throw new BadRequestException("Idempotency-Key must be UUID");
    }

    String requestHash = sha256(req);

    return idempotencyService.executeIdempotent(
        user.getId(),
        "POST:/v1/payments",
        idemKey,
        requestHash,
        Duration.ofHours(24),
        () -> {
            // Actual business logic — runs only once per key
            Payment payment = paymentService.charge(req, idemKey);
            return ResponseEntity.status(201).body(toResponse(payment));
        }
    );
}
```

```java
@Service
public class IdempotencyService {
    public <T> T executeIdempotent(String userId, String endpoint, String key,
                                   String requestHash, Duration ttl,
                                   Supplier<T> action) {
        // 1. Atomic INSERT ... ON CONFLICT DO NOTHING
        boolean inserted = repo.tryInsert(key, userId, endpoint, requestHash,
                                          Status.PENDING, ttl);
        if (!inserted) {
            IdempotencyRecord rec = repo.findOrFail(key);
            if (!rec.requestHash.equals(requestHash))
                throw new ConflictException("Key reused with different payload");
            if (rec.status == Status.DONE)
                return (T) rec.cachedResponse;
            if (rec.status == Status.PENDING)
                throw new ConflictException("Request in progress");
        }

        // 2. Execute the action
        try {
            T result = action.get();
            repo.update(key, Status.DONE, result);
            return result;
        } catch (RuntimeException e) {
            repo.update(key, Status.FAILED, null);
            throw e;
        }
    }
}
```

---

*This document simulates a complete 1-hour HLD interview on Designing Idempotent APIs — covering theory, the Idempotency Key pattern, storage design, distributed-system concerns, and a real-world Payment API example.*

