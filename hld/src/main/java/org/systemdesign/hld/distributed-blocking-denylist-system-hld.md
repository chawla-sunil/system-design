# Distributed Blocking / Denylist System - HLD Interview

> **Duration:** 50-60 minutes  
> **Goal:** Decide whether a user, account, device, IP, domain, or payment token should be blocked.

---

## 1. Interview Plan

| Time | Cover |
|---|---|
| 0-5 min | Clarifying questions |
| 5-10 min | Requirements and assumptions |
| 10-15 min | Scale estimation |
| 15-20 min | APIs and data model |
| 20-30 min | Architecture |
| 30-42 min | Read and write flows |
| 42-50 min | Consistency, caching, and failures |
| 50-56 min | Multi-region, security, and monitoring |
| 56-60 min | Trade-offs and summary |

> **Candidate opening:** "I will clarify what can be blocked, rule scope, scale, propagation time, and failure behavior. Then I will define the APIs and data model, draw the architecture, and explain lookup, updates, consistency, and failures."

---

## 2. Five Questions to Ask

| Question | Why it matters |
|---|---|
| What entities and match types do we support? | Exact values, IP ranges, and domain suffixes need different indexes |
| Are rules global or scoped by tenant, service, and action? | Scope becomes part of the lookup |
| What are peak checks, writes, rule count, and latency? | Determines storage, caching, and partitioning |
| How quickly must blocks and unblocks propagate? | Determines consistency guarantees |
| During failure, do callers fail open or fail closed? | Determines security versus availability |

### Assumed Interview Answers

- Exact user, account, device, email, payment-token fingerprint, IP, IP CIDR, and domain suffix.
- Global, tenant, service, and action scopes.
- 5M peak checks/sec, 1B active rules, and 10K peak changes/sec.
- Decision latency below 5 ms p99.
- Normal changes propagate within two seconds.
- Emergency blocks must be active in required regions before success.
- Payments and admin access fail closed; low-risk reads may fail open.

---

## 3. Requirements

### Functional

1. Return `ALLOW` or `DENY` for one or more identifiers.
2. Create, update, disable, delete, and inspect rules.
3. Support exact, IP CIDR, and domain suffix matching.
4. Support global, tenant, service, and action scopes.
5. Support expiring rules, bulk imports, and emergency blocks.
6. Return a reason and rule ID to authorized callers.
7. Audit every rule change.

### Non-Functional

| Requirement | Target |
|---|---|
| Peak checks | 5M/sec |
| Decision latency | Under 5 ms p99 |
| Availability | 99.999% |
| Active rules | 1B |
| Normal propagation | 99.99% within 2 seconds |
| Deployment | Multi-region active-active reads |

### Assumptions

- Most requests are allowed; fewer than 0.1% match.
- Any applicable active deny rule results in `DENY`.
- Reads greatly outnumber writes.
- The decision path never calls another region.
- Expiration is checked at decision time.
- Failure policy is configured per action.

### Out of Scope

Fraud detection, ML scoring, DDoS protection, arbitrary regex rules, and user appeal workflows.

---

## 4. Capacity Estimate

Assume three identifiers and four possible scopes per request:

```text
5M requests/sec x 3 identifiers x 4 scopes
= up to 60M candidate lookups/sec
```

The primary database cannot serve every lookup. Regional caches and in-memory filters are required.

At roughly 400 bytes per rule:

```text
1B rules x 400 bytes = 400 GB logical data
```

Plan for 2-3 TB including indexes, replication, tombstones, and headroom.

A Bloom filter for 1B keys at 0.1% false positives needs roughly 1.8 GB and can be partitioned across decision nodes.

---

## 5. API and Data Model

### Decision API

```http
POST /v1/decisions:check

{
  "tenant_id": "tenant-42",
  "service": "payments",
  "action": "transfer",
  "subjects": [
    {"type": "USER_ID", "value": "user-123"},
    {"type": "DEVICE_ID", "value": "device-789"},
    {"type": "IP", "value": "203.0.113.7"}
  ]
}
```

```json
{
  "decision": "DENY",
  "reason_code": "ACCOUNT_COMPROMISED",
  "matched_rule_id": "rule-987",
  "policy_version": 84102931
}
```

