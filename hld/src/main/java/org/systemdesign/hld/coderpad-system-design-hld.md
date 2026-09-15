# CoderPad System Design - 40-50 Minute HLD Interview

> **Candidate level:** Software engineer with about 5 years of experience
>
> **Prompt:** "Design CoderPad, a collaborative browser-based coding interview platform."
>
> **Primary focus:** Real-time collaborative editing and secure, low-latency execution of untrusted code.

---

## 1. How I Would Start the Interview

I would not begin by drawing services. I would first establish what "CoderPad" means for this interview because it can include a collaborative editor, code execution, test cases, audio/video, playback, question banks, scoring, and integrations.

### Opening statement

> **Candidate:** "I will first clarify the expected features and scale. Then I will define the guarantees, estimate traffic, design the APIs and data model, draw the architecture, and deep-dive into the two hardest parts: concurrent editing and safely executing untrusted code. I will close with reliability, scaling, security, and trade-offs."

### Four questions to ask

Ask only the highest-value questions first:

1. **Which features are in scope?** Do we need pad creation, two-person real-time editing, code execution in multiple languages, test cases, chat, playback, and audio/video?
2. **What collaboration behavior is expected?** Is an interviewer-candidate session normally two people, and should both be able to edit and run code concurrently?
3. **What scale and latency should we design for?** How many interviews, concurrent pads, edit operations, and code executions should the platform handle?
4. **What execution constraints apply?** Which languages are supported, may code access the internet, and what CPU, memory, execution-time, and isolation guarantees are required?

### Simulated interviewer answers

> **Interviewer:** Support creating and joining an interview pad, real-time collaborative editing, multiple files, language selection, running code, custom test cases, output streaming, autosave, and session playback. Audio/video and AI assistance are out of scope.

> **Interviewer:** Usually there are two participants, but allow up to five. Both may edit and run code. The interviewer owns the session and controls permissions.

> **Interviewer:** Assume 200,000 interviews per day, 10,000 concurrent active pads at peak, and global users. Editor updates should normally appear to another participant in under 150 ms. Starting a warm execution should take under one second.

> **Interviewer:** Support Java, Python, JavaScript, C++, and Go initially. Code must run without outbound internet access. Limit each run to 10 seconds, 1 CPU, 512 MB RAM, and 10 MB output.

### What I state back

> **Candidate:** "I will design for small collaborative rooms with a maximum of five participants, not Google-Docs-sized documents with hundreds of editors. I will optimize the collaboration path for low latency and partition by pad. Code execution will be asynchronous and isolated from the collaboration system. PostgreSQL will own durable business metadata, object storage will hold snapshots and replay artifacts, Redis will hold ephemeral presence and routing, and a queue will absorb execution bursts."

---

## 2. Interview Time Plan

| Time | Discussion |
|---|---|
| 0-5 min | Clarifying questions, scope, and assumptions |
| 5-9 min | Functional and non-functional requirements |
| 9-13 min | Capacity estimates |
| 13-18 min | APIs and core data model |
| 18-25 min | High-level architecture |
| 25-33 min | Deep dive: real-time collaborative editing |
| 33-40 min | Deep dive: secure code execution |
| 40-44 min | Persistence, reconnect, and playback |
| 44-48 min | Scale, failures, security, and observability |
| 48-50 min | Trade-offs and final summary |

If the interviewer redirects the discussion, prioritize:

1. Real-time editing correctness.
2. Untrusted-code isolation.
3. End-to-end flows and failure behavior.
4. Storage choices and scale.

---

## 3. Requirements and Scope

### 3.1 Functional requirements

| ID | Requirement |
|---|---|
| FR-1 | An interviewer can create, schedule, start, and end an interview pad |
| FR-2 | Participants can join using an authenticated invitation or a short-lived guest token |
| FR-3 | Up to five participants can edit the same files in real time |
| FR-4 | The pad supports multiple files and one selected runtime language |
| FR-5 | Users can run code and receive stdout, stderr, compile errors, exit status, and timing |
| FR-6 | Users can define public or interviewer-hidden test cases |
| FR-7 | The editor autosaves and restores after refresh or disconnect |
| FR-8 | Presence, cursor, and selection updates are visible to collaborators |
| FR-9 | The completed interview can be replayed from its edit and execution timeline |
| FR-10 | The interviewer can switch a participant between view and edit permissions |

### 3.2 Non-functional requirements

| ID | Target |
|---|---|
| NFR-1 | p95 edit propagation below 150 ms within a region |
| NFR-2 | p99 collaboration API availability of 99.99% |
| NFR-3 | No acknowledged durable edit is silently lost |
| NFR-4 | Per-file edits converge to the same content and order for every connected client |
| NFR-5 | Warm execution starts in under 1 second at p95 |
| NFR-6 | Execution infrastructure survives malicious or buggy user programs |
| NFR-7 | A noisy or compromised runner cannot access the control plane or another run |
| NFR-8 | Global access, with participants of a pad pinned to one collaboration region |
| NFR-9 | Interview data is encrypted, access controlled, auditable, and retention configurable |
| NFR-10 | The system degrades gracefully during execution or playback subsystem failures |

### 3.3 Explicitly out of scope

- Audio and video calls; integrate with a separate WebRTC product if required.
- AI code completion or automated candidate scoring.
- A full Git hosting service.
- Arbitrary package installation and unrestricted internet access.
- Mobile-native editing.
- Hundreds of simultaneous editors in one document.

### 3.4 Core invariants

These are important to say aloud because they drive the design:

1. A pad has one authoritative ordered revision stream.
2. An accepted edit has a unique `clientOperationId` and is applied at most once.
3. All participants eventually converge on identical content at the same revision.
4. An execution runs an immutable source snapshot, not whatever the pad contains later.
5. User code never executes in an API or collaboration server.
6. Ending or revoking a pad prevents new joins and new executions, but does not corrupt saved history.

---

## 4. Back-of-the-Envelope Estimation

The goal is not perfect forecasting. It is to identify the dominant resources.

### 4.1 Assumptions

| Metric | Assumption |
|---|---:|
| Interviews per day | 200,000 |
| Average interview duration | 60 minutes |
| Peak concurrent active pads | 10,000 |
| Participants per pad | 2 average, 5 maximum |
| Active edit operations per pad | 5 operations/second at peak |
| Average encoded edit operation | 300 bytes before replication overhead |
| Runs per interview | 20 |
| Average source snapshot | 100 KB |
| Average execution output | 20 KB, capped at 10 MB |
| Retention | 1 year by default |

### 4.2 Collaboration traffic

```text
Peak incoming operations = 10,000 pads * 5 ops/sec
                         = 50,000 edit ops/sec

With an average of two peers, broadcast traffic is approximately:
50,000 * 2 * 300 bytes = 30 MB/sec before protocol overhead
```

This is manageable with a horizontally scaled WebSocket tier. The important property is that all operations for one pad must reach the same logical session owner.

### 4.3 Execution traffic

