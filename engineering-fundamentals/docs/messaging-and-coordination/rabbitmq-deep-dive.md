# 🐰 RabbitMQ Deep Dive — Senior Engineer's Complete Reference

> Everything a senior engineer should know about RabbitMQ.  
> From AMQP basics to clustering, quorum queues, and production best practices.

---

## Table of Contents

1. [What is RabbitMQ — Really?](#1-what-is-rabbitmq--really)
2. [AMQP Protocol — The Foundation](#2-amqp-protocol--the-foundation)
3. [Core Architecture — Exchanges, Queues, Bindings](#3-core-architecture--exchanges-queues-bindings)
4. [Exchange Types — Deep Dive](#4-exchange-types--deep-dive)
5. [Message Lifecycle — From Publish to Consume](#5-message-lifecycle--from-publish-to-consume)
6. [Reliability — Guaranteeing No Message Loss](#6-reliability--guaranteeing-no-message-loss)
7. [Dead Letter Exchanges (DLX) & DLQ](#7-dead-letter-exchanges-dlx--dlq)
8. [Message TTL, Expiry, and Priority](#8-message-ttl-expiry-and-priority)
9. [Consumer Patterns — Work Queues, Pub/Sub, RPC](#9-consumer-patterns--work-queues-pubsub-rpc)
10. [Prefetch & Flow Control](#10-prefetch--flow-control)
11. [Clustering & High Availability](#11-clustering--high-availability)
12. [Quorum Queues — Production Standard](#12-quorum-queues--production-standard)
13. [Streams (RabbitMQ 3.9+)](#13-streams-rabbitmq-39)
14. [Connection & Channel Management](#14-connection--channel-management)
15. [Spring Boot + RabbitMQ Integration](#15-spring-boot--rabbitmq-integration)
16. [Monitoring & Management](#16-monitoring--management)
17. [Performance Tuning](#17-performance-tuning)
18. [RabbitMQ vs Kafka vs ActiveMQ vs SQS](#18-rabbitmq-vs-kafka-vs-activemq-vs-sqs)
19. [Production Best Practices](#19-production-best-practices)
20. [Interview Q&A — 30 Questions](#20-interview-qa--30-questions)

---

## 1. What is RabbitMQ — Really?

RabbitMQ is a **message broker** — a middleman that receives messages from producers and delivers them to consumers.

### The Problem It Solves

```
Without message broker:
┌─────────┐                ┌─────────┐
│ Order   │────HTTP────────▶│ Payment │  If Payment is down → Order fails!
│ Service │                │ Service │  Tight coupling. Synchronous.
└─────────┘                └─────────┘

With RabbitMQ:
┌─────────┐    ┌──────────┐    ┌─────────┐
│ Order   │──▶│ RabbitMQ │──▶│ Payment │  If Payment is down → message waits!
│ Service │    │  (Broker) │    │ Service │  Decoupled. Asynchronous.
└─────────┘    └──────────┘    └─────────┘
```

### Key Benefits

| Benefit | Explanation |
|---------|------------|
| **Decoupling** | Producer doesn't know/care about consumer |
| **Async processing** | Producer doesn't wait for consumer to finish |
| **Load leveling** | Queue absorbs traffic spikes, consumers process at their own rate |
| **Reliability** | Messages persisted even if consumer is down |
| **Fan-out** | One message → multiple consumers |
| **Retry & DLQ** | Failed messages can be retried or inspected |

---

## 2. AMQP Protocol — The Foundation

AMQP (Advanced Message Queuing Protocol) is the wire protocol RabbitMQ implements.

### AMQP Model

```
Publisher ──▶ Exchange ──(routing)──▶ Queue ──▶ Consumer
                  │                     ▲
                  └───── Binding ───────┘
                    (routing key pattern)
```

### Connection Model

```
Application ──── Connection (TCP) ────── RabbitMQ
                      │
                 ┌────┴────────┐
                 │   Channel 1  │  ← Lightweight virtual connection
                 │   Channel 2  │
                 │   Channel 3  │
                 └──────────────┘
```

**Why channels?**  
TCP connections are expensive. Opening/closing TCP for each operation is wasteful. Channels multiplex over a single TCP connection. Use **one channel per thread** (channels are NOT thread-safe).

---

## 3. Core Architecture — Exchanges, Queues, Bindings

### Exchange

```
Exchange = Router
Receives messages from producers and routes them to queues based on:
1. Exchange type (direct, fanout, topic, headers)
2. Routing key on the message
3. Binding rules between exchange and queues
```

### Queue

```
Queue = Buffer (FIFO)
- Stores messages until consumed
- Can be durable (survives broker restart)
- Can have max length, TTL, priority
- Can be exclusive (one consumer only)
- Can be auto-delete (removed when last consumer disconnects)
```

### Queue Properties

```java
// Declaring a queue
Map<String, Object> args = new HashMap<>();
args.put("x-max-length", 10000);           // Max messages
args.put("x-message-ttl", 60000);          // TTL: 60 seconds
args.put("x-dead-letter-exchange", "dlx"); // DLX
args.put("x-dead-letter-routing-key", "dlq");
args.put("x-queue-type", "quorum");        // Quorum queue

channel.queueDeclare("orders", true, false, false, args);
//                    name    durable exclusive autoDelete arguments
```

### Binding

```
Binding = Rule: Exchange → Queue

Exchange ──── Binding (routing_key="order.created") ────▶ Queue

A queue can bind to multiple exchanges.
An exchange can bind to multiple queues.
```

---

## 4. Exchange Types — Deep Dive

### 1. Direct Exchange

```
Exact match on routing key.

Producer sends: routing_key = "payment.success"

Exchange ─── Binding (key="payment.success") ──▶ Payment Queue ✅
         └── Binding (key="order.created")   ──▶ Order Queue   ❌

Use case: Task routing, logging by severity (info, warn, error)
```

### 2. Fanout Exchange

```
Broadcast to ALL bound queues. Routing key ignored.

Producer sends: routing_key = "anything" (ignored)

Exchange ─── Binding ──▶ Queue A ✅
         ├── Binding ──▶ Queue B ✅
         └── Binding ──▶ Queue C ✅

Use case: Notifications, event broadcasting, pub/sub
```

### 3. Topic Exchange

```
Pattern matching with wildcards:
  * = exactly one word
  # = zero or more words

Producer sends: routing_key = "order.created.us"

Exchange ─── Binding (key="order.created.*")  ──▶ Queue A ✅ (* = "us")
         ├── Binding (key="order.#")           ──▶ Queue B ✅ (# = "created.us")
         ├── Binding (key="*.created.*")       ──▶ Queue C ✅ (* = "order", * = "us")
         └── Binding (key="payment.#")         ──▶ Queue D ❌ (doesn't start with "payment")

Use case: Selective event routing, geographic routing
```

### 4. Headers Exchange

```
Routes based on message headers (not routing key).

Producer sends: headers = {type: "report", format: "pdf"}

Exchange ─── Binding (headers: {type: "report"}, x-match: "any")  ──▶ Queue A ✅
         └── Binding (headers: {type: "report", format: "csv"}, x-match: "all") ──▶ Queue B ❌

x-match: "any" = match any header
x-match: "all" = match all headers

Use case: Complex routing not suited for string-based keys
```

### Default Exchange

```
Every queue is automatically bound to the default exchange
with routing_key = queue_name.

Producer sends to "" (empty exchange name) with routing_key = "myqueue"
→ Message goes directly to "myqueue"
→ Simplest possible routing
```

---

## 5. Message Lifecycle — From Publish to Consume

```
1. Producer publishes message to Exchange
   - Includes: routing key, headers, body, properties (persistent, TTL, priority)

2. Exchange routes to matching Queue(s) based on binding rules

3. Message stored in Queue
   - In memory (transient) or on disk (persistent)

4. Consumer receives message
   - Push model: RabbitMQ pushes to consumer (basic.consume)
   - Pull model: Consumer explicitly requests (basic.get) — use push model!

5. Consumer processes message

6. Consumer sends ACK
   - RabbitMQ removes message from queue

7. If NACK/Reject:
   - Requeue: message goes back to queue head
   - No requeue: message discarded or sent to DLX
```

### Message Properties

```java
AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
    .deliveryMode(2)                // 1 = transient, 2 = persistent
    .contentType("application/json")
    .priority(5)                    // 0-9 (if priority queue)
    .messageId(UUID.randomUUID().toString())
    .timestamp(new Date())
    .expiration("60000")            // TTL in ms
    .headers(Map.of("source", "order-service"))
    .build();

channel.basicPublish("exchange", "routing.key", properties, body);
```

---

## 6. Reliability — Guaranteeing No Message Loss

### The 4 Pillars of Reliability

```
1. Durable Exchange     → Exchange survives broker restart
2. Durable Queue        → Queue survives broker restart
3. Persistent Messages  → Messages written to disk
4. Consumer ACK         → Message only removed after processing

All 4 required for zero message loss!
```

### Publisher Confirms (Producer Side)

```java
// Enable publisher confirms
channel.confirmSelect();

// Publish
channel.basicPublish("exchange", "key", properties, body);

// Wait for confirmation from broker
if (channel.waitForConfirms(5000)) {
    // Message safely stored by broker
} else {
    // Handle failure — retry or log
}

// Async confirms (better performance)
channel.addConfirmListener(
    (deliveryTag, multiple) -> { /* ACK: message stored */ },
    (deliveryTag, multiple) -> { /* NACK: message NOT stored — retry! */ }
);
```

### Consumer Acknowledgment (Consumer Side)

```java
// Manual ACK (recommended)
channel.basicConsume("queue", false, (tag, delivery) -> {
    try {
        processMessage(delivery.getBody());
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    } catch (Exception e) {
        // Reject and requeue (or send to DLQ)
        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
        // requeue=true → back to queue. requeue=false → discarded/DLX
    }
}, tag -> {});
```

### Delivery Guarantee Levels

| Level | How | Trade-off |
|-------|-----|-----------|
| **At-most-once** | Auto ACK, no confirms | Fastest, may lose messages |
| **At-least-once** | Manual ACK + publisher confirms | Safe, may get duplicates |
| **Exactly-once** | At-least-once + idempotent consumer | Safest, most complex |

**Interview Q:** *"How to achieve exactly-once delivery?"*  
**A:** RabbitMQ provides at-least-once. For exactly-once, make consumers **idempotent** — use message ID deduplication, database unique constraints, or idempotency keys.

---

## 7. Dead Letter Exchanges (DLX) & DLQ

### When Messages Go to DLX

1. **Message rejected** (basic.nack/basic.reject with requeue=false)
2. **Message TTL expires**
3. **Queue max length exceeded** (oldest message dead-lettered)

### Setup DLX

```
Main Queue → (on failure) → DLX Exchange → DLQ Queue

┌──────────┐  rejects   ┌──────────┐          ┌──────────┐
│  Main    │───────────▶│   DLX    │─────────▶│   DLQ    │
│  Queue   │  TTL expires│ Exchange │          │  Queue   │
└──────────┘  max length └──────────┘          └──────────┘
```

```java
// Declare main queue with DLX settings
Map<String, Object> args = new HashMap<>();
args.put("x-dead-letter-exchange", "dlx-exchange");
args.put("x-dead-letter-routing-key", "dlq-key");
channel.queueDeclare("main-queue", true, false, false, args);

// Declare DLX exchange and DLQ
channel.exchangeDeclare("dlx-exchange", "direct");
channel.queueDeclare("dead-letter-queue", true, false, false, null);
channel.queueBind("dead-letter-queue", "dlx-exchange", "dlq-key");
```

### Retry Pattern with DLX

```
Main Queue → DLX → Wait Queue (TTL=30s) → DLX → Main Queue (retry!)
                                                      │
                                               After N retries → Final DLQ
```

---

## 8. Message TTL, Expiry, and Priority

### TTL (Time-To-Live)

```java
// Per-queue TTL (all messages in this queue expire after 60s)
Map<String, Object> args = Map.of("x-message-ttl", 60000);
channel.queueDeclare("temp-queue", true, false, false, args);

// Per-message TTL
AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
    .expiration("30000")  // This message expires in 30s
    .build();
channel.basicPublish("", "queue", props, body);
```

### Priority Queues

```java
// Declare priority queue (max priority 0-255, typically 0-10)
Map<String, Object> args = Map.of("x-max-priority", 10);
channel.queueDeclare("priority-queue", true, false, false, args);

// Publish with priority
AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
    .priority(8)  // Higher = processed first
    .build();
```

---

## 9. Consumer Patterns — Work Queues, Pub/Sub, RPC

### 1. Work Queue (Competing Consumers)

```
                    ┌──── Consumer A
Producer ──▶ Queue ─┤
                    └──── Consumer B
                    
Round-robin: messages distributed evenly across consumers
With prefetch=1: fast consumer gets more messages
```

### 2. Publish/Subscribe (Fan-out)

```
                    Fanout     ┌── Queue A ──▶ Consumer A
Producer ──▶ Exchange ─────────┤
                               └── Queue B ──▶ Consumer B
                    
Every consumer gets every message.
```

### 3. Request/Reply (RPC)

```
Client ──▶ Request Queue ──▶ Server
Client ◀── Reply Queue ◀──── Server

Client:
1. Create exclusive reply queue
2. Publish message with reply_to=reply-queue and correlation_id=uuid
3. Wait on reply queue

Server:
1. Consume from request queue
2. Process
3. Publish result to reply_to queue with same correlation_id
```

```java
// Client
String replyQueue = channel.queueDeclare().getQueue(); // Auto-generated exclusive queue
String corrId = UUID.randomUUID().toString();

AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
    .replyTo(replyQueue)
    .correlationId(corrId)
    .build();

channel.basicPublish("", "rpc-queue", props, "request".getBytes());

// Wait for reply with matching correlationId
```

### 4. Routing (Topic-Based)

```
Producer ──▶ Topic Exchange ──── "order.created.*" ──▶ Order Handler
                            └─── "order.#"         ──▶ Audit Log
```

---

## 10. Prefetch & Flow Control

### What is Prefetch?

```
Without prefetch (default):
RabbitMQ pushes ALL messages as fast as possible
→ Fast consumer good. Slow consumer overwhelmed → OOM!

With prefetch (QoS):
RabbitMQ pushes max N unacknowledged messages per consumer
→ Consumer processes at its own pace
```

```java
// Set prefetch = 10 (max 10 unACKed messages)
channel.basicQos(10);

// Per-channel vs per-consumer
channel.basicQos(10, false);  // Per consumer (recommended)
channel.basicQos(10, true);   // Per channel
```

### Choosing Prefetch Count

| Prefetch | Behavior |
|----------|----------|
| 1 | Safest. Fair distribution. Low throughput. |
| 10-50 | Good balance for most workloads |
| 100-500 | High throughput, but risk of memory issues |
| 0 (unlimited) | Dangerous! Consumer may OOM |

**Rule of thumb:** Start with `prefetch = 20`, measure, adjust.

---

## 11. Clustering & High Availability

### RabbitMQ Cluster Architecture

```
┌─────────────────────────────────────────┐
│              RabbitMQ Cluster             │
│                                          │
│  ┌────────┐  ┌────────┐  ┌────────┐    │
│  │ Node 1 │  │ Node 2 │  │ Node 3 │    │
│  │(disk)  │◀─▶│(disk) │◀─▶│(ram)  │    │
│  └────────┘  └────────┘  └────────┘    │
│                                          │
│  Metadata: replicated to all nodes       │
│  Messages: on the node where queue lives │
│            (unless quorum/mirrored queue) │
└─────────────────────────────────────────┘
```

### What's Replicated?

| Data | Replicated? |
|------|------------|
| Exchange/Queue definitions | ✅ All nodes |
| Bindings | ✅ All nodes |
| Users/Permissions | ✅ All nodes |
| Message data | ❌ Only on queue's node (unless quorum queue) |

### Classic Mirrored Queues (Deprecated)

```
# Don't use for new deployments. Use Quorum Queues instead.
# Mirrored queues have race conditions and split-brain issues.
```

---

## 12. Quorum Queues — Production Standard

### What Are Quorum Queues?

Quorum Queues use the **Raft consensus algorithm** to replicate messages across nodes.

```
Quorum Queue: "orders"
┌────────┐  ┌────────┐  ┌────────┐
│ Node 1 │  │ Node 2 │  │ Node 3 │
│ LEADER │  │FOLLOWER│  │FOLLOWER│
│ [msg1] │  │ [msg1] │  │ [msg1] │
│ [msg2] │  │ [msg2] │  │ [msg2] │
└────────┘  └────────┘  └────────┘

Write confirmed when majority (2 of 3) have the message.
If Node 1 dies → Node 2 or 3 becomes leader. No message loss.
```

### Quorum Queue vs Classic Queue

| Feature | Classic Queue | Quorum Queue |
|---------|--------------|-------------|
| Replication | None (single node) | Raft (majority-based) |
| Data safety | Lose data if node dies | Survive minority failures |
| Ordering | Strict FIFO | FIFO (per consumer) |
| Performance | Faster (no replication) | ~10-20% slower |
| Poison message handling | Manual | Built-in delivery limit |
| Non-durable | Supported | Always durable |

### Declare Quorum Queue

```java
Map<String, Object> args = Map.of(
    "x-queue-type", "quorum",
    "x-delivery-limit", 3    // After 3 redeliveries → dead-lettered
);
channel.queueDeclare("orders", true, false, false, args);
```

---

## 13. Streams (RabbitMQ 3.9+)

### RabbitMQ Streams — Kafka-like Functionality

```
Streams = append-only log (like Kafka topics)
- Messages are NOT deleted after consumption
- Multiple consumers can read from different offsets
- Replay capability!
```

```java
// Declare stream
Map<String, Object> args = Map.of(
    "x-queue-type", "stream",
    "x-max-length-bytes", 5_000_000_000L,  // 5GB retention
    "x-stream-max-segment-size-bytes", 100_000_000L  // 100MB segments
);
channel.queueDeclare("events-stream", true, false, false, args);

// Consume from beginning
channel.basicQos(100);
Map<String, Object> consumeArgs = Map.of("x-stream-offset", "first");
channel.basicConsume("events-stream", false, consumeArgs, callback, cancelCallback);
```

### When to Use Streams vs Queues

| | Classic/Quorum Queue | Stream |
|--|---------------------|--------|
| Message deletion | After ACK | Retained (time/size based) |
| Replay | ❌ | ✅ |
| Multiple consumers | Competing (work queue) | Independent (each reads all) |
| Use case | Task processing | Event sourcing, audit log |

---

## 14. Connection & Channel Management

### Best Practices

```
1. One Connection per application (or per thread pool)
   - TCP connection is expensive (handshake, authentication)
   - Reuse connections!

2. One Channel per thread
   - Channels are NOT thread-safe
   - Creating a channel is cheap
   
3. Don't create a new connection for each message!
   - Massive overhead
   - Connection pool if needed

4. Use connection recovery
   - Network issues happen
   - Enable automatic recovery
```

```java
// Connection with automatic recovery
ConnectionFactory factory = new ConnectionFactory();
factory.setHost("rabbitmq-host");
factory.setAutomaticRecoveryEnabled(true);
factory.setNetworkRecoveryInterval(5000);  // Retry every 5 seconds
factory.setRequestedHeartbeat(30);         // Heartbeat every 30s

Connection connection = factory.newConnection();
Channel channel = connection.createChannel();
```

---

## 15. Spring Boot + RabbitMQ Integration

### Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### Configuration

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000
```

### Producer

```java
@Service
public class OrderProducer {
    private final RabbitTemplate rabbitTemplate;
    
    public void sendOrder(Order order) {
        rabbitTemplate.convertAndSend(
            "order-exchange",    // exchange
            "order.created",     // routing key
            order                // message (auto-serialized to JSON)
        );
    }
}
```

### Consumer

```java
@Component
public class OrderConsumer {
    
    @RabbitListener(queues = "order-queue")
    public void handleOrder(Order order, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            processOrder(order);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            channel.basicNack(tag, false, false); // Send to DLQ
        }
    }
}
```

### Configuration Class

```java
@Configuration
public class RabbitConfig {
    
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("order-exchange");
    }
    
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable("order-queue")
            .withArgument("x-dead-letter-exchange", "dlx")
            .withArgument("x-dead-letter-routing-key", "dlq")
            .build();
    }
    
    @Bean
    public Binding orderBinding(Queue orderQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderQueue)
            .to(orderExchange)
            .with("order.*");
    }
    
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

---

## 16. Monitoring & Management

### Management Plugin (Web UI)

```bash
# Enable management plugin
rabbitmq-plugins enable rabbitmq_management

# Access at: http://localhost:15672
# Default: guest/guest (only from localhost)
```

### Key Metrics to Monitor

| Metric | What It Tells You |
|--------|------------------|
| Queue depth | Messages waiting. Growing = consumers too slow |
| Consumer count | Active consumers per queue |
| Message rate | Publish/deliver/ack per second |
| Unacknowledged count | Messages delivered but not yet ACKed |
| Memory usage | RabbitMQ memory. High = messages piling up |
| Disk space | For persistent messages |
| Connection count | Active connections |

### CLI Tools

```bash
rabbitmqctl list_queues name messages consumers
rabbitmqctl list_exchanges name type
rabbitmqctl list_bindings
rabbitmqctl list_connections
rabbitmqctl cluster_status
rabbitmqadmin get queue=myqueue count=10  # Peek at messages
```

---

## 17. Performance Tuning

| Technique | Impact |
|-----------|--------|
| **Batch publish** | Fewer network round trips |
| **Prefetch tuning** | Balance throughput vs fairness |
| **Persistent only when needed** | Transient messages = faster |
| **Lazy queues** | Store messages on disk (reduce memory, increase throughput) |
| **Larger messages** | Batch small messages into one |
| **Multiple consumers** | Scale processing horizontally |
| **Quorum queue replication** | Set to 3 (not 5) for performance |
| **Separate connections** | Publisher and consumer on different connections |

---

## 18. RabbitMQ vs Kafka vs ActiveMQ vs SQS

| Feature | RabbitMQ | Kafka | ActiveMQ | SQS |
|---------|----------|-------|----------|-----|
| Model | Smart broker | Dumb broker/smart consumer | Smart broker | Managed queue |
| Protocol | AMQP | Custom | JMS, AMQP | HTTP/SQS |
| Ordering | Per queue | Per partition | Per queue | Best effort (FIFO available) |
| Throughput | 50K msg/s | 1M+ msg/s | 10K msg/s | Varies |
| Replay | ❌ (streams: ✅) | ✅ | ❌ | ❌ |
| Routing | Complex (exchanges) | Topic/partition | Queues/topics | Simple queue |
| Hosting | Self or cloud | Self or cloud | Self | AWS managed |
| Best for | Complex routing, RPC | Event streaming | JMS legacy | Serverless, AWS |

---

## 19. Production Best Practices

```
□ Use Quorum Queues for critical data (not classic)
□ Enable publisher confirms + consumer manual ACK
□ Set up DLQ for every queue
□ Set message TTL to prevent infinite queue growth
□ Set max queue length as safety net
□ Use durable exchanges + queues + persistent messages
□ Monitor queue depth, memory, disk
□ Set up alerts for growing queues
□ Use connection pooling (don't create per-message)
□ One channel per thread
□ Prefetch = 10-50 (not 0 or 1)
□ Make consumers idempotent
□ Use JSON for message serialization (with schema versioning)
□ 3-node cluster minimum for production
□ Separate publisher and consumer connections
```

---

## 20. Interview Q&A — 30 Questions

| # | Question | Answer |
|---|----------|--------|
| 1 | What is RabbitMQ? | Open-source AMQP message broker for async communication |
| 2 | What is AMQP? | Wire protocol: Producer → Exchange → Binding → Queue → Consumer |
| 3 | Exchange types? | Direct (exact match), Fanout (broadcast), Topic (wildcard), Headers |
| 4 | Direct vs Topic exchange? | Direct = exact routing key match. Topic = wildcard pattern matching. |
| 5 | What is a binding? | Rule connecting exchange to queue with routing pattern |
| 6 | Connection vs Channel? | Connection = TCP. Channel = virtual connection, one per thread. |
| 7 | How to prevent message loss? | Durable queue + persistent msg + publisher confirm + manual ACK |
| 8 | What is publisher confirm? | Broker confirms message is stored. Producer knows it's safe. |
| 9 | Manual vs Auto ACK? | Manual = safe (ACK after processing). Auto = fast (may lose on crash). |
| 10 | What is DLQ? | Queue for failed/expired/rejected messages. For inspection and retry. |
| 11 | What is prefetch? | Limits unACKed messages per consumer. Flow control. |
| 12 | Quorum vs Classic queue? | Quorum = Raft-replicated, HA, data safe. Classic = single node. |
| 13 | How quorum queues work? | Raft consensus. Write confirmed by majority. Leader + followers. |
| 14 | What are RabbitMQ streams? | Append-only log. Messages retained. Replay possible. Like Kafka topics. |
| 15 | RabbitMQ vs Kafka? | RabbitMQ = complex routing, message deleted after ACK. Kafka = high throughput, retained log. |
| 16 | When to use RabbitMQ? | Task queues, complex routing, RPC, when you need acknowledgments. |
| 17 | When to use Kafka? | Event streaming, event sourcing, high throughput, replay needed. |
| 18 | What is message TTL? | Time-to-live. Message auto-expires after timeout. |
| 19 | How RPC works in RabbitMQ? | Request queue + reply-to header + correlation ID. |
| 20 | What is a vhost? | Virtual host = namespace. Isolates exchanges, queues, users. |
| 21 | How to scale consumers? | Add consumers to same queue. RabbitMQ round-robins messages. |
| 22 | What is a poison message? | Message that always fails processing. Quorum queues have delivery-limit. |
| 23 | How to retry failed messages? | Reject → DLX → Wait Queue (TTL) → Back to main queue. |
| 24 | Exactly-once delivery? | Use at-least-once + idempotent consumer (dedup by message ID). |
| 25 | How clustering works? | Metadata replicated to all nodes. Messages on queue's node (unless quorum). |
| 26 | What happens when a node fails? | Quorum queue: leader election. Classic: queue unavailable until node returns. |
| 27 | How to monitor RabbitMQ? | Management plugin (web UI), Prometheus + Grafana, CLI tools. |
| 28 | What is lazy queue? | Stores messages on disk instead of memory. Good for large backlogs. |
| 29 | What is shovel/federation? | Cross-datacenter message replication. Shovel = simple. Federation = flexible. |
| 30 | How to handle message ordering? | Single queue + single consumer = ordered. Multiple consumers = no guarantee. |

---

> **Pro Tip for Interviews:** Show you understand trade-offs: "I'd use RabbitMQ for our order processing pipeline because we need complex routing (topic exchange), dead-letter handling, and at-least-once delivery. If we needed event replay or very high throughput, I'd consider Kafka instead."