Support batching so callers do not make one network call per identifier.

### Rule API

```http
POST   /v1/rules
GET    /v1/rules/{rule_id}
PATCH  /v1/rules/{rule_id}
POST   /v1/rules/{rule_id}:disable
DELETE /v1/rules/{rule_id}
POST   /v1/imports
```

Writes use an idempotency key. Updates use `If-Match` with the current version.

### Rule Record

```text
Rule {
  rule_id
  tenant_id, service, action
  subject_type
  match_type              // EXACT, CIDR, DOMAIN_SUFFIX
  subject_fingerprint
  state                   // ACTIVE, DISABLED, DELETED
  mode                    // ENFORCE, SHADOW
  reason_code
  starts_at, expires_at
  version
  created_by, created_at, updated_at
}
```

Exact lookup key:

```text
tenant | service | action | subject_type | HMAC(normalized_value)
```

Use HMAC rather than a plain hash because low-entropy values such as emails can be guessed.

### Database Choice: DynamoDB

We will use **Amazon DynamoDB** as the authoritative rule database because the workload has:

- One billion rules requiring horizontal partitioning.
- Simple key-based access rather than relational joins.
- Bursty writes up to 10K/sec.
- Conditional writes for versions and idempotency.
- DynamoDB Streams for reliable change capture.
- Multi-AZ durability and regional deployment.

Authoritative table:

```text
DenyRules

PK: RULE#<rule_id>
SK: METADATA

GSI lookup key:
LOOKUP#<scope>#<subject_type>#<HMAC(normalized_value)>
```

The GSI supports administrative lookup by subject. The 5M/sec decision path does **not** query this table directly.

Each region has a materialized **DynamoDB PolicyView table** keyed by the exact lookup key. Decision nodes consult it only after a Bloom-positive result. An optional regional Redis cache sits in front of `PolicyView` for hot records.

```text
PolicyView

PK: SHARD#<hash(lookup_key)>
SK: KEY#<lookup_key>
Value: active rule IDs, reason, expiry, and version
```

Other storage:

| Data | Storage |
|---|---|
| Authoritative rules | DynamoDB `DenyRules` |
| Regional exact decision view | DynamoDB `PolicyView` |
| Hot exact records | Redis and in-process cache |
| Bloom filters and trie snapshots | Object storage |
| Immutable audit history | Object storage with retention lock |

> **Interview answer:** "I chose DynamoDB because this is a massive key-value workload with predictable access patterns. It provides automatic sharding, conditional writes, and Streams. Reads are still protected by Bloom filters and caches; DynamoDB is used only for possible matches, not all five million requests per second."

### Index by Match Type

| Match | Index |
|---|---|
| Exact identifier | Hash key + Bloom filter + exact store |
| IP CIDR | Compressed radix trie |
| Domain suffix | Reversed-label trie |

Normalization must be shared and versioned across writers and readers.

---

## 6. High-Level Architecture

```text
                         CONTROL PLANE

 [Fraud] [Threat Feed] [Admin UI] [Bulk Import]
          \       |       |       /
              [Rule Management API]
                       |
               [DynamoDB DenyRules] -------> [Audit Log]
                       |
                [DynamoDB Streams]
                       |
                   [Event Bus]
                       |
          +------------+------------+
          |                         |
 [Region A Materializer]   [Region B Materializer]
          |                         |
 [DynamoDB PolicyView]     [DynamoDB PolicyView]
 [Redis + Snapshot]        [Redis + Snapshot]


                          DATA PLANE

 [Application/API Gateway]
             |
      [SDK or Sidecar]
             |
   [Regional Decision Service]
      |          |           |
 [Recent Delta] [Bloom] [CIDR/Domain Tries]
                    |
          [Redis / DynamoDB PolicyView]
```

### Component Responsibilities

| Component | Responsibility |
|---|---|
| Rule API | Authorization, validation, idempotency |
| DynamoDB `DenyRules` | Authoritative rule state |
| DynamoDB Streams | Reliably captures committed changes |
| Event bus | Distributes versioned changes |
| Materializer | Builds regional DynamoDB `PolicyView` records and snapshots |
| Redis | Caches hot exact records |
| Decision Service | Low-latency rule evaluation |
| Snapshot | Fast startup and recovery |