```text
Daily runs = 200,000 interviews * 20 = 4,000,000 runs/day
Average QPS = 4,000,000 / 86,400 ~= 46 runs/sec
Design peak at approximately 10x average = 500 run requests/sec
```

At a pessimistic average runtime of three seconds:

```text
Concurrent runners = 500 starts/sec * 3 sec = 1,500
```

Capacity must be divided by language and runtime version. Warm pools reduce start latency, while the queue provides backpressure during bursts.

### 4.4 Storage

Saving every keystroke as a full file is wasteful. Store compact operations and periodic snapshots.

```text
Source snapshot data/day = 200,000 * 100 KB = 20 GB/day
One year of final snapshots ~= 7.3 TB before replication
```

Operation history, execution outputs, and intermediate snapshots will be larger. If a session averages 5-20 MB of compressed replay data, yearly storage is roughly 0.4-1.5 PB. Object storage is appropriate because it is cheap, durable, and supports lifecycle policies.

### 4.5 Bandwidth and the likely bottleneck

Text collaboration bandwidth is moderate. The harder bottleneck is bursty compute:

- Language-specific runner capacity.
- Cold image or microVM startup.
- CPU-heavy or intentionally abusive programs.
- Streaming large output.

Therefore collaboration servers and execution workers must scale independently.

---

## 5. Core Domain Model

```text
Organization
  |
  +-- User
  |
  +-- Interview
        |
        +-- Pad
        |    +-- Files
        |    +-- Participants
        |    +-- Operations
        |    +-- Snapshots
        |
        +-- TestCases
        +-- Executions
        +-- AuditEvents
```

### Main entities

- **Interview:** Scheduling, owner, candidate, status, retention, and permissions.
- **Pad:** Live collaborative workspace and current durable revision.
- **Pad file:** Path, language mode, content revision, and deletion state.
- **Operation:** Insert, delete, file create/rename/delete, or language/config update.
- **Snapshot:** Complete immutable workspace at a specific revision.
- **Execution:** An asynchronous run of one exact snapshot under one runtime image.
- **Test case:** Input and expected output; hidden cases are never sent to the candidate client.
- **Timeline event:** Durable replay record for edits, runs, joins, permission changes, and comments.

---

## 6. API and Protocol Design

Use REST for lifecycle and query operations. Use WebSocket for bidirectional, low-latency collaboration and execution output.

### 6.1 Interview and pad REST APIs

```http
POST /v1/interviews
Authorization: Bearer <token>
Idempotency-Key: <uuid>

{
  "title": "Backend coding interview",
  "candidateEmail": "candidate@example.com",
  "startsAt": "2026-09-20T10:00:00Z",
  "language": "JAVA_21",
  "expiresAt": "2026-09-20T12:00:00Z"
}
```

```json
{
  "interviewId": "int_01K...",
  "padId": "pad_01K...",
  "joinUrl": "https://coder.example/p/pad_01K...",
  "status": "SCHEDULED"
}
```

```http
POST   /v1/interviews/{interviewId}/join-token
POST   /v1/interviews/{interviewId}/start
POST   /v1/interviews/{interviewId}/end
GET    /v1/pads/{padId}
GET    /v1/pads/{padId}/snapshot?revision=1572
GET    /v1/pads/{padId}/events?afterRevision=1500&limit=500
PATCH  /v1/pads/{padId}/participants/{userId}
GET    /v1/interviews/{interviewId}/replay-manifest
```

The join token should be short lived, pad scoped, user scoped, and contain the role. It must not be a permanent secret embedded in the URL.

### 6.2 WebSocket connection

```text
wss://collab.example.com/v1/pads/{padId}/connect
Authorization: Bearer <short-lived-pad-token>
```

The initial handshake contains:

```json
{
  "type": "HELLO",
  "connectionId": "conn_123",
  "lastSeenRevision": 1568,
  "supportedProtocolVersion": 2
}
```

The server responds with either missing operations or a new snapshot:

```json
{
  "type": "SYNC",
  "padId": "pad_01K...",
  "serverRevision": 1572,
  "mode": "DELTA",
  "operations": []
}
```

If the client is too far behind:

```json
{
  "type": "SYNC",
  "serverRevision": 1572,
  "mode": "SNAPSHOT",
  "snapshotUrl": "https://object-store/...signed...",
  "snapshotSha256": "..."
}
```

### 6.3 Edit message

```json
{
  "type": "EDIT",
  "clientOperationId": "client-7:9981",
  "fileId": "file_main",
  "baseRevision": 1572,
  "operation": {
    "kind": "INSERT",
    "position": 421,
    "text": "return result;"
  }
}
```

Authoritative response:

```json
{
  "type": "EDIT_COMMITTED",
  "clientOperationId": "client-7:9981",
  "fileId": "file_main",
  "revision": 1573,
  "authorId": "usr_123",
  "operation": {
    "kind": "INSERT",
    "position": 418,
    "text": "return result;"
  }
}
```

The position may differ because the server transformed the operation against concurrent edits.

### 6.4 Presence and cursor messages

```json
{
  "type": "CURSOR",
  "fileId": "file_main",
  "anchor": 420,
  "head": 425,
  "presenceSequence": 81
}
```

Cursor and typing events are ephemeral, rate limited, and may be dropped. They should not share the durability cost of source edits.

### 6.5 Execution APIs

```http
POST /v1/pads/{padId}/executions
Idempotency-Key: <uuid>

{
  "revision": 1573,
  "runtime": "JAVA_21",
  "entryFileId": "file_main",
  "stdin": "5\n",
  "testCaseIds": ["test_public_1"]
}
```

```json
{
  "executionId": "run_01K...",
  "status": "QUEUED",
  "snapshotRevision": 1573,
  "streamChannel": "execution:run_01K..."
}
```

Execution output is delivered on the existing WebSocket:

```json
{
  "type": "EXECUTION_OUTPUT",
  "executionId": "run_01K...",
  "sequence": 12,
  "stream": "STDOUT",
  "data": "42\n"
}
```

Final event:

```json
{
  "type": "EXECUTION_FINISHED",
  "executionId": "run_01K...",
  "status": "SUCCEEDED",
  "exitCode": 0,
  "cpuTimeMs": 81,
  "wallTimeMs": 130,
  "peakMemoryBytes": 25165824,
  "outputTruncated": false
}
```

Also expose `GET /v1/executions/{executionId}` so reconnecting clients can recover a missed result.

### 6.6 API correctness details

- `Idempotency-Key` prevents duplicate interview creation and duplicate execution submission.
- `clientOperationId` deduplicates retried edits.
- Every mutation verifies membership and role server-side.
- List and event APIs use cursor pagination, never offset pagination.
- API responses contain server timestamps and stable IDs, preferably UUIDv7 or another time-sortable unique ID.
- Return `409 REVISION_CONFLICT` only when an operation cannot be transformed, such as editing a deleted file.
- Return `429` with retry information when per-user, per-pad, or per-organization limits are exceeded.

---

## 7. Data Model and Database Choices

