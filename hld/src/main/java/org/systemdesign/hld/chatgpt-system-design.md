# System Design: ChatGPT / Claude-like Chat & Message Threading System

> Interview-style HLD covering schema, DB choice, multi-client concurrency, message ordering, regeneration, and branching (edit-to-fork) — like Claude/ChatGPT.

---

## 1. Requirements

### 1.1 Functional
1. A user can create **multiple conversations** (threads / chats).
2. Each conversation is an ordered sequence of **messages** (user ↔ assistant).
3. A user may open the **same conversation from multiple clients** (web, mobile, laptop) simultaneously.
4. Sending a message returns a **streamed AI response** (token-by-token) which is persisted.
5. **Regenerate**: User can re-ask the same question — the previous AI response is preserved and user can navigate between response *siblings* (like Claude's "‹ 2/3 ›").
6. **Edit-and-resend**: If a user edits an older user message (say the 5th-from-last), a **new branch** is created from that point. Older branch is preserved; user can toggle between branches using arrows on the message.
7. Real-time delivery across all clients (device A sends → device B sees in real time).
8. Search, rename, delete, share (nice-to-have).

### 1.2 Non-functional
- Low latency to first token (TTFT < 500 ms for stream start).
- Ordered, exactly-once user message persistence.
- Horizontal scale: 100M users, 10K QPS peak generations, huge write volume for messages.
- Durable, immutable message history (audit / compliance).
- Multi-region, high availability.

---

## 2. Core Data Model — Tree (DAG) of Messages, not a Flat List

This is the **single most important insight**. In a naive design, a conversation is a linked list:

```
msg1 → msg2 → msg3 → msg4 → msg5
```

But regeneration and edits create **siblings**. So a conversation is actually a **tree** of messages, and the "current view" is a **path from root to a chosen leaf**.

```
                 root (conversation)
                     │
                   U1 (user: "Hello")
                     │
                   A1 (assistant reply)
                     │
                   U2 ────────── U2'  (edited variant of U2 → new branch)
                   │             │
                 A2   A2'        A2''  (regenerated siblings of A2)
                   │
                   U3
                   │
                  ...
```

- Every message has a `parent_message_id`.
- **Regeneration** of an assistant reply = new assistant message with the *same parent* as the previous reply (sibling assistant messages).
- **Edit** of a user message = new user message with the *same parent* as the original user message (sibling user messages) → everything below is a new branch.
- The client shows arrows `‹ 2/3 ›` because there are multiple children of the same parent.
- A conversation stores a `current_leaf_message_id` pointer per user (or per client) representing the "active path" being displayed.

The active thread rendered on screen = walk from `current_leaf_message_id` up to the root, then reverse.

---

## 3. Database Choice

### 3.1 What we need to store
| Data | Volume | Access pattern | Consistency |
|---|---|---|---|
| Users, auth | Small | Point lookup | Strong |
| Conversations (metadata) | Medium | List by user, get by id | Strong |
| Messages (the tree) | **Massive**, append-heavy | Get all messages by conversation_id, get children of a message | Strong within conversation |
| Live stream tokens | Ephemeral | Pub/Sub | N/A (transient) |
| Embeddings for search | Large | ANN search | Eventual |

### 3.2 Choice — Polyglot Persistence

1. **PostgreSQL (primary OLTP store)** for `users`, `conversations`, `messages`.
    - Strong consistency, transactions (needed when we insert a user message + assistant placeholder + update `current_leaf` atomically).
    - Excellent for tree queries via recursive CTEs (`WITH RECURSIVE`) to fetch a branch.
    - Shard by `user_id` (or `conversation_id` hash) using **Citus / Vitess / native partitioning**. Each conversation lives entirely on one shard → all tree walks are single-shard.
    - Alternative: **DynamoDB / Cassandra** if we want purely append-only and hyperscale. Partition key = `conversation_id`, sort key = `created_at || message_id`. But recursive tree walks are awkward — we'd fetch all messages of the conversation (usually ≤ few hundred) and build the tree in the app layer. That's actually fine and is what ChatGPT/Claude likely do.
    - **Interview recommendation**: Postgres for correctness and simple tree ops; move to Cassandra/DynamoDB only if scale demands.

2. **Redis** for:
    - Per-conversation **distributed lock** (see §5 for ordering).
    - **Pub/Sub** for fan-out of streamed tokens to all connected clients of the same user.
    - Rate limiting, session cache, `current_leaf` cache.

3. **Kafka** for:
    - Event log: `MessageCreated`, `AssistantResponseCompleted` → drives analytics, embeddings pipeline, moderation, billing.
    - Decouples the write path from downstream consumers.

4. **Object Storage (S3 / GCS)** for large attachments (images, PDFs uploaded to a chat).

5. **Vector DB (pgvector / Pinecone / Weaviate)** for semantic search across a user's chats and for RAG.

6. **Elasticsearch / OpenSearch** for full-text search over conversation titles and message content.

---

## 4. Schema (PostgreSQL)

```sql
-- ---------- users ----------
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           TEXT UNIQUE NOT NULL,
    display_name    TEXT,
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- ---------- conversations ----------
CREATE TABLE conversations (
    id                          UUID PRIMARY KEY,
    user_id                     UUID NOT NULL REFERENCES users(id),
    title                       TEXT,
    model                       TEXT,           -- e.g. 'gpt-4o', 'claude-opus'
    system_prompt               TEXT,
    current_leaf_message_id     UUID,           -- pointer to the tip of the *currently displayed* branch
    created_at                  TIMESTAMPTZ DEFAULT now(),
    updated_at                  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ON conversations (user_id, updated_at DESC);

-- ---------- messages (the tree) ----------
CREATE TABLE messages (
    id                  UUID PRIMARY KEY,
    conversation_id     UUID NOT NULL REFERENCES conversations(id),
    parent_message_id   UUID REFERENCES messages(id),   -- NULL only for the synthetic root / first message
    role                TEXT NOT NULL CHECK (role IN ('system','user','assistant','tool')),
    content             JSONB NOT NULL,                 -- rich content: text blocks, images, tool_calls
    status              TEXT NOT NULL DEFAULT 'complete', -- 'streaming' | 'complete' | 'error' | 'cancelled'
    model               TEXT,                           -- which model produced it (assistant only)
    token_usage         JSONB,                          -- prompt/completion tokens
    client_message_id   UUID,                           -- idempotency key from the client
    seq                 BIGINT,                         -- monotonic seq within conversation (for ordering)
    created_at          TIMESTAMPTZ DEFAULT now(),
    UNIQUE (conversation_id, client_message_id)         -- idempotency
);
CREATE INDEX ON messages (conversation_id, seq);
CREATE INDEX ON messages (parent_message_id);

-- Optional: siblings count cache to render "‹ 2/3 ›" without extra query
CREATE TABLE message_siblings (
    parent_message_id   UUID PRIMARY KEY,
    child_ids           UUID[] NOT NULL,     -- ordered by created_at
    updated_at          TIMESTAMPTZ DEFAULT now()
);

-- ---------- per-client view state (so each device can be on a different branch if needed) ----------
CREATE TABLE conversation_views (
    conversation_id     UUID NOT NULL,
    user_id             UUID NOT NULL,
    client_id           TEXT NOT NULL,        -- e.g. device id
    active_leaf_id      UUID NOT NULL,
    updated_at          TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id, client_id)
);

-- ---------- attachments ----------
CREATE TABLE attachments (
    id              UUID PRIMARY KEY,
    message_id      UUID REFERENCES messages(id),
    s3_key          TEXT NOT NULL,
    mime_type       TEXT,
    size_bytes      BIGINT
);
```

### Key schema decisions
- `parent_message_id` gives us the **tree**. Regeneration & edit both = insert a sibling.
- `seq` is a monotonic per-conversation counter (obtained via Redis `INCR` or Postgres sequence keyed by conversation) → deterministic ordering even under concurrent writes.
- `client_message_id` (UUID generated on device) → **idempotency**: retries from a flaky mobile network don't duplicate messages.
- `status = 'streaming'` lets other devices see a "typing…" placeholder and progressively fill it.
- `current_leaf_message_id` on the conversation is the "global" branch pointer; `conversation_views` allows per-device override if you want each device to independently browse branches.

---

## 5. Handling Multiple Concurrent Clients for the Same User

Scenario: user has ChatGPT open on laptop, phone, and web simultaneously and sends messages from both laptop and phone almost at the same time.

### 5.1 The problem
- Both requests hit different API pods.
- Both want to append to the same conversation.
- Both need the assistant to reply.
- All three devices must see the same final ordering.

### 5.2 Design

1. **Idempotency via `client_message_id`**
   Every client generates a UUID for each user message. The API upserts on `(conversation_id, client_message_id)`. Retries are safe.

2. **Per-conversation lock (Redis / Postgres advisory lock)**
   To serialize appends within a single conversation:
   ```
   lock_key = "conv:{conversation_id}"
   Acquire with Redlock (TTL 30s, refreshed while streaming)
   ```
   Or use Postgres:
   ```sql
   SELECT pg_advisory_xact_lock(hashtext(conversation_id::text));
   ```
   This guarantees a **single writer per conversation at a time**, so `seq` and `parent_message_id` chains are consistent. Different conversations are unaffected → still highly parallel.

3. **Sticky routing (optional optimization)**
   Route all requests for `conversation_id = X` to the same app pod via consistent hashing (e.g., via Envoy). Reduces lock contention and lets you cache the conversation state.

4. **Real-time fan-out via WebSocket / SSE + Redis Pub/Sub**
    - Each connected client opens a WebSocket to a **Gateway service**.
    - Gateway subscribes to Redis channel `conv:{conversation_id}`.
    - When any writer produces tokens or persists a message, it `PUBLISH`es to that channel.
    - Every client of that user, on every device, receives the update.
    - The Gateway layer holds the WebSocket; the LLM worker only produces events.

5. **Ordering guarantee**
    - Server assigns `seq` under the lock.
    - Clients render by `seq`, not by local receive time.
    - If a client receives events out of order (network reorder), it buffers and reorders by `seq`.

6. **What if two devices ask questions at the same instant?**
    - Both go through the lock in some order.
    - Whichever wins the lock first gets `seq = N`, its assistant reply gets `seq = N+1`.
    - The other request waits, then appends `seq = N+2` (user) and `seq = N+3` (assistant).
    - Both devices see the same final linear history.
    - Alternative UX (Claude-like): reject the second while the first is still streaming with "already generating…" — simpler and closer to what real products do.

7. **Cancellation across devices**
    - User on phone hits Stop → API sends cancel to the LLM worker; worker writes final partial content with `status = 'cancelled'` and publishes to Redis; all devices update.

---

## 6. Regeneration (Multiple Assistant Responses to Same Question)

Sequence:

```
POST /messages/{assistant_msg_id}/regenerate
```

1. Look up the assistant message `A1` and its parent user message `U1`.
2. Build the prompt from root → `U1`.
3. Insert a new assistant message `A1'` with `parent_message_id = U1.id`, `status = 'streaming'`.
4. Update `conversations.current_leaf_message_id = A1'.id`.
5. Stream tokens as usual, publish to Redis, persist on completion.

To render arrows `‹ 2/3 ›`:
```sql
SELECT id, created_at
FROM messages
WHERE parent_message_id = :U1
ORDER BY created_at;
```
The index of the current message in that list = "which sibling". Clicking `‹`/`›` just changes `current_leaf_message_id` (walking down from a sibling, we choose the most-recently-visited child at each step — stored per-view).

---

## 7. Edit-and-Resend (Branching Mid-Thread)

Scenario: 20-message conversation. User edits message #15 (a user message) and resubmits.

1. Client calls:
   ```
   POST /messages/{U15}/edit_and_send
   Body: { content: "…new text…", client_message_id: <uuid> }
   ```
2. Server:
    - Loads `U15`, finds its parent `A14`.
    - Inserts **new** message `U15'` with `parent_message_id = A14.id`, `role = 'user'`, new content.
    - Inserts assistant placeholder `A15'` with `parent_message_id = U15'.id`, `status = 'streaming'`.
    - Sets `conversations.current_leaf_message_id = A15'.id` — the visible branch now flips to the new branch.
    - Old subtree (`U15 → A15 → U16 → A16 → …`) is **NOT deleted**. It's still reachable from `A14`.
3. Streams the new response.

Now `A14` has **two children**: `U15` (old) and `U15'` (new). The UI shows arrows on `U15'`:
```
[Edit] Prompt (‹ 2/2 ›)
```
Clicking `‹` sets `current_leaf_message_id` back to the tip of the old branch (server remembers the "last visited leaf" of each subtree per view, or we just pick the newest leaf under that child).

**Fetching the visible thread** for rendering:
```sql
WITH RECURSIVE path AS (
    SELECT * FROM messages WHERE id = :current_leaf
    UNION ALL
    SELECT m.* FROM messages m JOIN path p ON m.id = p.parent_message_id
)
SELECT * FROM path ORDER BY seq;
```

**Sibling badges** for every message on the path:
```sql
SELECT parent_message_id, count(*) AS n
FROM messages
WHERE parent_message_id IN (:list_of_parents_on_path)
GROUP BY parent_message_id
HAVING count(*) > 1;
```

---

## 8. High-Level Design (HLD)

```
                          ┌──────────────────────────┐
   Web / iOS / Android ──►│   API Gateway / CDN      │
                          │   (Auth, Rate limit)     │
                          └────────────┬─────────────┘
                                       │
                        ┌──────────────┴───────────────┐
                        ▼                              ▼
              ┌───────────────────┐         ┌──────────────────────┐
              │  Chat REST API    │         │  Realtime Gateway    │
              │  (stateless pods) │         │  (WebSocket / SSE)   │
              └─────────┬─────────┘         └──────────┬───────────┘
                        │                              │
        write path      │                              │ subscribe to
                        ▼                              │ conv:{id}
              ┌───────────────────┐                    │
              │ Conversation Svc  │◄───────────────────┘
              │ (locks, ordering, │
              │  branch mgmt)     │
              └───┬──────────┬────┘
                  │          │ publish tokens
     lock/pubsub  │          ▼
                  │    ┌──────────┐    ┌─────────────────────┐
                  │    │  Redis   │◄──►│ Inference Worker    │
                  │    │ (locks + │    │ (calls LLM, streams)│
                  │    │  pubsub) │    └──────────┬──────────┘
                  │    └──────────┘               │ persist final
                  ▼                               ▼
        ┌──────────────────────┐          ┌──────────────────────┐
        │  PostgreSQL          │◄─────────│  same DB via         │
        │  (sharded by user)   │          │  Conversation Svc    │
        │  users / conv / msg  │          └──────────────────────┘
        └────────┬─────────────┘
                 │ CDC / outbox
                 ▼
        ┌──────────────────────┐   ┌─────────────────┐   ┌─────────────┐
        │  Kafka event log     │──►│ Embedding job   │──►│ Vector DB   │
        │  MessageCreated etc. │   │ (RAG index)     │   │ (pgvector)  │
        └──────┬───────────────┘   └─────────────────┘   └─────────────┘
               │
               ▼
        ┌──────────────────┐
        │ Search indexer   │──► Elasticsearch
        │ Analytics / bill │──► Warehouse (BigQuery/Snowflake)
        │ Safety / mod     │
        └──────────────────┘
```

### End-to-end request flow (send a user message)

1. Client sends `POST /conversations/{id}/messages` over HTTP with a `client_message_id`, and opens/keeps a WebSocket to Realtime Gateway.
2. API Gateway authenticates the JWT and forwards to Chat API.
3. Chat API → Conversation Service:
   a. Acquire `conv:{id}` lock in Redis.
   b. Compute `seq`.
   c. Insert user message row.
   d. Insert assistant placeholder row (`status=streaming`).
   e. Update `current_leaf_message_id`.
   f. `PUBLISH conv:{id}` with `{event: 'user_message', msg}` and `{event: 'assistant_started', msg}`.
   g. Dispatch inference job (Kafka topic `inference_requests` or direct gRPC) with prompt = walk of the branch.
4. Inference Worker calls the LLM, streams tokens:
    - Per token: `PUBLISH conv:{id} {event:'token', assistant_id, delta}`.
    - Realtime Gateway forwards to every WebSocket subscribed to that conversation (all of the user's devices).
5. On completion, worker updates the assistant row (`content`, `status=complete`, `token_usage`) in a transaction, publishes `{event:'assistant_complete'}`, releases the lock, emits Kafka `AssistantResponseCompleted`.
6. Downstream: embedding, search index, billing, moderation.

### Why WebSocket + Redis Pub/Sub for multi-client?
- WebSocket gives push-based, low-latency streaming.
- Redis Pub/Sub decouples the writer pod from the reader (Gateway) pods.
- A single logical event is delivered to N devices without the writer knowing N.
- For durability of missed events (device offline), we also persist the `assistant_complete` message; on reconnect, clients call `GET /conversations/{id}/messages?since_seq=…` to catch up.

---

## 9. API Sketch

```
POST   /conversations                              → create conversation
GET    /conversations                              → list (paginated)
GET    /conversations/{id}?leaf={msg_id}           → active thread on a branch
POST   /conversations/{id}/messages                → send user message (streams response)
POST   /messages/{id}/regenerate                   → new assistant sibling
POST   /messages/{id}/edit_and_send                → new user sibling → new branch
POST   /conversations/{id}/select_branch           → set current_leaf_message_id
DELETE /conversations/{id}
GET    /conversations/{id}/tree                    → full tree (for branch navigator UI)
WS     /realtime                                   → subscribe to updates
```

Streaming responses use **Server-Sent Events** or **chunked HTTP** with events: `token`, `tool_call`, `done`, `error`.

---

## 10. Scaling & Reliability

| Concern | Approach |
|---|---|
| Message table size | Partition Postgres by `hash(user_id)` → 64/256 shards; retain hot data 90 days in Postgres, cold data → object storage / Cassandra. |
| Hot conversation (support agent + LLM) | Sticky routing + single-writer lock; contention is per-conversation, not global. |
| LLM cost & latency | GPU worker pool, autoscale via queue depth (Kafka lag). Speculative decoding, KV-cache reuse keyed by branch prefix hash. |
| WebSocket scale | Realtime Gateway is stateless w.r.t. business logic; connections sticky via consistent hashing (`conversation_id`). Millions of connections per fleet. |
| Multi-region | Postgres regional primary + read replicas; conversation home-region pinning; Redis local per region; cross-region async replication for account data. |
| Idempotency | `client_message_id` unique constraint; retry-safe. |
| Backpressure on streaming | If a client is slow, Gateway drops to that client only (others unaffected); the worker keeps streaming to Redis. |
| Abuse / safety | Moderation service in the pipeline before token emission; can hard-stop and mark `status='blocked'`. |
| Observability | Trace-id per request, OpenTelemetry; metrics: TTFT, tokens/sec, lock wait time, WebSocket fan-out ratio. |

---

## 11. Interview Talking Points (concise)

- **The single most important design idea**: represent a conversation as a **tree of messages** with `parent_message_id`, and store a `current_leaf_message_id` per conversation (or per view). Regenerate and edit both become "insert a sibling". Everything else follows.
- **DB**: Postgres for the tree (transactions + recursive CTEs, sharded by user), Redis for locks + pub/sub, Kafka for events, S3 for blobs, vector DB for RAG, ES for search.
- **Multi-client concurrency**: idempotency key from client + per-conversation Redis lock + monotonic `seq` + WebSocket fan-out via Redis Pub/Sub. All devices converge on the same ordering because the server, not the client, assigns `seq`.
- **Streaming**: SSE/WebSocket; tokens published to a Redis channel keyed by conversation; every connected device subscribes.
- **Branching (edit)**: never mutate an old message. Insert a new sibling of the edited node; move the `current_leaf` pointer. Old branch is fully preserved and reachable — that's what powers the `‹ 2/3 ›` arrows.
- **Trade-offs called out**: Postgres vs Cassandra/Dynamo (chose Postgres for correctness at this scale of per-conversation data), WebSocket vs long-poll (WS for latency), per-conversation lock vs lock-free CRDT (lock is simpler and sufficient because conversation writes are naturally serialized by human speed).

---

## 12. TL;DR

> A conversation is a **tree** of messages, not a list. Every user action (send, regenerate, edit) is an **append of a new node**; old nodes are immutable. The "current view" is a pointer to a leaf. Store the tree in **sharded Postgres**, coordinate concurrent writers with a **Redis per-conversation lock + monotonic seq**, and fan out streamed tokens to all of a user's devices through **Redis Pub/Sub over WebSocket**. Everything you see in ChatGPT/Claude — response siblings, edit-to-branch, back-arrows — falls out of this one data structure.
