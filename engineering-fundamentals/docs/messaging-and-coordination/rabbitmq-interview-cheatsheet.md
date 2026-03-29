# ⚡ RabbitMQ in 5 Minutes — Interview Cheat Sheet

> Quick-fire RabbitMQ concepts. Know these for any distributed systems interview.

---

## What is RabbitMQ?

**One line:** RabbitMQ is an **open-source message broker** that implements AMQP (Advanced Message Queuing Protocol) for reliable, asynchronous communication between services.

**Think of it as:** A post office for your microservices. Producer sends a message → RabbitMQ routes it → Consumer receives it. Decoupled, reliable, asynchronous.

---

## Core Concepts — Know These Cold

```
┌──────────┐    ┌──────────────────────────────────┐    ┌──────────┐
│ Producer │──▶│           RabbitMQ                 │──▶│ Consumer │
│          │    │                                    │    │          │
│ (sends   │    │ Exchange ──(binding)──▶ Queue      │    │ (reads   │
│  message)│    │                                    │    │  message)│
└──────────┘    └──────────────────────────────────┘    └──────────┘
```

| Concept | What It Is |
|---------|-----------|
| **Producer** | Application that sends messages |
| **Consumer** | Application that receives messages |
| **Queue** | Buffer that stores messages (FIFO) |
| **Exchange** | Router that receives messages and routes to queues |
| **Binding** | Rule connecting an Exchange to a Queue (with routing key) |
| **Routing Key** | Label on a message that Exchange uses for routing |
| **Virtual Host (vhost)** | Logical separation (like namespaces) |
| **Connection** | TCP connection to RabbitMQ |
| **Channel** | Lightweight virtual connection inside a Connection |

---

## Exchange Types — The Interview Favorite

| Exchange Type | Routing Logic | Use Case |
|---------------|--------------|----------|
| **Direct** | Exact routing key match | Task distribution, logging levels |
| **Fanout** | Broadcast to ALL bound queues (ignores routing key) | Notifications, events |
| **Topic** | Pattern matching with wildcards (`*` = one word, `#` = zero or more) | Selective event routing |
| **Headers** | Match on message headers (not routing key) | Complex routing rules |

### Direct Exchange

```
Producer → Exchange (routing_key="payment.success")
              │
              ├── Queue A (binding: "payment.success") ✅ receives
              └── Queue B (binding: "order.created")   ❌ doesn't match
```

### Fanout Exchange

```
Producer → Exchange (routing_key=anything)
              │
              ├── Queue A ✅ receives (all bound queues get it)
              ├── Queue B ✅ receives
              └── Queue C ✅ receives
```

### Topic Exchange

```
Producer → Exchange (routing_key="order.created.us")
              │
              ├── Queue A (binding: "order.created.*") ✅ matches
              ├── Queue B (binding: "order.#")          ✅ matches
              └── Queue C (binding: "payment.#")        ❌ doesn't match
```

---

## Message Acknowledgment (Reliability)

```
1. Consumer receives message
2. Consumer processes message
3. Consumer sends ACK to RabbitMQ
4. RabbitMQ removes message from queue

If consumer crashes before ACK → message redelivered to another consumer!
```

| Mode | Behavior | Risk |
|------|----------|------|
| **Auto ACK** | ACK sent immediately on delivery | Message lost if consumer crashes during processing |
| **Manual ACK** | ACK sent after processing | Safe — message redelivered on crash |
| **NACK/Reject** | Consumer rejects message | Requeue or send to DLQ |

---

## Key Features

| Feature | What It Does |
|---------|-------------|
| **Durability** | Queue and messages survive broker restart (persistent) |
| **Dead Letter Queue (DLQ)** | Failed/expired messages routed here for inspection |
| **TTL** | Messages expire after a timeout |
| **Priority Queue** | Higher-priority messages consumed first |
| **Prefetch Count** | Limit unacknowledged messages per consumer (flow control) |
| **Clustering** | Multiple nodes for HA and scaling |
| **Quorum Queues** | Raft-based replicated queues (recommended for production) |

---

## RabbitMQ vs Kafka — The Classic Interview Question

| Feature | RabbitMQ | Kafka |
|---------|----------|-------|
| **Model** | Message queue (push to consumer) | Event log (consumer pulls) |
| **Message deletion** | After ACK | Retained for configured period |
| **Ordering** | Per queue | Per partition |
| **Replay** | ❌ (message gone after ACK) | ✅ (replay from any offset) |
| **Throughput** | ~50K msg/s | ~1M msg/s |
| **Routing** | Complex (exchanges, bindings) | Simple (topic → partition) |
| **Use case** | Task queues, RPC, complex routing | Event streaming, log aggregation, event sourcing |
| **Protocol** | AMQP | Custom binary |
| **Best for** | Traditional messaging | High-throughput streaming |

---

## 🔥 Top 10 Interview Questions (Quick Answers)

| # | Question | Key Answer |
|---|----------|-----------|
| 1 | What is RabbitMQ? | Open-source AMQP message broker for async communication between services. |
| 2 | Exchange types? | Direct (exact match), Fanout (broadcast), Topic (pattern), Headers (header-based). |
| 3 | What is a binding? | Rule connecting exchange to queue with a routing key pattern. |
| 4 | How to ensure no message loss? | Durable queue + persistent messages + manual ACK + publisher confirms. |
| 5 | What is DLQ? | Dead Letter Queue — receives failed/expired/rejected messages for inspection. |
| 6 | RabbitMQ vs Kafka? | RabbitMQ = smart broker, complex routing, message deleted after ACK. Kafka = dumb broker, high throughput, message retained. |
| 7 | What is prefetch? | Limits unACKed messages per consumer. Prevents fast producer overwhelming slow consumer. |
| 8 | Connection vs Channel? | Connection = TCP connection. Channel = lightweight virtual connection inside it. |
| 9 | What are quorum queues? | Raft-based replicated queues. Data replicated across nodes for HA. |
| 10 | How to scale consumers? | Multiple consumers on same queue = work queue pattern. RabbitMQ round-robins. |

---

## Quick Reference

```
Producer  → Sends messages
Exchange  → Routes messages (Direct, Fanout, Topic, Headers)
Binding   → Rule: Exchange → Queue (with routing key)
Queue     → Stores messages (FIFO buffer)
Consumer  → Receives and processes messages
ACK       → Consumer confirms processing (message removed)
DLQ       → Where failed messages go
Channel   → Virtual connection (lightweight)
vhost     → Namespace isolation
```