No single database is ideal for all paths.

### 7.1 Storage decision summary

| Data | Store | Why |
|---|---|---|
| Users, organizations, interviews, membership, permissions | PostgreSQL | Relational constraints, transactions, strong consistency, easy auditing |
| Pad/file metadata and current durable revision | PostgreSQL | Correct lifecycle transitions and ownership checks |
| Live session routing, presence, dedupe hot set | Redis Cluster | Low latency, TTL, atomic primitives, ephemeral data |
| Durable edit/timeline stream | Kafka plus object-store segments | Ordered partition per pad, replay, burst absorption, cheap long-term retention |
| Periodic and final workspace snapshots | Object storage such as S3 | Durable, cheap, compressed immutable blobs, lifecycle support |
| Execution request state and result metadata | PostgreSQL | User-visible state machine and idempotency |
| Execution dispatch | Kafka or a durable work queue | Backpressure, retries, consumer groups, language partitioning |
| Logs and metrics | Observability stores | Search, aggregation, alerting; not business source of truth |

### 7.2 Why PostgreSQL, not only MongoDB or Cassandra?

The control-plane entities are relational:

- An interview belongs to an organization and owner.
- Participants have roles and permissions.
- Join tokens and execution requests require uniqueness.
- Start/end/revoke transitions must be atomic.
- Audit and retention policies relate to organization configuration.

PostgreSQL gives transactions, foreign keys, uniqueness, indexes, and operational maturity. At this scale, shard by `organization_id` or `interview_id` only when a single primary can no longer serve the write load. Read replicas can serve history and administrative reads.

Cassandra or DynamoDB would be reasonable for a massive append-only operation log, but they are not needed for all business metadata. The design instead sends ordered operations through Kafka and compacts them into object storage.

### 7.3 Why not store code in Redis?

Redis is not the durable source of truth. It may hold:

- Current session owner: `padId -> collaborationNodeId`.
- Presence entries with short TTLs.
- Recent operation IDs for fast duplicate detection.
- A hot current document cache.

Durability comes from the replicated operation log and snapshots. Redis loss may cause reconnects and cache rebuilding, but not interview loss.

### 7.4 Relational schema

```sql
organizations (
    organization_id   UUID PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    retention_days    INTEGER NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
)

users (
    user_id           UUID PRIMARY KEY,
    organization_id   UUID NULL,
    email             VARCHAR(320) NOT NULL,
    display_name      VARCHAR(200) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, email)
)

interviews (
    interview_id      UUID PRIMARY KEY,
    organization_id   UUID NOT NULL,
    owner_user_id     UUID NOT NULL,
    pad_id            UUID NOT NULL UNIQUE,
    title             VARCHAR(300) NOT NULL,
    status            VARCHAR(30) NOT NULL,
    home_region       VARCHAR(30) NOT NULL,
    starts_at         TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    version           BIGINT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
)

pad_participants (
    pad_id            UUID NOT NULL,
    user_id           UUID NOT NULL,
    role              VARCHAR(30) NOT NULL,
    can_edit          BOOLEAN NOT NULL,
    joined_at         TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    PRIMARY KEY (pad_id, user_id)
)

pad_files (
    file_id           UUID PRIMARY KEY,
    pad_id            UUID NOT NULL,
    path              VARCHAR(500) NOT NULL,
    language          VARCHAR(50) NOT NULL,
    created_revision  BIGINT NOT NULL,
    deleted_revision  BIGINT,
    UNIQUE (pad_id, path)
)

pad_checkpoints (
    pad_id            UUID NOT NULL,
    revision          BIGINT NOT NULL,
    object_key        VARCHAR(1000) NOT NULL,
    sha256            CHAR(64) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (pad_id, revision)
)

executions (
    execution_id      UUID PRIMARY KEY,
    pad_id            UUID NOT NULL,
    requested_by      UUID NOT NULL,
    idempotency_key   VARCHAR(100) NOT NULL,
    snapshot_revision BIGINT NOT NULL,
    snapshot_sha256   CHAR(64) NOT NULL,
    runtime_id        VARCHAR(100) NOT NULL,
    runtime_image     VARCHAR(300) NOT NULL,
    status            VARCHAR(30) NOT NULL,
    exit_code         INTEGER,
    failure_reason    VARCHAR(100),
    result_object_key VARCHAR(1000),
    queued_at         TIMESTAMPTZ NOT NULL,
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    UNIQUE (pad_id, idempotency_key)
)
```

Useful indexes include:

- `interviews(organization_id, starts_at DESC)`.
- `interviews(owner_user_id, starts_at DESC)`.
- `pad_participants(user_id, pad_id)` for a user's interviews.
- `executions(pad_id, queued_at DESC)`.
- Partial indexes for active interviews and queued executions.

### 7.5 Operation event shape

```json
{
  "eventId": "evt_01K...",
  "padId": "pad_01K...",
  "revision": 1573,
  "clientOperationId": "client-7:9981",
  "authorId": "usr_123",
  "eventType": "TEXT_EDIT",
  "fileId": "file_main",
  "operation": {
    "kind": "INSERT",
    "position": 418,
    "text": "return result;"
  },
  "serverTimestamp": "2026-09-15T02:20:01.123Z"
}
```

Kafka partition key is `padId`, preserving order within a pad. The durable record is later batched, compressed, and written to object storage as a timeline segment.

---

## 8. High-Level Architecture

```text
                                  +----------------------+
                                  | Identity / Auth      |
                                  +----------+-----------+
                                             |
 Browser / IDE client                        |
        |                                    |
        +---- HTTPS ----> CDN / WAF / API Gateway
        |                         |
        |                         +------> Interview Service ----> PostgreSQL
        |                         |          |                        |
        |                         |          +---- audit outbox ------+
        |                         |
        +---- WebSocket ----------+------> Global Traffic Manager
                                                   |
                                      route to pad's home region
                                                   |
                                  +----------------v----------------+
                                  | WebSocket / Collaboration Tier  |
                                  | - auth and permission checks    |
                                  | - presence and cursor relay     |
                                  | - pad session ownership         |
                                  | - OT ordering and transform     |
                                  +-----+----------+-----------+----+
                                        |          |           |
                                    Redis       Kafka       Snapshotter
                                  presence/    ordered       |
                                   routing     pad log       v
                                                          Object Store
                                        |
                                  execution command
                                        |
                                  +-----v---------------------------+
                                  | Execution Service               |
                                  | - validates snapshot/runtime    |
                                  | - quota and idempotency         |
                                  | - creates execution record      |
                                  +-----+---------------------------+
                                        |
                              language-partitioned durable queues
                                        |
                    +-------------------+--------------------+
                    |                   |                    |
              Java runner pool    Python runner pool    C++ runner pool
                    |                   |                    |
                    +-------------------+--------------------+
                                        |
                            isolated microVM / sandbox
                                        |
                                 Result Stream Bus
                                        |
                           WebSocket Gateway + Object Store
```

### 8.1 Main components