Separate the planes because rule management prioritizes correctness and audit, while decisions prioritize latency and availability.

---

## 7. Read Path

1. Authenticate the caller and trust tenant/service scope from its identity.
2. Normalize all identifiers.
3. Generate global, tenant, service, and action lookup keys.
4. Check emergency and recent-change deltas.
5. Check the local exact cache.
6. For misses, check the Bloom filter.
7. If Bloom says "possibly present," verify using the exact store.
8. Check IP and domain tries.
9. Ignore disabled, future, expired, and shadow rules.
10. Return `DENY` if any active rule matches; otherwise `ALLOW`.

### Critical Bloom Filter Rule

```text
Bloom absent   -> skip exact lookup
Bloom present  -> verify exact record
Exact active   -> deny
```

Never deny from Bloom alone because Bloom filters have false positives.

### Why Check Recent Delta First?

A rule added after the current snapshot is missing from its Bloom filter:

```text
Snapshot Bloom: absent
Recent delta:   present
```

Check the delta first so a recently added block is not missed.

---

## 8. Write Path

### Normal Update

1. Authenticate and authorize the producer.
2. Validate and normalize the rule.
3. Conditionally write the versioned rule to DynamoDB.
4. DynamoDB Streams publishes the committed change.
5. Regional materializers consume it idempotently.
6. Update regional exact stores and recent deltas.
7. Decision nodes apply the delta.
8. A later immutable snapshot includes the change.

DynamoDB Streams prevents this failure:

```text
DynamoDB write succeeds -> API process crashes before publishing an event
```

The stream is generated from the committed database change, so propagation does not depend on the API process remaining alive.

### Emergency Block

1. Persist and publish through a high-priority stream.
2. Each required region applies policy epoch `E`.
3. Regions acknowledge `E`.
4. Return success only after required regions acknowledge.
5. Drain or fail closed in a region that cannot reach `E`.

This gives stronger consistency but lower write availability during a partition.

---

## 9. Updates, Unblocks, and Expiration

### Versions and Tombstones

Every mutation carries a monotonic version:

```text
v10 ADD
v11 DELETE
v10 delayed replay -> ignored
```

A delete leaves a versioned tombstone so an old event cannot recreate the block.

### Expiration

Every decision checks:

```text
active =
  starts_at <= now
  and (expires_at is null or now < expires_at)
```

A cleanup job removes old records later but does not define correctness.

### Snapshot Replacement

1. Build a new immutable snapshot.
2. Validate checksum and schema.
3. Load it beside the old snapshot.
4. Atomically switch the active pointer.
5. Keep all deltas newer than the snapshot watermark.

---

## 10. Consistency and Failure Policy

### Three Decision Results

```text
ALLOW         No active match
DENY          Active exact rule matched
INDETERMINATE Decision cannot be safely completed
```

Example caller policy:

```text
payments.transfer:
  on_indeterminate: DENY
  max_policy_age: 2 seconds

content.read:
  on_indeterminate: ALLOW_WITH_TELEMETRY
  max_policy_age: 10 minutes
```

Do not silently return `ALLOW` when exact verification fails.

### CAP Trade-off

During a network partition, an isolated region cannot remain fully available, receive every new rule, and guarantee no stale allows.

- High-risk actions fail closed or leave the stale region.
- Low-risk actions may fail open using the last known policy.
- Emergency rules choose consistency over availability.

---

## 11. Scaling, Multi-Region, and Failures

Partition exact records using:

```text
hash(tenant_id + subject_type + subject_fingerprint) mod N
```

Use virtual shards for rebalancing.

### Multi-Region

- Active-active regional decision services.
- All reads stay in-region.
- Rule changes replicate through the event bus.
- Signed snapshots are copied across regions.
- Keep spare capacity to survive an availability-zone loss.

### Failure Handling

| Failure | Behavior |
|---|---|
| Decision node fails | Retry another node |
| Regional cache fails | Use L1/Bloom and limited exact-store fallback |
| Exact store fails | Return `INDETERMINATE` for unresolved keys |
| Event stream lags | Track policy age; stop stale high-risk decisions |
| Duplicate or old event | Ignore using version |
| Corrupt snapshot | Keep previous verified snapshot |
| Control plane fails | Existing decision data continues working |
| Region partitions | Apply action-specific fail policy |

