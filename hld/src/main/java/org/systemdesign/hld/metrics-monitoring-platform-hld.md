# Metrics Monitoring Platform - High-Level Design Interview
> **Duration:** 40-50 minutes  
> **Candidate:** Software engineer with about 5 years of experience  
> **Prompt:** Design a platform like Datadog Metrics, Prometheus/Grafana, or CloudWatch.
---

## Table of Contents
1. [What the interviewer tests](#1-what-the-interviewer-tests)
2. [Interview approach](#2-interview-approach)
3. [Clarifying questions and assumptions](#3-clarifying-questions-and-assumptions)
4. [Requirements and guarantees](#4-requirements-and-guarantees)
5. [Capacity estimation](#5-capacity-estimation)
6. [Metric model](#6-metric-model)
7. [API design](#7-api-design)
8. [Database choices](#8-database-choices)
9. [High-level architecture](#9-high-level-architecture)
10. [End-to-end ingestion flow](#10-end-to-end-ingestion-flow)
11. [Query and dashboard flow](#11-query-and-dashboard-flow)
12. [Alerting flow](#12-alerting-flow)
13. [Time-series storage deep dive](#13-time-series-storage-deep-dive)
14. [Partitioning and cardinality](#14-partitioning-and-cardinality)
15. [Aggregation and retention](#15-aggregation-and-retention)
16. [Reliability and failure handling](#16-reliability-and-failure-handling)
17. [Multi-region design](#17-multi-region-design)
18. [Security and multi-tenancy](#18-security-and-multi-tenancy)
19. [Monitoring the monitoring platform](#19-monitoring-the-monitoring-platform)
20. [Trade-offs](#20-trade-offs)
21. [Interviewer follow-ups](#21-interviewer-follow-ups)
22. [Final answer and cheat sheet](#22-final-answer-and-cheat-sheet)
---

## 1. What the Interviewer Tests
A metrics platform is a high-write, time-ordered, multi-tenant analytical system. The interviewer wants more than "store points and draw graphs."
A strong answer should identify:
- Continuous ingestion from millions of metric series; Time-range queries over recent and historical data; Labels that enable filtering but can cause cardinality explosion; Different access patterns for ingestion, metadata, dashboards, and alerts; Low-cost retention through compression, compaction, and downsampling; Timely, reliable, deduplicated alert delivery; Isolation between tenants and between interactive and alert queries; Survival during the same production incidents it is meant to observe.
The candidate should:
- Clarify scale and semantics before naming technologies; Separate the write path, read path, control plane, and alerting plane; Estimate samples/sec, bandwidth, storage, and query fan-out; Choose a time-series database and explain why; Walk ingestion, query, and alert flows end to end; Discuss overload, replay, late data, and regional failure; State consistency and delivery guarantees explicitly.
> **Opening:** "I will clarify collection, scale, retention, and alerting. Then I will estimate capacity, define APIs and data, draw the architecture, and deep-dive into storage, cardinality, queries, alerts, and failures."
---

## 2. Interview Approach

### 2.1 Minute-by-Minute Plan
| Time | Discussion | Output |
|---|---|---|
| 0-5 min | Ask four questions | Collection, scale, retention, alert target |
| 5-9 min | Fix scope | Functional requirements and guarantees |
| 9-13 min | Estimate load | Write rate, storage, bandwidth, query load |
| 13-17 min | Define model and APIs | Series, sample, query, rule, dashboard |
| 17-24 min | Draw architecture | Agent, gateway, Kafka, TSDB, query, alerts |
| 24-31 min | Deep-dive write path | Validation, batching, WAL, blocks |
| 31-37 min | Deep-dive read path | Index, fan-out, cache, aggregation |
| 37-43 min | Deep-dive alerting | Scheduling, state, dedupe, routing |
| 43-47 min | Scale and failures | Cardinality, backpressure, replay, DR |
| 47-50 min | Close | Trade-offs, bottleneck, five-minute summary |

### 2.2 Whiteboard Order
1. Write functional and non-functional requirements.
2. State scale and retention assumptions.
3. Define a metric series and sample.
4. Draw collection and ingestion.
5. Add Kafka and time-series storage.
6. Add query, dashboards, and cache.
7. Add rule evaluation and notifications.
8. Finish with failure handling and multi-region.
Narrate decisions instead of silently drawing boxes.
Useful statements:
> "The write key is tenant plus series ID plus a time bucket."
> "A successful write means durable queue replication, not immediate query visibility."
> "I index labels but compress metric values in time-ordered chunks."
> "Cardinality, rather than numeric value size, is the main capacity risk."
---

## 3. Clarifying Questions and Assumptions

### 3.1 Simulated Interview: Ask Only 3-4 High-Value Questions
> **Interviewer:** Design a metrics monitoring platform.
> **Candidate:** "Should collection be pull-based, push-based, or both?"
> **Interviewer:** Support both. Agents scrape local targets and push compressed batches. Also support Prometheus remote write.
> **Candidate:** "What are the host count, active series, sampling interval, and peak samples per second?"
> **Interviewer:** 100,000 hosts, 10 million active series, a 10-second interval, and 2 million samples/sec at peak.
> **Candidate:** "How long should raw and aggregated data be retained?"
> **Interviewer:** Raw for 15 days, one-minute aggregates for 13 months, and hourly aggregates for three years.
> **Candidate:** "What dashboard and alerting SLOs should I target?"
> **Interviewer:** Common dashboards in two seconds, rules every 30 seconds, and notification within one minute.
These questions determine protocol, capacity, storage hierarchy, query design, and alert scheduling.

### 3.2 Assumptions to State
- The system is a managed multi-tenant service; It stores counters, gauges, and histograms; Every sample has a metric name, labels, timestamp, and value; Agents batch and compress before upload; Most samples arrive within one minute; Normal ingestion accepts samples up to two hours late; Transport is at-least-once, so duplicates are possible; Query visibility may lag accepted ingestion by up to 10 seconds at p99; Recent dashboard queries dominate read traffic; Alert evaluation intervals are 30 seconds or longer; Ingestion availability matters more than immediate read consistency; The initial deployment is multi-AZ with cross-region disaster recovery.

### 3.3 Key Definitions
| Term | Meaning |
|---|---|
| Metric | Measured quantity such as `cpu_usage_percent` |
| Label | Dimension such as `service=checkout` |
| Series | Metric name plus one exact label set |
| Sample | Timestamp and numeric value in one series |
| Cardinality | Number of unique active series |
| Scrape | Pull current metrics from an exporter |
| Remote write | Push sample batches to central storage |
| Recording rule | Periodic query stored as a new metric |
| Alert rule | Query plus duration and severity |
| Downsampling | Replace raw points with window aggregates |
---

## 4. Requirements and Guarantees

### 4.1 Functional Requirements
| ID | Requirement |
|---|---|
| FR-1 | Collect metrics through agents, scrapes, and remote write |
| FR-2 | Support counters, gauges, and histograms |
| FR-3 | Store timestamped samples and labels |
| FR-4 | Query by metric, label filters, range, and aggregation |
| FR-5 | Create dashboards with panels and variables |
| FR-6 | Create recording and alert rules |
| FR-7 | Route alerts to email, Slack, PagerDuty, and webhooks |
| FR-8 | Group, silence, acknowledge, and deduplicate alerts |
| FR-9 | Expose target health, quotas, retention, and usage |

### 4.2 Non-Functional Requirements
| ID | Target |
|---|---|
| NFR-1 | Sustain 2 million samples/sec peak |
| NFR-2 | 99.99% ingestion availability |
| NFR-3 | p99 accepted-to-queryable delay under 10 seconds |
| NFR-4 | p95 common dashboard query under 2 seconds |
| NFR-5 | Notify within 60 seconds of an alert condition |
| NFR-6 | No acknowledged loss after one node or AZ failure |
| NFR-7 | Horizontal scaling and tenant isolation |
| NFR-8 | Regional RPO under 5 minutes; RTO under 30 minutes |

### 4.3 Guarantees
- `202 Accepted` means the batch is replicated in the durable log; Samples are eventually consistent and normally visible in 10 seconds; Metadata and rule definitions are strongly consistent; Transport is at-least-once; Same-series ordering is preserved within a partition; Duplicate series/timestamp samples are handled idempotently; Out-of-order samples inside the allowed window are eventually merged; A rare duplicate notification is preferred to missing a critical alert.

### 4.4 Out of Scope
- Full-text log search and distributed trace storage; Browser monitoring and synthetic testing; Automated incident remediation; Machine-learning anomaly detection in version one; Arbitrary joins over unlimited series; Millisecond hard real-time alerts.
---

## 5. Capacity Estimation

### 5.1 Write Rate
- Active series: 10 million; Sample interval: 10 seconds; Average: `10M / 10 = 1M samples/sec`; Peak: 2M samples/sec; Daily samples: `1M * 86,400 = 86.4B`.
Assume one logical sample has:
- 8-byte timestamp; 8-byte floating-point value; 8-byte series reference and batch overhead.
At about 24 bytes/sample:
`2M * 24 bytes = 48 MB/sec` before transport and replication overhead.
Labels are registered per series and are not repeated for every stored sample. Agents use protobuf batches and Snappy or Zstandard compression.

### 5.2 Storage
Assume TSDB compression requires 2 bytes/sample for values and timestamps:
`86.4B * 2 bytes = 172.8 GB/day`.
Apply roughly 3x for indexes, metadata, replication, and safety:
`172.8 GB * 3 = approximately 520 GB/day`.
Raw 15-day storage:
`520 GB * 15 = approximately 7.8 TB`.
This is an order-of-magnitude estimate. Label churn and index size may dominate in practice.

### 5.3 Downsampling
- Ten-second raw points become one-minute aggregates: about 6x fewer windows; One-minute points become hourly aggregates: another 60x fewer windows; Gauges store min, max, sum, count, and last; Counters preserve reset-aware increase; Histograms merge compatible buckets; Old immutable blocks live in inexpensive object storage.

### 5.4 Read Load
Assume:
- 2,000 peak interactive queries/sec; 500,000 alert evaluations every 30 seconds; A 24-hour chart has 8,640 raw points per series; A 1,000-series chart could scan 8.64 million points.
A panel is only about 1,000 pixels wide. The planner should select a coarser resolution and cap returned points.

### 5.5 Kafka Sizing
Use enough partitions to:
- Spread tenant-series keys; Parallelize consumers; Survive broker loss without overload; Keep each partition under benchmarked throughput.
Propose 200-500 partitions initially, then load test. Do not claim an exact number without payload and hardware measurements.
---

## 6. Metric Model

### 6.1 Types
**Gauge:** Can increase or decrease; aggregate with last, min, max, or average.
**Counter:** Usually increases and resets on restart; query with `rate()` or `increase()`.
**Histogram:** Cumulative buckets, count, and sum; supports aggregatable quantiles.
**Summary:** Client-side quantiles; generally cannot be combined correctly across instances.
Prefer histograms for fleet-wide latency percentiles.

### 6.2 Canonical Sample
```json
{
  "tenantId": "tenant-42",
  "metric": "http_requests_total",
  "labels": {
    "service": "checkout",
    "region": "ap-south-1",
    "status": "200",
    "instance": "10.0.2.15:8080"
  },
  "timestampMs": 1789411200000,
  "value": 183729.0
}
```
Canonicalize labels by sorting keys and validating names.
`series_id = hash(tenant_id, metric_name, canonical_labels)`.
Compare canonical labels to detect rare hash collisions. Never trust a tenant ID supplied inside the payload.

### 6.3 Metadata Entities
| Entity | Important fields |
|---|---|
| Tenant | ID, plan, quotas, retention, home region |
| Series | tenant, series ID, metric, labels, first/last seen |
| Dashboard | ID, owner, panels, variables, version |
| Alert rule | query, interval, duration, labels, annotations |
| Recording rule | query, output metric, output labels |
| Silence | matchers, start, end, creator, reason |
| Receiver | route matchers, channel, grouping, repeat interval |
---

## 7. API Design

### 7.1 Ingest
```http
POST /v1/metrics:write
Authorization: Bearer <token>
Content-Type: application/x-protobuf
Content-Encoding: snappy
Idempotency-Key: 7e5d...
```
Response after durable Kafka replication:
```json
{
  "status": "accepted",
  "batchId": "batch-91ad",
  "acceptedSamples": 4200,
  "rejectedSamples": 3
}
```
Limit compressed bytes, expanded bytes, series count, sample count, and timestamp range. Return rejection counts by reason instead of silently dropping invalid samples.

### 7.2 Range Query
```http
GET /v1/query_range
  ?query=sum(rate(http_requests_total{service="checkout"}[5m])) by (region)
  &start=1789407600
  &end=1789411200
  &step=30
```
Support an instant query:
```http
GET /v1/query?query=up{service="checkout"}&time=1789411200
```

### 7.3 Discovery
```http
GET /v1/series?match[]=http_requests_total{service="checkout"}
GET /v1/labels
GET /v1/label/region/values?metric=http_requests_total
```
Discovery uses the label index and always has response limits.

### 7.4 Alert Rule
```http
POST /v1/alert-rules
Content-Type: application/json
```
```json
{
  "name": "checkout-high-error-rate",
  "query": "service:http_error_ratio_5m{service=\"checkout\"} > 0.05",
  "evaluationInterval": "30s",
  "for": "5m",
  "labels": {"severity": "critical", "team": "payments"},
  "annotations": {"summary": "Checkout errors exceed 5%"}
}
```

### 7.5 Control APIs
```http
POST   /v1/dashboards
GET    /v1/dashboards/{id}
PUT    /v1/dashboards/{id}
DELETE /v1/dashboards/{id}
POST   /v1/silences
DELETE /v1/silences/{id}
```
Use ETags or versions to prevent lost metadata updates. Audit every rule, silence, credential, and permission change.
---

## 8. Database Choices

### 8.1 Primary Store: Distributed TSDB
Use a Prometheus-compatible distributed TSDB, such as a Mimir/Cortex/Thanos-style design.
Why:
- Append-heavy time-series writes; Efficient compression of ordered timestamps and values; Time-range scans are the main read pattern; Inverted label indexes support filtering; PromQL-like functions support rates and histograms; Immutable object blocks make long retention economical; Ingestion and query tiers scale independently.
Possible alternatives are VictoriaMetrics or ClickHouse with a time-series schema.

### 8.2 Why Not One General Database?
**PostgreSQL only:** Great for metadata, but row/index/WAL cost is high for trillions of samples.
**Cassandra only:** Excellent writes, but label discovery and ad hoc aggregation require custom indexes and query logic.
**Elasticsearch only:** Flexible search, but per-document indexing is too expensive for every metric point.
Use the correct store per access pattern:
| Data | Store | Reason |
|---|---|---|
| Metric samples | Distributed TSDB | Compression, label index, range query |
| Durable ingestion | Kafka | Buffering, ordering, replay |
| Long-term blocks | Object storage | Cheap, durable, elastic |
| Dashboards/rules/tenants | PostgreSQL | Transactions and constraints |
| Query results | Redis | Low-latency TTL cache |
| Audit | Append-only log/object store | Long retention and tamper evidence |
> **Interview answer:** "PostgreSQL owns strong control-plane state; Kafka buffers writes; the TSDB owns recent series and indexes; immutable blocks move to object storage; Redis caches tenant-scoped query results."
---

## 9. High-Level Architecture
```text
 Applications / Exporters / Hosts
              |
       Monitoring Agents
    scrape + disk buffer + batch
              |
              v
   Load Balancer / API Gateway
              |
              v
      Ingestion Gateways
 auth | validate | quota | series ID
              |
              v
       Kafka Durable Log --------> Usage Metering
              |
              v
      Stream Processors
  dedupe | normalize | shard route
              |
              v
       TSDB Ingesters
    memory head + local WAL
              |
       compact immutable blocks
              |
              v
        Object Storage
              ^
              |
 UI/Grafana -> Query Frontend -> Query Scheduler -> Query Workers
                    |                                |
                    v                                v
                  Redis                   TSDB + Object Blocks

 PostgreSQL <-> Control API <-> Dashboards / Rules / Tenants
                              |
                              v
                       Rule Evaluators
                              |
                              v
                 Alert Manager -> Notification Queue
                              |
                  Slack / Email / PagerDuty / Webhook
```

### 9.1 Responsibilities
**Agent:** Discovers targets, scrapes, adds trusted labels, batches, compresses, buffers, and retries.
**API gateway:** Terminates TLS, authenticates, rate-limits, and routes to a tenant home region.
**Ingestion gateway:** Validates samples, enforces quota, computes series IDs, and publishes to Kafka.
**Kafka:** Absorbs bursts, decouples services, and enables replay.
**Stream processor:** Deduplicates, meters usage, watches cardinality, and routes shards.
**TSDB ingester:** Appends to WAL/head chunks, replicates, compresses, and uploads blocks.
**Query frontend:** Parses, costs, splits, queues, selects resolution, and checks cache.
**Query worker:** Reads indexes/chunks and computes partial aggregations.
**Rule evaluator:** Schedules recording/alert queries and persists runtime state.
**Alert manager:** Groups, silences, inhibits, deduplicates, routes, and retries notifications.

### 9.2 Separation of Planes
- Data write plane: agent through TSDB; Data read plane: query frontend through storage; Control plane: tenants, dashboards, rules, quotas, credentials; Alert plane: evaluation, state, grouping, and notification.
One slow plane should not consume all resources of another. Reserve query capacity for alert evaluation.
---

## 10. End-to-End Ingestion Flow

### 10.1 Agent to Kafka
1. Agent discovers configured targets.
2. It scrapes each target on a jittered 10-second interval.
3. Jitter avoids a fleet-wide traffic spike.
4. It adds controlled service, region, and instance labels.
5. It writes a compressed batch to a bounded local disk queue.
6. Gateway authenticates and maps credentials to a tenant.
7. Gateway checks compressed and expanded request sizes.
8. Ingestion validates names, labels, timestamp skew, and numeric values.
9. It enforces samples/sec, active-series, and new-series quotas.
10. It canonicalizes labels and computes the series ID.
11. It publishes to replicated Kafka.
12. It returns `202 Accepted`.
Kafka key:
`hash(tenant_id, series_id)`.
This preserves ordering for one series while spreading a large tenant across partitions. Hashing only by tenant would create a hot partition.

### 10.2 Kafka to TSDB
1. Consumer reads and decompresses a batch with safety limits.
2. It checks schema version and bounded duplicate state.
3. It records usage and cardinality.
4. Consistent hashing selects a TSDB shard.
5. Ingester appends samples to its local WAL.
6. Replicas acknowledge according to quorum policy.
7. Samples enter compressed in-memory head chunks.
8. Consumer commits Kafka offset after durable TSDB acceptance.
9. Sealed chunks form immutable blocks.
10. Blocks and indexes upload to object storage.

### 10.3 Delivery and Conflicts
- End-to-end transport is at-least-once; Agent retries use a stable batch ID; Logical identity is series ID plus timestamp; Replayed identical samples are idempotent; Conflicting values use a documented deterministic policy; Prefer rejecting conflicts or last-accepted-write plus a conflict metric; Exactly-once across agent, Kafka, and storage is unnecessary complexity.

### 10.4 Backpressure
When writers slow:
- Kafka lag grows first; Autoscaling adds consumers and ingesters; Tenant admission control protects shared capacity; Gateway returns `429` or `503` with `Retry-After`; Agents retry with exponential backoff and jitter; Local queues absorb short outages; Queue overflow drops only by explicit priority policy; Every drop increments visible health and audit metrics.
---

## 11. Query and Dashboard Flow
1. User opens a dashboard.
2. Dashboard definition is read from PostgreSQL.
3. Browser requests each visible panel.
4. Query frontend authenticates and injects tenant scope.
5. It parses the expression into an AST.
6. It estimates matched series and scanned samples.
7. It rejects or queues requests over limits.
8. It selects raw, minute, or hourly resolution.
9. It splits long time ranges into parallel subqueries.
10. It checks a tenant-scoped cache.
11. Workers find relevant shards and time blocks.
12. Workers intersect label posting lists.
13. They read only overlapping compressed chunks.
14. Partial aggregates are merged.
15. Frontend returns bounded points to the UI.
For `metric{service="checkout",region=~"ap-.*"}`:
- Find the metric-name posting list; Intersect it with `service=checkout`; Expand the bounded region matcher; Resolve matching series IDs; Read chunks overlapping the requested time.
Performance controls:
- Choose step from range and panel width; Cap series, points, scanned samples, and response size; Bound broad regex matchers; Cancel abandoned queries; Prioritize interactive queries over bulk export; Reserve a worker pool for alert evaluation; Use recording rules for repeated expensive expressions.
Cache keys include tenant, query, range, step, and resolution. Cache immutable old ranges longer than the still-changing recent window.
---

## 12. Alerting Flow
1. User creates a versioned rule in PostgreSQL.
2. Rule distributor assigns its group to an evaluator shard.
3. A lease with fencing token gives one evaluator ownership.
4. Evaluator runs the query at deterministic timestamps.
5. Each result label set becomes one alert instance.
6. State moves through inactive, pending, firing, and resolved.
7. Runtime state is checkpointed.
8. Events are sent to replicated alert managers.
9. Alert manager applies grouping, silences, and inhibition.
10. A durable queue decouples external notification delivery.
```text
INACTIVE
   | condition true
   v
PENDING ---- false ----> INACTIVE
   | true for configured duration
   v
FIRING ----- false ----> RESOLVED ----> INACTIVE
```
The `for` duration avoids alerts from one noisy evaluation.
Notification behavior:
- Fingerprint = rule ID plus result labels; Stable fingerprints deduplicate evaluator failover; Group related alerts by service, cluster, or team; Repeat interval prevents notification every evaluation; Receiver idempotency keys are used when supported; Transient failures retry with bounded backoff; Persistent failures go to a dead-letter view; External channels cannot guarantee global exactly once.
Missing data is explicit:
- Treat as alerting; Treat as healthy; Keep the last state.
Expose `up` and last-scrape metrics so users can write absence alerts.
---

## 13. Time-Series Storage Deep Dive

### 13.1 Head and WAL
The active head contains:
- Series ID to active-series map; Ordered compressed sample chunks; Metric and label inverted indexes; Write-ahead log for crash recovery.
Samples close in series and time stay together. This improves compression and range-read locality.

### 13.2 Compression
Timestamps normally have a stable interval. Use delta-of-delta encoding:
- Store first timestamp and first delta; Encode small changes to the delta compactly.
Floating-point values often share bits. Use XOR encoding:
- XOR current and previous value; Encode only changed bit ranges.

### 13.3 Immutable Blocks
Periodically:
1. Seal a two-hour head range.
2. Merge bounded late samples by timestamp.
3. Resolve duplicates deterministically.
4. Build compressed chunks and posting lists.
5. Write min/max time, checksums, and source IDs.
6. Upload data and index to object storage.
7. Publish the complete manifest atomically.
Readers ignore blocks without a complete manifest. Checksums detect corruption.

### 13.4 Late and Backfill Data
- Accept normal writes up to two hours late; Keep a mutable recent window or side buffer; Merge late samples during compaction; Reject older samples from the real-time endpoint; Use a controlled backfill API to create isolated blocks; Reconcile overlapping backfill blocks before publishing.

### 13.5 Compaction
Compactors merge adjacent blocks, deduplicate, downsample, and rebuild indexes. Jobs are idempotent and use leases. Source blocks are deleted only after replacement durability. A reconciliation job finds orphan or overlapping blocks.
---

## 14. Partitioning and Cardinality

### 14.1 Partitioning
| Layer | Partition key |
|---|---|
| Kafka | hash of tenant and series ID |
| TSDB | consistent hash of tenant and series ID |
| Object blocks | tenant shard and time range |
| Rules | tenant and rule-group ID |
| Cache | tenant, query, range, step, resolution |
Virtual nodes balance TSDB membership changes. Replicas span availability zones.

### 14.2 Why Cardinality Is Dangerous
Suppose labels have:
- 100 routes; 5 status codes; 1,000 instances.
This can create `100 * 5 * 1,000 = 500,000` series. Adding `user_id` with millions of values can make the space unbounded.
Each series consumes:
- Metadata and index entries; Active-head memory; Chunk and object overhead; Compaction work; Query fan-out.

### 14.3 Controls
At ingestion:
- Limit labels per series and label lengths; Limit active series per tenant; Limit new series per minute; Reject forbidden or unbounded labels; Drop configured labels before series identity; Report top cardinality contributors.
At instrumentation:
- Use route templates, not raw URLs; Put request/user IDs in logs or traces; Avoid timestamps and random values as labels; Aggregate locally where appropriate.
At query:
- Estimate matcher fan-out; Bound regex expansion; Cap scanned series and samples; Apply weighted fair queues per tenant.
A very large tenant may receive dedicated partitions or a dedicated storage cell.
---

## 15. Aggregation and Retention
| Tier | Resolution | Retention | Use |
|---|---|---|---|
| Hot | Raw 10 seconds | 15 days | Incident debugging |
| Warm | 1 minute | 13 months | Trends and capacity |
| Cold | 1 hour | 3 years | Long-term reports |
Gauge windows store min, max, sum, count, and last. Counter windows store first, last, reset count, and corrected increase. Histogram windows merge compatible buckets, total count, and sum. Never average separately calculated p99 values.
Retention deletion:
1. Mark expired blocks.
2. Hide them from new query plans.
3. Wait through a safety period.
4. Delete object data.
5. Keep an audit record.
Recording rules materialize expensive common expressions. They reduce repeated query cost but add series and evaluation delay.
---

## 16. Reliability and Failure Handling

### 16.1 Agent or Network Failure
- Agent stores a bounded disk queue; Restart resumes unsent batches; Exponential backoff prevents retry storms; Missed scrapes while the host is down cannot be reconstructed; Expose queue age, dropped samples, and last successful upload.

### 16.2 Gateway Failure
- Gateways are stateless across three availability zones; Load balancer selects another healthy gateway; Client retries use the same idempotency key; No success is returned before durable queue acknowledgment.

### 16.3 Kafka Failure
- Replication factor three with quorum acknowledgments; Replicas are spread across zones; If quorum is unavailable, writes are not acknowledged; Agents buffer and retry rather than receiving false success; Monitor lag relative to log-retention time.

### 16.4 Consumer or Ingester Failure
- Kafka reassigns the consumer partition; Replacement resumes at the last committed offset; Replayed samples are idempotent; Ingester reconstructs the head from WAL; Recent data is replicated to other ingesters; Kafka provides a second recovery source while retained.

### 16.5 Object Storage Failure
- Recent queries continue from head blocks; Historical queries fail clearly or return marked partial data; Compactors stop publishing and retain source blocks; Never report missing data as zero.

### 16.6 Query Failure
- Scheduler retries an idempotent subquery on another worker; Queries have deadlines and cancellation; Retries are bounded; Partial responses are explicit, never silently successful.

### 16.7 Evaluator Failure
- Lease expires and another evaluator obtains a newer fencing token; It restores the last checkpoint; Deterministic timestamps prevent evaluation drift; Stable fingerprints suppress duplicate notifications; A bounded number of missed intervals may be replayed.

### 16.8 Metadata Failure
- PostgreSQL has synchronous multi-AZ replication; Mutations require the primary; Cached dashboards may remain readable; Cached rules continue briefly during a control-plane outage.

### 16.9 Data Corruption and Clock Skew
- Verify block checksums on write and read; Quarantine corrupt blocks and rebuild from replicas; Use NTP on agents and servers; Reject samples too far in the future; Evaluate alerts using server-side fixed timestamps; Track clock skew as a platform metric.
---

## 17. Multi-Region Design

### 17.1 Home-Region Model
Assign each tenant or workspace a home write region.
Advantages:
- One ordering and cardinality authority; Simpler alert ownership; No active-active write conflicts; Predictable data residency.
Agents use a global endpoint that routes to the home region.

### 17.2 Replication
- Replicate immutable blocks asynchronously to DR object storage; Replicate PostgreSQL metadata cross-region; Mirror Kafka to protect recent unblocked data; Replicate rule, silence, and notification state; Measure replication lag against RPO.

### 17.3 Failover
1. Confirm sustained home-region failure.
2. Fence the old region using a write epoch.
3. Promote metadata in the DR region.
4. Route tenant traffic to DR.
5. Start writes under the new epoch.
6. Restore evaluator ownership.
7. Replay mirrored Kafka and agent queues.
8. Reconcile blocks when the old region returns.
Fencing prevents split-brain writes. Kafka mirroring plus agent buffers targets RPO under five minutes.
---

## 18. Security and Multi-Tenancy

### 18.1 Identity and Authorization
- Agents use workload identity or short-lived service credentials; Users authenticate through OIDC/SAML; Stored API keys are hashed and rotatable; Scopes separate read, write, rules, and administration; Roles include viewer, editor, alert manager, and administrator; Authenticated identity determines tenant; payload tenant IDs are ignored.

### 18.2 Isolation
- Prefix every physical key with tenant identity; Include tenant in every cache and series key; Enforce tenant filters inside storage adapters; Encrypt traffic with TLS; Encrypt databases, Kafka, disks, and object storage; Use per-tenant envelope keys where required; Enforce quotas before expensive parsing and query fan-out.

### 18.3 Abuse Protection
- Limit compressed and expanded request sizes; Detect decompression bombs; Bound metric/label names and label count; Reject extreme future or old timestamps; Rate-limit authentication failures; Limit regex complexity and query cost; Detect cardinality attacks through new-series velocity.

### 18.4 Audit
Audit rule, dashboard, silence, credential, role, retention, export, and deletion changes. Store audit records in an append-only system with independent retention.
---

## 19. Monitoring the Monitoring Platform
Use a small independent monitoring path or external provider for critical signals. The platform must not be its only observer.

### 19.1 Key SLIs
**Ingestion**
- Accepted and rejected samples/sec; Request latency and error rate; Kafka publish latency and consumer lag; Oldest unprocessed sample age; Accepted-to-queryable delay; Active and newly created series.
**Storage**
- WAL fsync latency and errors; Head memory and active chunks; Replication failures; Block upload and compaction backlog; Object-store errors and corrupt blocks; Compressed bytes per sample.
**Query**
- Latency by interactive, alert, and export class; Queue wait time; Scanned series and samples; Cache hit ratio; Cancellation, failure, and partial-result rate.
**Alerting**
- Late or failed rule evaluations; Condition-to-notification delay; Pending and firing alerts; Notification queue age; Delivery success and retry rate.

### 19.2 Platform Alerts
Page when:
- Queryable delay violates SLO; Kafka lag approaches retention; Rejection rate changes unexpectedly; Alert evaluations are late; Notification queue age exceeds one minute; Quorum or an availability zone is lost; DR replication violates RPO; Cardinality growth is abnormal.
Page on user-visible symptoms, not every redundant node failure.
---

## 20. Trade-Offs

### 20.1 Pull Versus Push
Pull gives central discovery and clear target health. It is difficult across firewalls and large networks. Push crosses outbound-only boundaries and supports agent buffering. It can hide a stopped client unless absence is monitored.
**Choice:** Pull locally with agents; push batches centrally.

### 20.2 Kafka Versus Direct Writes
Direct TSDB writes reduce latency and components. Kafka adds burst absorption, replay, independent consumers, and durable acceptance. It also adds cost and operational complexity.
**Choice:** Kafka is justified at 2M samples/sec and strict durability.

### 20.3 Local Disk Versus Object Storage
Local SSD is fast for a mutable recent head. Object storage is cheap and durable for immutable history.
**Choice:** Use both, with WAL/head locally and blocks in object storage.

### 20.4 Precompute Versus Query-Time Work
Precomputation makes repeated queries fast but costs storage and flexibility. Query-time aggregation is flexible but can scan too much.
**Choice:** Generic downsampling plus user-defined recording rules.

### 20.5 Consistency
Strong consistency for every sample reduces availability and increases latency. Short read delay is acceptable for monitoring.
**Choice:** Eventual sample visibility, strong control-plane metadata, durable acknowledgment.

### 20.6 Build Versus Buy
Use a mature Prometheus-compatible distributed TSDB in production. Building compression, indexing, compaction, PromQL, and corruption recovery is risky. The interview design still explains those internals.
---

## 21. Interviewer Follow-Ups

### Q1. Why is high cardinality dangerous?
Each unique label set creates a series with metadata, index, memory, chunks, compaction, and query cost. An unbounded `user_id` or request ID label can overwhelm the system.
### Q2. How do you calculate global p99 latency?
Emit histogram buckets with common boundaries. Sum bucket rates across instances and calculate the quantile from the merged distribution. Do not average per-instance p99 values.
### Q3. How do you handle counter resets?
When the current value is lower than the previous value, treat it as a reset. Rate functions sum non-negative deltas with reset correction.
### Q4. How do you detect a dead host?
Track scrape-generated `up` and last-seen timestamps. Mark series stale after a grace period and use an absence rule.
### Q5. Why acknowledge at Kafka rather than object storage?
Object block creation is delayed by head collection and compaction. Kafka replication gives fast durable acceptance and replay.

### Q6. What if Kafka replays samples?
Series ID plus timestamp gives logical identity. Bounded deduplication and deterministic conflict rules make replay safe.

### Q7. What if one tenant owns half the traffic?
Hash its series across many partitions, enforce quotas, reserve capacity, and move it to a dedicated cell if necessary.

### Q8. What is the likely bottleneck?
Active-series cardinality and query fan-out usually dominate numeric storage bytes. Confirm with memory/series, lag, index size, and scanned-sample metrics.

### Q9. How would logs and traces integrate?
Use shared resource labels and metric exemplars containing trace IDs. Keep logs and traces in specialized stores and link from dashboards.

### Q10. How would anomaly detection fit?
Run models in a separate stream or scheduled-query service. Write anomaly scores as derived series and reuse the alert engine.
---

## 22. Final Answer and Cheat Sheet

### 22.1 Five-Minute Closing Answer
> "I am designing for 100,000 hosts, 10 million active series, and two million peak samples per second. Agents scrape local exporters every ten seconds, attach controlled labels, batch and compress data, buffer it on disk, and push it to a regional endpoint."
> "The ingestion gateway authenticates the tenant, validates samples, enforces throughput and cardinality quotas, computes series IDs, and appends batches to replicated Kafka. I acknowledge only after Kafka durability. Consumers route by tenant and series to replicated TSDB ingesters, which write a WAL and compressed head chunks before publishing immutable indexed blocks to object storage."
> "A distributed TSDB owns samples because this workload needs high append throughput, time-series compression, label indexes, and range scans. PostgreSQL owns strongly consistent dashboards, rules, tenants, and permissions. Redis caches safe tenant-scoped query results. Raw data stays for 15 days, minute aggregates for 13 months, and hourly aggregates for three years."
> "The query frontend authenticates, parses, estimates cost, selects resolution, splits ranges, and dispatches to query workers. Workers intersect label posting lists, read only relevant chunks, and push partial aggregation near storage. Per-tenant limits on series, samples, concurrency, and regex expansion prevent noisy neighbors."
> "Evaluators shard rule groups with leases and run at deterministic timestamps. Alert instances move from inactive to pending to firing. Alert managers group, silence, inhibit, deduplicate, and route notifications through a durable queue. Stable fingerprints provide effectively-once behavior even though external channels cannot guarantee exactly once."
> "The main risk is cardinality explosion, so I enforce active-series and new-series quotas and expose top label contributors. Agent buffers, Kafka replay, WALs, multi-AZ replication, immutable blocks, checksums, and reconciliation handle failures. Each tenant has a home region, with Kafka, metadata, and blocks replicated to a fenced DR region. The key trade-off is eventual metric visibility for higher write availability while control metadata remains strongly consistent."

### 22.2 One-Minute Cheat Sheet
**Ask:** pull/push, scale, retention, dashboard/alert SLO.
**Scale:** 10M series, 10-second interval, 1M average, 2M peak samples/sec.
**Stores:** TSDB for samples, PostgreSQL for metadata, Kafka for replay, object storage for history, Redis for cache.
**Write:** agent -> gateway -> Kafka -> processor -> TSDB WAL/head -> object blocks.
**Read:** UI -> query frontend -> cache/scheduler -> workers -> TSDB/object storage.
**Alert:** rule evaluator -> state machine -> alert manager -> durable notification queue.
**Partition:** hash tenant plus series; blocks by tenant shard and time; rules by tenant/group.
**Guarantee:** at-least-once transport, idempotent sample writes, eventual visibility, strong metadata.
**Risk:** unbounded labels and expensive fan-out.
**Protection:** quotas, backpressure, compression, downsampling, fair scheduling, replay, fencing.
**Final sentence:** "The design independently scales ingestion, storage, querying, and alerting while cardinality controls and durable buffering keep it reliable and affordable."