#### API Gateway

- TLS termination, WAF, authentication, tenant and user rate limiting.
- Routes REST traffic and upgrades WebSocket connections.
- Rejects oversized payloads before they reach application services.

#### Interview Service

- Creates interviews, pads, invitations, and membership.
- Owns lifecycle state and authorization policy.
- Selects a home region when a pad is created.
- Does not process edit operations or execute code.

#### Collaboration Gateway / Session Service

- Maintains WebSocket connections.
- Assigns one logical leader to each active pad.
- Orders and transforms edit operations.
- Broadcasts committed edits and ephemeral presence.
- Publishes durable timeline events.
- Reconstructs a pad from a checkpoint plus operations after a failover.

#### Snapshot Service

- Periodically materializes all files at revision `R`.
- Compresses, hashes, encrypts, and writes the snapshot to object storage.
- Records the checkpoint only after the object is durable.
- Creates an immediate immutable snapshot when a run is requested.

#### Execution Service and Scheduler

- Verifies access, revision, runtime, quotas, and request size.
- Persists execution state and enqueues a job.
- Selects the language/version worker pool.
- Applies fair scheduling by tenant and interactive priority.

#### Runner

- Starts an isolated sandbox from a pinned, signed runtime image.
- Mounts source and test input, applies resource limits, and runs compile/execute commands.
- Streams bounded output and emits a final signed result.
- Destroys the sandbox and temporary storage after completion.

---

## 9. End-to-End Flow: Create and Join a Pad

### 9.1 Create

1. The interviewer calls `POST /v1/interviews` with an idempotency key.
2. API Gateway authenticates the caller and applies organization quota.
3. Interview Service selects a home region based on owner location and capacity.
4. In one PostgreSQL transaction it inserts the interview, pad, owner membership, initial file, and an outbox event.
5. The response returns the pad ID and invitation URL.
6. The outbox publisher asynchronously emits `InterviewCreated` for email and audit consumers.

Why use a transactional outbox? Creating the database row and independently publishing to Kafka creates a dual-write problem. The outbox row commits with the interview, and a relay publishes it with retries.

### 9.2 Join

1. The invite URL is exchanged for a short-lived pad token.
2. The server validates that the interview is active, the invite is not revoked, and the participant limit is not exceeded.
3. The client fetches the latest pad metadata and opens a WebSocket to the home region.
4. The collaboration gateway authenticates the token and discovers the pad leader from Redis or the session directory.
5. The client sends its last known revision.
6. The server sends missing operations when the gap is small; otherwise it sends the latest snapshot plus newer operations.
7. Presence is registered with a TTL and renewed by heartbeat.

If one participant is in another continent, both still connect to the pad's home region. This adds some latency for one user but prevents multi-leader edit conflicts. For interviews, correctness and simplicity are preferable to active-active editing.

---

## 10. Deep Dive: Real-Time Collaborative Editing

This is the first key interview deep dive.

### 10.1 The concurrency problem

Suppose both clients start with:

```text
cat
```

At the same time:

- Client A inserts `s` at position 3, intending `cats`.
- Client B inserts `r` at position 2, intending `cart`.

If each client blindly applies the other's original position, they can produce different results. We need a convergence algorithm.

### 10.2 Options: locking, OT, or CRDT

| Option | Advantages | Problems |
|---|---|---|
| Single-editor lock | Very simple | Poor interview experience; not truly collaborative |
| Last-write-wins full document | Easy persistence | Loses edits and sends large payloads |
| Operational Transformation (OT) | Mature for centralized live editors, compact text operations | Transform logic must be correct; server ordering required |
| CRDT | Strong eventual convergence and offline/multi-leader editing | Larger metadata, tombstones, more client complexity |

### 10.3 Choice: server-authoritative OT

Choose OT because:

- A pad has at most five editors.
- Sessions are online and short lived.
- We already prefer a single home region and one session leader.
- Centralized ordering makes permission changes, replay, and debugging easier.
- Operations remain compact.

CRDT is the better choice if offline-first editing or active-active regional editing is a hard requirement. This is a trade-off, not a claim that OT is always superior.

### 10.4 Operation lifecycle

Each client maintains:

- The last server revision it has applied.
- A queue of locally generated, unacknowledged operations.
- A stable client ID and monotonically increasing client operation sequence.

The server maintains:

- Current authoritative document state.
- Current pad revision.
- Recent operations required for transformation.
- A deduplication map for recently seen operation IDs.

For each edit:

1. Client applies the edit locally immediately for responsive typing.
2. Client sends `(clientOperationId, baseRevision, operation)`.
3. The pad leader checks authentication, permission, payload size, and file existence.
4. If `clientOperationId` was already committed, return its previous revision.
5. If `baseRevision < currentRevision`, transform the operation against all accepted operations in `(baseRevision, currentRevision]`.
6. Apply the transformed operation to authoritative state.
7. Assign the next monotonically increasing revision.
8. Append the committed operation to the replicated log.
9. Acknowledge the sender and broadcast to other clients.
10. Clients transform the committed remote operation against their pending local operations, apply it, and advance revision.

### 10.5 When is an edit acknowledged?

There are two possible policies:

1. **Fast acknowledgment:** acknowledge after the in-memory leader applies it, then persist asynchronously. Lowest latency but acknowledged edits may be lost on sudden leader failure.
2. **Durable acknowledgment:** acknowledge after the operation is replicated to the durable log. Slightly higher latency but honors "no acknowledged edit loss."

Choose durable acknowledgment. A Kafka producer configured with replication and `acks=all`, or an equivalent replicated log, should normally add only a few milliseconds within a region. Broadcast may happen optimistically, but the UI should distinguish local/pending state from durable state if needed.

### 10.6 Exactly-once and retries

Networks do not provide exactly-once delivery. We provide an effectively-once result:

- Every edit has a globally unique `clientOperationId`.
- The active leader keeps a recent dedupe cache.
- Durable events include the same ID.
- On retry, the server returns the already assigned revision.
- Consumers and snapshotters are idempotent by `(padId, revision)`.

### 10.7 Pad leadership and partitioning

Use consistent hashing or a partitioned session directory:

```text
owner = hash(padId) -> collaboration shard
```

Within a shard, one process owns the pad at a time. A short lease with a fencing token prevents two leaders from accepting operations after a network partition.

Every write to the durable log contains the leader epoch:

```text
(padId, leaderEpoch, revision)
```

A stale leader with an old fencing token cannot append. This avoids split brain.

### 10.8 Leader failure

1. WebSockets disconnect or stop receiving heartbeats.
2. Clients reconnect through the load balancer with exponential backoff and jitter.
3. A new leader acquires the lease and a higher fencing token.
4. It loads the latest object-store checkpoint.
5. It replays operations after the checkpoint to reconstruct the current revision.
6. Clients send their last committed revision and retry unacknowledged operations using the same IDs.
7. Duplicates are discarded, missing operations are applied, and editing resumes.