A node becomes ready only after loading a valid snapshot, replaying deltas, and reaching an acceptable policy version.

Continuously compare source and regional checksums. Rebuild a shard from a snapshot if reconciliation finds divergence.

---

## 12. Security and Monitoring

### Security

- Use workload identity and mTLS.
- Authorize by tenant, source, subject type, and scope.
- Require MFA and stronger approval for global/emergency rules.
- HMAC identifiers and never store raw payment card numbers.
- Redact identifiers from logs and traces.
- Sign snapshots and bulk imports.
- Rate-limit producers and limit their blast radius.
- Roll out new automated sources in `SHADOW` mode.
- Keep immutable rule-change audit records.

### Important Metrics

```text
decision_latency_p99
decision_result{ALLOW,DENY,INDETERMINATE}
policy_age_seconds{region,shard}
rule_propagation_seconds
emergency_activation_seconds
event_consumer_lag_seconds
bloom_false_positive_rate
cache_hit_rate
exact_store_latency
reconciliation_mismatches
```

Page on excessive policy age, emergency activation failure, high `INDETERMINATE` rate, reconciliation mismatch, or snapshot verification failure.

Log all denies and sensitive actions, but sample ordinary allows.

---

## 13. Key Trade-offs

| Choice | Decision |
|---|---|
| Query primary DB on every request | No; it cannot handle 60M candidate lookups/sec |
| Redis as the complete system | No; it does not solve audit, replay, or multi-region correctness |
| Bloom-only decisions | No; false positives would block valid users |
| Regional decision data | Yes; required for latency and availability |
| Eventual consistency | Use for normal rules |
| Activation barrier | Use for emergency rules |
| Fail open everywhere | No; security bypass |
| Fail closed everywhere | No; unnecessary outages |

---

## 14. Common Follow-Up Questions

**Can Bloom accidentally block a user?**  
No. A positive result always requires exact verification.

**What if add and delete events arrive out of order?**  
Ignore older versions and retain delete tombstones.

**What if a cached rule expires?**  
Check `expires_at` during every decision.

**How do IP ranges work?**  
Use separate IPv4 and IPv6 compressed radix tries.

**How do domain suffixes work?**  
Reverse normalized labels, such as `sub.example.com` to `com.example.sub`, and use a trie.

**What if the event stream fails?**  
Writes remain durable in the outbox. Existing decisions continue, but stale high-risk actions fail closed.

**Why use both events and snapshots?**  
Events provide fast changes; snapshots provide quick startup and recovery.

**How is an emergency block guaranteed globally?**  
Wait for required regions to acknowledge its policy epoch. Stop protected traffic in a region that cannot acknowledge.

---

## 15. Final Interview Summary

> "I would separate the system into a control plane and a regional decision plane.
>
> The control plane stores idempotent rule changes in DynamoDB `DenyRules`. DynamoDB Streams publishes versioned changes, and regional materializers create DynamoDB `PolicyView` records, Bloom filters, IP and domain tries, and recent-change deltas. Redis optionally caches hot exact records.
>
> On the read path, the Decision Service checks recent changes first, uses Bloom to eliminate definite misses, and verifies possible matches in the exact store. Bloom never directly denies. Versions and tombstones handle reordered updates, and every decision checks expiration.
>
> Normal rules have a two-second propagation SLO. Emergency rules return success only after required regions activate their policy epoch. If a safe answer is impossible, the service returns `INDETERMINATE`; high-risk actions fail closed while low-risk actions may fail open.
>
> The read path stays regional for low latency. I would monitor decision latency, policy age, propagation lag, indeterminate decisions, and reconciliation mismatches."

### Five Things to Remember

1. Separate **control plane** and **decision plane**.
2. Use **Bloom + exact verification**, never Bloom alone.
3. Use **snapshot + recent delta** for fresh updates.
4. Use **versions + tombstones** for ordering and unblocks.
5. Make **fail-open/fail-closed action-specific**.