Checkpoint frequently enough to bound recovery, for example every 500 operations or 30 seconds, plus at session end.

### 10.9 Presence and cursor semantics

Presence does not need durable ordering:

- Heartbeat every 10-15 seconds.
- Redis TTL around 30-45 seconds.
- Cursor events throttled to perhaps 10-20 per second per user.
- Coalesce or drop stale cursor updates under load.
- Never let cursor traffic delay code edits.

### 10.10 File operations

File create, rename, delete, and language changes are events in the same pad revision stream. That prevents ordering anomalies such as a text edit being applied after its file was deleted.

Conflicts follow deterministic rules:

- Two creates at the same path: first committed event wins; second receives conflict.
- Edit concurrent with delete: deletion wins after its revision; rejected edit is surfaced to the client.
- Rename concurrent with edit: file identity is `fileId`, not path, so the edit still applies.

---

## 11. Deep Dive: Secure Code Execution

This is the second key interview deep dive. User code must be treated as hostile.

### 11.1 Why execution is asynchronous

Compilation or execution can take seconds and may queue during bursts. Holding a normal REST request open couples API capacity to compute capacity. Instead:

1. `POST /executions` creates a durable job.
2. The server returns `202 Accepted`.
3. Output streams over WebSocket.
4. The final result remains queryable by execution ID.

### 11.2 Exact source selection

The execution request names revision `R`. The Snapshot Service materializes or retrieves the immutable workspace for exactly `R`, calculates SHA-256, and stores the hash on the execution record.

This prevents a race:

```text
User clicks Run at revision 100
User keeps typing and pad advances to revision 105
Runner still executes immutable revision 100
```

The result UI displays the executed revision so users know which source produced it.

### 11.3 Scheduling

Use separate queues by runtime family or resource class:

```text
execution.java.21
execution.python.3_13
execution.node.24
execution.cpp.gcc
execution.go.1_25
```

The scheduler considers:

- Organization concurrency quota.
- User and pad rate limit.
- Interactive priority.
- Requested runtime.
- Available warm pool.
- Region and data locality.
- Queue age to avoid starvation.

Use weighted fair queuing so one large customer cannot consume all runners. Cap concurrent runs per pad, for example two active runs and a small queued backlog.

### 11.4 Isolation options

| Isolation | Startup | Isolation strength | Use |
|---|---:|---:|---|
| Process on shared host | Fastest | Weak | Do not use for hostile code |
| Standard container | Fast | Moderate; shared kernel | Only with strong additional sandboxing |
| gVisor/Kata container | Fast-medium | Stronger syscall/kernel boundary | Good practical option |
| Firecracker microVM | Medium, very fast when warm | Strong VM boundary | Preferred for strict multi-tenant execution |
| Dedicated VM per run | Slow and expensive | Strong | Usually unnecessary |

Choose Firecracker microVMs or an equivalent microVM sandbox. Maintain a warm pool per common language to meet startup latency. gVisor is a valid cost/latency trade-off and should be discussed if the interviewer prefers Kubernetes-native execution.

### 11.5 Sandbox controls

Every execution receives:

- One ephemeral microVM or sandbox.
- Non-root user.
- Read-only runtime image pinned by digest.
- Ephemeral writable filesystem with a strict quota.
- One vCPU and 512 MB memory enforced by cgroups/hypervisor.
- Ten-second wall-clock timeout and CPU quota.
- Process and thread count limit to block fork bombs.
- Output byte limit, for example 10 MB.
- No host mounts, Docker socket, cloud metadata endpoint, or control-plane credentials.
- No outbound network by default.
- Empty or allowlisted environment variables; never platform secrets.
- Restricted syscalls, capabilities, and devices.
- Independent network namespace.
- Forced termination and cleanup after completion.

Runtime images are minimal, signed, vulnerability scanned, and promoted through a controlled release process.

### 11.6 Compile and run flow

1. Worker claims the queued job using a lease.
2. It changes execution state from `QUEUED` to `STARTING` using compare-and-set.
3. Worker obtains the immutable source snapshot using a short-lived, read-only URL.
4. It verifies the SHA-256 hash.
5. It allocates a clean sandbox from the correct warm pool.
6. Source and input are copied to the ephemeral workspace.
7. Language adapter invokes the pinned compile command if required.
8. If compilation succeeds, it invokes the run command.
9. Stdout and stderr are chunked with monotonically increasing sequence numbers.
10. Output chunks are published to the result stream and bounded local buffer.
11. On completion, timeout, cancellation, or resource violation, the worker emits one terminal result.
12. Result and bounded logs are stored, execution state becomes terminal, and the sandbox is destroyed.

### 11.7 Language adapter

Use a versioned adapter contract rather than hard-coding commands throughout the scheduler:

```text
RuntimeAdapter
  - validateWorkspace(files)
  - compileCommand(entryFile, limits)
  - runCommand(entryFile, stdin, limits)
  - normalizeDiagnostics(rawOutput)
  - runtimeMetadata()
```

Adding a language means:

1. Build and sign a runtime image.
2. Implement and test the adapter.
3. Define limits and compile/run commands.
4. Register the runtime version.
5. Provision or autoscale its worker pool.

### 11.8 Test-case security

Hidden test input and expected output must not be sent to the browser. The Execution Service gives the runner short-lived access to encrypted test data. The runner reports pass/fail and allowed diagnostics, not hidden expected values.

Run hidden cases separately if needed so candidate code cannot infer all cases through one shared process. Apply aggregate resource limits to avoid using tests as an amplification mechanism.

### 11.9 Output streaming and backpressure

The child process writes to bounded pipes. If output exceeds the limit:

- Stop accepting output.
- Mark `outputTruncated=true`.
- Optionally terminate the run as `OUTPUT_LIMIT_EXCEEDED`.

Each chunk has a sequence number. The WebSocket gateway can detect gaps, and the client can fetch the final stored result after reconnecting. Slow clients must not cause unbounded memory growth in the runner.

### 11.10 Worker failure and duplicate execution

Execution is at-least-once at the queue layer. A lease makes stalled jobs recoverable:

- Worker renews the lease while the sandbox runs.
- If the worker dies, the lease expires.
- The scheduler may retry only when no terminal result exists.
- State transitions use compare-and-set.

A retry may run the same source twice, which is safe because the sandbox has no external network or persistent side effects. Only one terminal result wins in the execution record. If external side effects were allowed, stronger deduplication would be required.

---

## 12. Autosave, Snapshots, and Session Playback

### 12.1 Persistence strategy

Use an event log plus snapshots:

```text
Snapshot at revision 1000
   + operations 1001 ... 1573
   = document state at revision 1573
```

Advantages:

- Real-time operations remain small.
- Recovery does not replay the entire interview.
- Any revision can be reconstructed for execution or playback.
- The final state is cheap to read.
- Timeline data naturally supports audit and replay.

### 12.2 Snapshot policy

Create a checkpoint:

- Every 500 committed operations.
- Or every 30 seconds while actively editing.
- Immediately before an execution if a reusable snapshot for that revision does not exist.
- At interview end.

The snapshot record is committed only after upload and checksum verification. Older operation segments can move to colder storage after the final snapshot while respecting retention.

### 12.3 Playback

The replay manifest contains:

- Initial snapshot.
- Ordered edit segments.
- Join/leave and permission events.
- Cursor events if the product chooses to retain them.
- Execution request and result timestamps.
- Checkpoints for seeking.

The client plays events according to their server timestamps but uses revision order for code changes. For fast seeking, jump to the nearest checkpoint before the requested time and replay only the remainder.

Playback is eventually available after the interview ends; it should not be on the critical live-edit path.

### 12.4 Privacy and retention

- Make recording/replay visible to participants.
- Avoid retaining ephemeral cursor data unless the product requires it.
- Encrypt data using organization-aware keys where required.
- Delete snapshots, event segments, outputs, and metadata through a tracked deletion workflow.
- Apply legal holds before lifecycle deletion.
- Keep an immutable audit record of view, export, and deletion actions.

---

## 13. Caching Strategy

Use caches only where stale or rebuildable data is acceptable.

### Good cache candidates

- Active pad routing and leader lease.
- Presence and connection mapping.
- Hot document state in collaboration-node memory.
- Recent operation dedupe IDs.
- Organization quota and permission hints with short TTL.
- Runtime image layers on execution hosts.
- Static editor assets through CDN.

### Do not use cache as authority for

- Whether an interview is revoked or ended.
- Durable pad revision.
- Final execution status.
- Billing or audit data.
- Hidden test authorization.

Permission changes should publish invalidation events. Sensitive actions, such as starting a run or joining with an invite, recheck the authoritative state rather than trusting a long-lived cache.

---

## 14. Scaling the System

### 14.1 Collaboration tier

- Partition by `padId`.
- A pad is a natural unit because its traffic must be ordered.
- Maintain sticky logical ownership, not merely load-balancer connection stickiness.
- Scale nodes based on active connections, operations per second, CPU, and event-loop lag.
- Protect a shard with per-pad operation and payload limits.

One very active pad should not overload the node, although the five-participant limit already bounds fan-out.

### 14.2 Kafka / event log

- Partition by `padId` to preserve order.
- Use enough partitions for throughput and consumer parallelism.
- Replicate across availability zones.
- Snapshot consumers commit offsets only after durable output is written.
- Monitor hot partitions, produce latency, consumer lag, and under-replicated partitions.

Ordering is only required per pad, not globally. Global ordering would reduce scalability without product value.

### 14.3 PostgreSQL

Start with a primary plus synchronous standby across availability zones and read replicas.

As growth requires:

- Partition large execution and audit tables by time.
- Archive old large payloads to object storage.
- Shard tenants by `organization_id`.
- Keep all rows for one interview on the same shard.
- Use a directory service for organization-to-shard routing.

Do not prematurely shard a relational control plane before measurements justify the operational cost.

### 14.4 Runner pools

Scale each pool independently because demand differs by language:

- Keep a baseline warm capacity.
- Scale on queue depth, oldest-job age, utilization, and forecasted schedules.
- Prewarm capacity before common interview periods.
- Cache immutable runtime images on hosts.
- Use separate pools for higher-risk or resource-heavy runtimes.
- Apply organization concurrency quotas before jobs reach the queue.

### 14.5 Load shedding

Under overload, preserve core editing:

1. Drop or coalesce cursor and typing events.
2. Delay playback generation.
3. Reject excess execution requests with a retryable response.
4. Reduce nonessential analytics.
5. Continue durable code edits and reconnect.

Never silently drop accepted source edits.

---

## 15. Multi-Region Design

### 15.1 Region assignment

When an interview is created, choose a home region based on:

- Interviewer and candidate geography when known.
- Regulatory data residency.
- Current regional capacity.
- Organization preference.

Store `home_region` in the interview record. Global DNS or anycast routes the initial request, and the API returns the correct WebSocket regional endpoint.

### 15.2 Why not active-active editing?

Active-active editing would require cross-region CRDT/OT coordination and adds conflict and split-brain complexity. Most pads have two users for one hour. Pinning both to one region gives deterministic ordering and acceptable latency.

### 15.3 Replication and disaster recovery

- Replicate PostgreSQL to a paired region.
- Replicate object storage and final replay artifacts.
- Mirror or replicate durable event logs where the RPO requires it.
- Runtime images exist in regional registries.
- Keep infrastructure definitions and capacity plans ready in the failover region.

Possible targets:

- Availability: 99.99% collaboration plane.
- RPO: under one minute for acknowledged edit operations.
- RTO: under 15 minutes for regional failover.

During catastrophic regional loss, existing WebSockets disconnect. Clients reconnect to the promoted region and resend unacknowledged operations. The UI must clearly show pending versus saved state.

---

## 16. Reliability and Failure Scenarios

| Failure | Behavior | Recovery |
|---|---|---|
| Client loses network | Local edits remain pending; UI shows reconnecting | Reconnect with last revision and retry same operation IDs |
| Collaboration node crashes | Connections drop; acknowledged log remains durable | New leader loads checkpoint and replays operations |
| Redis unavailable | Presence/routing degraded | Rebuild routing, reconnect clients; durable edits continue through fallback directory |
| Kafka unavailable | Cannot honor durable edit acknowledgment | Buffer only within a strict bound, then pause editing with visible error; never claim edits are saved |
| Snapshot service delayed | Live collaboration continues | Replay log grows; alert on checkpoint age and consumer lag |
| PostgreSQL primary fails | Lifecycle APIs briefly unavailable | Promote synchronous standby; use idempotency on retries |
| Execution queue grows | Runs stay queued | Autoscale pools, fair schedule, reject beyond bounded backlog |
| Runner crashes | Lease expires | Retry in a fresh sandbox if no terminal result exists |
| Run loops forever | Sandbox reaches wall/CPU limit | Kill and return `TIME_LIMIT_EXCEEDED` |
| Fork bomb | PID/cgroup limit reached | Kill sandbox without affecting host |
| Output flood | Bounded pipe reaches cap | Truncate or terminate; never exhaust host memory |
| Object store temporarily unavailable | New snapshot/run cannot be materialized | Retry; live editing may continue while durable operation log is healthy |
| Region fails | Active sessions disconnect | Promote paired region and resync from replicated durable state |

The product should distinguish:

- **Locally typed:** visible only in local editor.
- **Sent:** delivered to collaboration leader.
- **Saved:** durably replicated and assigned a revision.

This prevents a misleading "saved" indicator during infrastructure failure.

---

## 17. Security Design

### 17.1 Authentication and authorization

- OIDC/SAML for organization users; short-lived scoped guest tokens for candidates.
- RBAC roles: owner, interviewer, candidate, observer.
- Server-side authorization on every REST request and WebSocket mutation.
- Revocation closes active WebSockets and prevents new executions.
- Invite links are single-purpose, expiring, and exchangeable rather than permanent bearer credentials.

### 17.2 Tenant isolation

- Every database query is scoped by organization and pad.
- Object keys are unguessable and signed URLs are short lived.
- Encryption at rest and in transit.
- Separate encryption keys for regulated enterprise tenants if required.
- Per-tenant execution, storage, and API quotas.
- Audit all invitation, replay, export, permission, and deletion actions.

### 17.3 Application threats

- Validate WebSocket message schema and maximum size.
- Rate limit edits, cursor updates, joins, runs, and output.
- Escape code and execution output before rendering to prevent XSS.
- Use strict Content Security Policy in the editor.
- Protect REST mutations against CSRF when cookie authentication is used.
- Scan uploaded auxiliary files.
- Never log source code, tokens, hidden tests, or stdin by default.

### 17.4 Execution threats

Defend against:

- Infinite loops and memory exhaustion.
- Fork bombs.
- Filesystem traversal.
- Kernel exploits.
- Container escapes.
- Crypto mining.
- Network scanning and data exfiltration.
- Cloud metadata credential theft.
- Side-channel leakage between runs.
- Compiler/runtime vulnerabilities.

Controls include microVM isolation, patched hosts, no network, minimal signed images, quotas, short execution lifetime, sandbox destruction, host monitoring, and regular escape testing.

### 17.5 Supply-chain security

- Pin runner images by immutable digest.
- Produce SBOMs.
- Sign images and verify signatures before launch.
- Continuously scan dependencies and base images.
- Roll out runtime changes gradually.
- Retain the exact image digest on every execution for reproducibility and incident response.

---

## 18. Consistency Model

| Data | Consistency |
|---|---|
| Edits within one pad | Strong total order through one authoritative leader |
| Connected-client document state | Eventually convergent to the authoritative revision |
| Interview start/end and permissions | Strongly consistent control-plane update |
| Presence and cursors | Best effort, eventually consistent |
| Replay availability | Eventual after compaction |
| Execution status | Strong state transition in DB; streaming output may be retried/duplicated by sequence |
| Administrative search/analytics | Eventual consistency |

CAP trade-off:

- During a partition that separates a stale collaboration leader from the durable log, reject or pause writes rather than accept edits that may conflict.
- Presence remains available and approximate.
- Already loaded editor content remains locally usable, but it is visibly unsaved until reconnection.

For the ordered edit stream, consistency is more important than accepting writes in both partitions.

---

## 19. Observability, SLOs, and Alerts

### 19.1 Key SLIs

#### Collaboration

- WebSocket connection success rate.
- Active connections and pads by region/node.
- Edit end-to-end propagation latency.
- Durable acknowledgment latency.
- Reconnect and resync rate.
- OT transformation conflict/error rate.
- Pad leader failover duration.
- Event-log produce latency and consumer lag.
- Checkpoint age and replay length.

#### Execution

- Submission success rate.
- Queue time by language and tenant.
- Warm versus cold start rate.
- Compile and execution duration.
- Success, compile-error, timeout, OOM, output-limit, and infrastructure-failure counts.
- Active sandboxes and pool saturation.
- Cleanup failures.
- Sandbox security violations.

#### Storage/control plane

- PostgreSQL transaction latency, connections, replication lag, and deadlocks.
- Redis hit rate, memory, eviction, and failover.
- Object-store error and latency.
- Timeline compaction lag.

### 19.2 Example SLOs

| SLO | Target |
|---|---|
| Successful authenticated pad connections | 99.99% monthly |
| Edit durable acknowledgment under 100 ms in-region | 99% |
| Edit visible to peer under 150 ms in-region | 95% |
| Warm run begins under 1 second | 95% |
| Execution infrastructure returns a terminal result | 99.9% |
| Final replay available within 2 minutes of ending | 99% |

Do not count user compile errors as platform failures. Separate user-code outcomes from infrastructure errors.

### 19.3 Useful alerts

- Edit p99 or durable-log latency breaches for five minutes.
- Reconnect rate sharply above baseline.
- Any acknowledged-revision gap.
- Oldest execution queue item above threshold.
- Warm pool below safe capacity.
- Runner cleanup failure or unexpected outbound network attempt.
- Database or Kafka replication lag threatening RPO.
- Snapshot checkpoint age above recovery objective.

Use distributed tracing with `requestId`, `padId`, and `executionId`, but hash or restrict pad identifiers in broad-access telemetry and never attach source code.

---

## 20. Important Trade-offs to Explain

### OT versus CRDT

- **Chosen:** OT with one server-authoritative order.
- **Reason:** Tiny online rooms, simpler metadata, lower bandwidth, deterministic replay.
- **Cost:** Home-region dependency and careful transformation implementation.
- **Switch to CRDT when:** Offline editing or active-active region writes are required.

### PostgreSQL versus NoSQL for control metadata

- **Chosen:** PostgreSQL for interviews, participants, and executions.
- **Reason:** Constraints, transactions, lifecycle state, idempotency.
- **Cost:** Eventually needs partitioning/sharding.
- **Use NoSQL when:** Operation history becomes a direct high-volume key-value workload, but do not force all relational metadata into it.

### Kafka versus direct database writes for every edit

- **Chosen:** Replicated ordered log with periodic snapshot compaction.
- **Reason:** Per-pad ordering, burst absorption, replay, decoupled consumers.
- **Cost:** Operational complexity and checkpoint/reconciliation logic.
- Direct PostgreSQL inserts can work for an early product, but high-frequency keystrokes create table/index pressure and make replay consumers harder to decouple.

### Container versus microVM

- **Chosen:** MicroVM or comparably hardened sandbox.
- **Reason:** User code is hostile and multi-tenant.
- **Cost:** Higher startup and infrastructure cost.
- Warm pools and cached images recover most latency.

### Synchronous durability versus fastest typing acknowledgment

- **Chosen:** Acknowledge saved state only after replicated-log durability.
- **Reason:** Avoid silently losing acknowledged interview work.
- **Cost:** Several milliseconds of additional latency.
- Local optimistic editing still makes typing feel immediate.

---

## 21. Likely Interviewer Follow-up Questions

### Q1. Why not save the whole document after every edit?

It wastes bandwidth and storage, creates last-write-wins races, and makes replay difficult. Compact insert/delete operations plus periodic snapshots give correct merging, efficient autosave, and timeline reconstruction.

### Q2. How do you prevent two collaboration nodes from leading one pad?

Use a lease with monotonically increasing fencing tokens. Every durable append includes the leader epoch. The log rejects writes from an older epoch, so a paused stale leader cannot resume and create split brain.

### Q3. What if Kafka is down?

If Kafka is the durability boundary, the server cannot honestly mark new edits saved. It may retain a small bounded pending buffer and show "saving," but after the bound it pauses edits or asks users to keep local pending changes. It must never acknowledge durable success and silently discard them.

### Q4. How do you run code in under one second?

Keep language-specific warm sandbox pools, pre-pull immutable images, place pools in each region, keep adapters and compiler caches ready, and autoscale based on queue age plus scheduled demand. Warm starts use preinitialized but clean templates; each run still gets an isolated ephemeral instance.

### Q5. Can users install packages?

Not with unrestricted internet. Offer a curated, scanned package catalog and internal read-only package proxy. Package lock files resolve only approved versions. This preserves reproducibility and blocks arbitrary network access.

### Q6. How do you support an interactive terminal?

Create a bounded execution session with a PTY inside the sandbox. WebSocket messages carry ordered stdin and terminal output. Apply idle timeout, total lifetime, output, process, memory, and CPU limits. Interactive sessions are more expensive, so use stricter quotas than one-shot runs.

### Q7. How are hidden tests protected?

Never send them to the browser. Decrypt them only inside the isolated runner using short-lived authorization, return limited pass/fail diagnostics, destroy the filesystem afterward, and audit access.

### Q8. What happens when both people click Run?

Each request captures its own exact revision and creates a separate execution ID. Enforce a small per-pad concurrency limit. The UI labels each result with requester, timestamp, runtime, and source revision.

### Q9. How do you cancel a run?

`POST /v1/executions/{id}/cancel` performs an idempotent state transition. If queued, mark canceled so workers skip it. If running, signal the worker, terminate the sandbox, persist `CANCELED`, and emit a terminal event. A race with normal completion is resolved by compare-and-set; exactly one terminal state wins.

### Q10. How do you recover a disconnected client with local edits?

The client reconnects with its last committed revision, obtains deltas or a snapshot, rebases its pending operations against the authoritative history, and retries them using unchanged operation IDs. Untransformable conflicts are shown explicitly rather than silently discarded.

### Q11. Would you put WebSockets behind a normal load balancer?

Yes, if it supports upgrades, long-lived connections, idle timeouts, draining, and health checks. Physical connection stickiness helps, but logical pad ownership still needs a directory because reconnects and pad participants may arrive at different gateways.

### Q12. How do you avoid a noisy tenant?

Use hierarchical quotas:

- Requests per user.
- Edit and run rate per pad.
- Concurrent runs per organization.
- Weighted fair queue allocation.
- Storage and retention quota.
- WebSocket connection cap.

Execution load cannot be allowed to reduce collaboration availability.

### Q13. Would you use WebRTC for editor collaboration?

No. WebRTC is useful for media and peer-to-peer data, but the editor needs server authorization, durable ordering, replay, and recovery. WebSocket to a central collaboration service is simpler and correct. Audio/video could independently use WebRTC.

### Q14. How would the design change for 100 simultaneous editors?

Fan-out, presence, and transform costs rise. I would batch operations, separate broadcast from ordering, use regional edge relays, consider CRDTs, and apply stronger cursor throttling. That is not necessary for the stated maximum of five.

---

## 22. Common Mistakes in This Interview

1. Drawing a generic load balancer, API, and database without explaining concurrent-edit convergence.
2. Saying "use WebSockets" as if transport solves ordering, durability, and conflict resolution.
3. Running user code in the application server or an ordinary shared process.
4. Naming Docker as the entire security strategy without network, resource, syscall, credential, and cleanup controls.
5. Executing "current code" without binding the run to an immutable revision.
6. Persisting every cursor movement while treating actual edits as best effort.
7. Choosing one database for every workload without explaining access patterns.
8. Claiming exactly-once network delivery instead of idempotent retry and deduplication.
9. Ignoring queue backpressure and noisy tenants.
10. Spending the whole interview on API names and never walking a failure.

---

## 23. A Strong Five-Minute Final Answer

> "I would design CoderPad as two independently scalable systems: a low-latency collaboration plane and an isolated code-execution plane.
>
> The lifecycle API uses PostgreSQL because interviews, participants, permissions, idempotency, and execution state need transactions and constraints. Each pad is assigned a home region. Participants connect over WebSocket to a collaboration tier, and all events for one pad go to one fenced session leader. The leader uses server-authoritative Operational Transformation to order concurrent insert/delete operations, appends accepted edits to a replicated log keyed by pad ID, assigns monotonically increasing revisions, and broadcasts them. Presence and cursors are ephemeral in Redis, while edits are durable. Periodic compressed snapshots go to object storage, so reconnect and failover load one checkpoint and replay only recent operations.
>
> A run request names an exact pad revision. The system materializes an immutable source snapshot, stores its hash, and enqueues an execution job by language. A fair scheduler dispatches to prewarmed Java, Python, JavaScript, C++, or Go pools. Each run gets a disposable microVM or hardened sandbox with no outbound network, no credentials, a read-only signed runtime image, non-root execution, and strict CPU, memory, PID, filesystem, output, and wall-time limits. Output is sequence-numbered and streamed to clients, while the durable final result remains queryable after reconnect.
>
> The system partitions collaboration and event streams by pad ID, scales runner pools independently by language and queue age, and sheds cursor or execution load before affecting source edits. It uses idempotency keys and client operation IDs because delivery is at least once. A crashed pad leader recovers from snapshot plus log; a crashed runner's lease expires and the side-effect-free job can retry safely. Security includes short-lived scoped join tokens, tenant isolation, encrypted storage, audited access, signed runtime images, and sandbox destruction.
>
> The central trade-off is OT with a single home-region leader versus CRDT. I choose OT for small, online interview rooms because it is simpler and compact. If offline or active-active editing becomes mandatory, I would reconsider CRDT."

---

## 24. Whiteboard Checklist

Before finishing, verify that the answer covered:

- [ ] Four clarifying questions and explicit scope.
- [ ] Functional requirements, NFRs, and scale estimates.
- [ ] REST for lifecycle and WebSocket for collaboration.
- [ ] PostgreSQL, Redis, ordered log, object storage, and why each is used.
- [ ] Per-pad ordering and partitioning.
- [ ] OT versus CRDT decision.
- [ ] Revision, operation ID, deduplication, and reconnect.
- [ ] Immutable source revision for every execution.
- [ ] Durable queue and language-specific warm pools.
- [ ] Strong untrusted-code isolation and resource controls.
- [ ] Autosave, snapshots, and replay.
- [ ] Multi-region ownership and disaster recovery.
- [ ] Backpressure, noisy tenants, and graceful degradation.
- [ ] Security, observability, SLOs, and failure scenarios.
- [ ] Final trade-offs and concise summary.

---

## 25. Final Interview Advice

Keep the conversation structured rather than listing technologies. For every component, connect the choice to a requirement:

- "I need deterministic convergence, so I use one authoritative pad stream and OT."
- "I need fast reconnect and playback, so I use an operation log plus checkpoints."
- "I execute hostile code, so it runs in a disposable microVM with no network or credentials."
- "Compute demand is bursty and language specific, so execution is queued and independently autoscaled."
- "Permissions and lifecycle transitions require constraints, so PostgreSQL owns control metadata."

The strongest answer is not the one with the most services. It is the one that states the invariants, identifies the two hard problems, walks the normal and failure flows, and explains each trade-off clearly.
