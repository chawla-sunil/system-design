# ⚡ Apache Kafka in 5 Minutes — Interview Cheat Sheet

> Quick-fire Kafka concepts. Know these for any distributed systems or backend interview.

---

## What is Kafka?

**One line:** Kafka is a **distributed event streaming platform** that lets you publish, subscribe, store, and process streams of records (events) in real time, at massive scale.

**Think of it as:** An immutable, ordered **append-only log** that multiple services can write to and read from — independently and at their own pace. Messages are **not deleted** after consumption.

---

## Core Concepts — Know These Cold

```
┌──────────┐    ┌──────────────────────────────────────────────┐    ┌───────────────┐
│ Producer │──▶│                  Kafka Cluster                 │──▶│   Consumer    │
│          │    │                                                │    │  (in a Group) │
│ (writes  │    │  Topic: "orders"                               │    │               │
│  events) │    │    ├── Partition 0 → [msg1, msg2, msg5]        │    │  (pulls       │
│          │    │    ├── Partition 1 → [msg3, msg4, msg6]        │    │   events)     │
│          │    │    └── Partition 2 → [msg7, msg8, msg9]        │    │               │
└──────────┘    └──────────────────────────────────────────────┘    └───────────────┘
```

| Concept | What It Is |
|---------|-----------|
| **Broker** | A Kafka server. Stores data on disk. Cluster = multiple brokers. |
| **Topic** | A named stream of events (like a table name). Logical category. |
| **Partition** | A topic is split into partitions. Each is an **ordered, immutable log**. |
| **Offset** | Unique sequential ID for each message within a partition. |
| **Producer** | Application that publishes (writes) events to topics. |
| **Consumer** | Application that subscribes (reads) events from topics. |
| **Consumer Group** | Group of consumers that **divide partitions** among themselves for parallel consumption. |
| **Replica** | Copy of a partition on another broker for fault tolerance. |
| **Leader** | The ONE replica that handles all reads/writes for a partition. |
| **Follower** | Replicas that passively replicate data from the leader. |
| **ISR** | In-Sync Replicas — followers caught up with the leader. |
| **Controller** | ONE special broker that manages partition leadership & broker lifecycle. |
| **ZooKeeper / KRaft** | Cluster coordination — metadata, elections, configuration. |

---

## Kafka Architecture at a Glance

```
                     Kafka Cluster
        ┌─────────────────────────────────────┐
        │  Broker 0    Broker 1    Broker 2   │
        │  ┌───────┐  ┌───────┐  ┌───────┐   │
        │  │P0 (L) │  │P0 (F) │  │P1 (L) │   │
        │  │P2 (F) │  │P1 (F) │  │P2 (L) │   │
        │  └───────┘  └───────┘  └───────┘   │
        └─────────────────────────────────────┘
                       ▲     │
                       │     ▼
              ZooKeeper / KRaft (metadata)

  L = Leader, F = Follower
```

---

## Partitions & Offsets — The Heart of Kafka

```
Topic: "orders" — Partition 0

  Offset:  0    1    2    3    4    5    6    7
         ┌────┬────┬────┬────┬────┬────┬────┬────┐
         │msg1│msg2│msg3│msg4│msg5│msg6│msg7│msg8│ ← append-only log
         └────┴────┴────┴────┴────┴────┴────┴────┘
                              ▲              ▲
                              │              │
                    Consumer A (offset 3)   Consumer B (offset 6)
                    (can replay from 0!)    (reading latest)
```

| Key Insight | Why It Matters |
|-------------|---------------|
| Messages are **not deleted** after consumption | Multiple consumers can read same data independently |
| Ordering guaranteed **within a partition** | NOT across partitions |
| Offset is per-partition, per-consumer-group | Each group tracks its own progress |
| Retention = time-based or size-based | Default: 7 days (`log.retention.hours=168`) |

---

## Producer Essentials

### Partitioning Strategy

```
Producer sends message with key
    │
    ├── Key = null     → Round-robin across partitions
    ├── Key = "user42" → hash(key) % num_partitions → always same partition
    └── Custom partitioner → your own logic
```

### Producer Acknowledgments (`acks`)

| Setting | Behavior | Durability | Performance |
|---------|----------|------------|-------------|
| `acks=0` | Don't wait for any ACK | ❌ May lose data | 🚀 Fastest |
| `acks=1` | Wait for leader ACK only | ⚠️ Data loss if leader crashes before replication | ⚡ Fast |
| `acks=all` (`-1`) | Wait for ALL ISR replicas to ACK | ✅ Strongest guarantee | 🐢 Slowest |

**Interview favorite:** `acks=all` + `min.insync.replicas=2` = **no data loss** even if one broker dies.

### Idempotent Producer

```
enable.idempotence=true  (default since Kafka 3.0)

Producer assigns sequence number to each message.
Broker deduplicates: same (producerId, sequence) = skip.
Result: Exactly-once delivery to the broker. No duplicates.
```

---

## Consumer Essentials

### Consumer Groups — Parallel Consumption

```
Topic: "orders" (3 partitions)

Consumer Group "checkout-service":
  Consumer A → Partition 0
  Consumer B → Partition 1
  Consumer C → Partition 2
  (each partition assigned to exactly ONE consumer in the group)

Consumer Group "analytics-service":
  Consumer X → Partition 0, 1, 2
  (single consumer gets all partitions)

⚠️ If consumers > partitions → extra consumers sit idle!
```

### Consumer Offsets

```
Consumer reads message at offset 5
    │
    ├── Processes it
    │
    └── Commits offset 6 (next to read) → stored in __consumer_offsets topic

On restart → consumer resumes from last committed offset.
```

| Commit Strategy | Behavior | Risk |
|-----------------|----------|------|
| **Auto-commit** | Commits periodically (default every 5s) | May lose or duplicate messages on crash |
| **Manual sync** | `commitSync()` — blocks until committed | Slow but safe |
| **Manual async** | `commitAsync()` — non-blocking | Fast, but may miss on failure |

### Rebalancing

```
Consumer joins or leaves group
    │
    ├── Group Coordinator triggers rebalance
    ├── All consumers stop, give up partitions
    ├── Partitions reassigned
    └── Consumers resume from last committed offset

⚠️ Rebalancing = downtime. Minimize with:
   - Sticky Assignor (minimizes partition movement)
   - Cooperative rebalancing (incremental, not stop-the-world)
   - Static group membership (consumer.group.instance.id)
```

---

## Replication & Fault Tolerance

```
Topic: "orders", Partition 0, Replication Factor = 3

  Broker 1 (Leader):   [msg1, msg2, msg3, msg4, msg5]
  Broker 2 (Follower): [msg1, msg2, msg3, msg4, msg5]  ← in-sync ✅ ISR
  Broker 3 (Follower): [msg1, msg2, msg3]               ← lagging  ❌ out of ISR
```

| Scenario | What Happens |
|----------|-------------|
| Leader dies, ISR has followers | Follower from ISR promoted → **no data loss** |
| Leader dies, ISR = only leader | `unclean.leader.election=true` → out-of-sync follower becomes leader → **data loss possible** |
| `unclean.leader.election=false` | Partition goes **offline** until leader recovers |

**Key config:** `min.insync.replicas=2` → producer write fails if fewer than 2 replicas in ISR. Prevents writing to a single replica.

---

## Key Configurations — Interview Must-Know

### Broker Configs

| Config | Default | What It Does |
|--------|---------|-------------|
| `log.retention.hours` | 168 (7 days) | How long to keep messages |
| `log.retention.bytes` | -1 (unlimited) | Max size per partition log |
| `num.partitions` | 1 | Default partitions for new topics |
| `default.replication.factor` | 1 | Default replicas for new topics |
| `min.insync.replicas` | 1 | Min ISR for `acks=all` to succeed |
| `unclean.leader.election.enable` | false | Allow out-of-sync replica to become leader |
| `log.segment.bytes` | 1 GB | Size of each log segment file |
| `log.cleanup.policy` | delete | `delete` or `compact` |

### Producer Configs

| Config | Default | What It Does |
|--------|---------|-------------|
| `acks` | all | Acknowledgment level |
| `retries` | MAX_INT | Retry count on failure |
| `batch.size` | 16384 (16 KB) | Batch size before sending |
| `linger.ms` | 0 | Wait time to fill batch |
| `enable.idempotence` | true | Exactly-once per partition |
| `max.in.flight.requests.per.connection` | 5 | Pipelining (set to 1 for strict ordering without idempotence) |

### Consumer Configs

| Config | Default | What It Does |
|--------|---------|-------------|
| `group.id` | — | Consumer group name |
| `auto.offset.reset` | latest | Where to start: `earliest` or `latest` |
| `enable.auto.commit` | true | Auto-commit offsets |
| `auto.commit.interval.ms` | 5000 | Auto-commit frequency |
| `max.poll.records` | 500 | Max records per `poll()` |
| `session.timeout.ms` | 45000 | Consumer considered dead after this |
| `heartbeat.interval.ms` | 3000 | Heartbeat frequency |

---

## Log Compaction — Often Missed in Interviews

```
log.cleanup.policy=compact

Before compaction (key → value):
  Offset 0: user42 → "New York"
  Offset 1: user99 → "London"
  Offset 2: user42 → "San Francisco"    ← latest for user42
  Offset 3: user99 → "Tokyo"            ← latest for user99

After compaction:
  Offset 2: user42 → "San Francisco"    ← kept (latest)
  Offset 3: user99 → "Tokyo"            ← kept (latest)

Use case: Maintaining latest state per key (like a changelog / KTable).
```

---

## Kafka Guarantees

| Guarantee | Details |
|-----------|---------|
| **Ordering** | Guaranteed within a partition. NOT across partitions. |
| **Durability** | `acks=all` + `min.insync.replicas=2` + replication factor 3 = no data loss |
| **At-least-once** | Default. Consumer may process same message twice on crash. |
| **At-most-once** | Commit offset before processing. May lose messages. |
| **Exactly-once** | Idempotent producer + transactional API + `read_committed` consumers |

---

## Kafka vs RabbitMQ — The Classic Comparison

| Feature | Kafka | RabbitMQ |
|---------|-------|----------|
| **Model** | Pull-based event log | Push-based message queue |
| **Message retention** | Retained (time/size-based) | Deleted after ACK |
| **Replay** | ✅ Replay from any offset | ❌ Gone after consumed |
| **Ordering** | Per partition | Per queue |
| **Throughput** | ~1M msg/s | ~50K msg/s |
| **Routing** | Simple (topic → partition) | Complex (exchanges, bindings) |
| **Consumer model** | Consumer groups | Competing consumers |
| **Protocol** | Custom binary over TCP | AMQP |
| **Best for** | Event streaming, log aggregation, event sourcing | Task queues, RPC, complex routing |
| **Message size** | Optimized for small messages (1 KB default max 1 MB) | Flexible |

---

## ZooKeeper vs KRaft — Quick Reference

| Aspect | ZooKeeper Mode | KRaft Mode (Kafka 3.3+) |
|--------|---------------|------------------------|
| **Dependency** | External ZK cluster needed | Self-contained |
| **Metadata store** | ZooKeeper znodes | `__cluster_metadata` topic |
| **Controller election** | ZK ephemeral znode | Raft consensus |
| **Partition limit** | ~200K | Millions |
| **Controller failover** | Seconds | Milliseconds |
| **Status** | Deprecated (Kafka 3.5+) | Production-ready, the future |

---

## Kafka Transactions & Exactly-Once Semantics (EOS)

```
Producer:
  props.put("transactional.id", "my-txn-id");
  producer.initTransactions();
  producer.beginTransaction();
  producer.send(record1);
  producer.send(record2);
  producer.sendOffsetsToTransaction(offsets, groupMetadata);  // consume + produce atomically
  producer.commitTransaction();

Consumer:
  props.put("isolation.level", "read_committed");
  // Only sees committed messages — uncommitted/aborted are filtered out
```

**EOS = Idempotent Producer + Transactions + read_committed consumers**

---

## Internal Topics

| Topic | Purpose |
|-------|---------|
| `__consumer_offsets` | Stores consumer group offset commits (50 partitions, compacted) |
| `__transaction_state` | Tracks ongoing transactions for EOS |
| `__cluster_metadata` | KRaft mode cluster metadata (replaces ZooKeeper) |

---

## Common CLI Commands

```bash
# Create topic
kafka-topics.sh --create --topic orders --partitions 3 --replication-factor 2 --bootstrap-server localhost:9092

# List topics
kafka-topics.sh --list --bootstrap-server localhost:9092

# Describe topic (shows partitions, replicas, ISR)
kafka-topics.sh --describe --topic orders --bootstrap-server localhost:9092

# Produce messages
kafka-console-producer.sh --topic orders --bootstrap-server localhost:9092

# Consume messages (from beginning)
kafka-console-consumer.sh --topic orders --from-beginning --bootstrap-server localhost:9092

# Consumer groups — list & describe
kafka-consumer-groups.sh --list --bootstrap-server localhost:9092
kafka-consumer-groups.sh --describe --group my-group --bootstrap-server localhost:9092

# Reset offsets
kafka-consumer-groups.sh --group my-group --topic orders --reset-offsets --to-earliest --execute --bootstrap-server localhost:9092
```

---

## 🔥 Top 15 Interview Questions (Quick Answers)

| # | Question | Key Answer |
|---|----------|-----------|
| 1 | What is Kafka? | Distributed event streaming platform — publish, subscribe, store, process streams at scale. |
| 2 | Topic vs Partition? | Topic = logical name. Partition = physical ordered log. Topic split into partitions for parallelism. |
| 3 | How is ordering guaranteed? | Within a partition only. Use same key → same partition for ordering per entity. |
| 4 | What is a Consumer Group? | Group of consumers that divide partitions among themselves. Each partition → one consumer in the group. |
| 5 | What happens if consumers > partitions? | Extra consumers sit idle. Max parallelism = number of partitions. |
| 6 | What is ISR? | In-Sync Replicas — followers caught up with leader. Only ISR members can become new leader (by default). |
| 7 | How to prevent data loss? | `acks=all` + `min.insync.replicas=2` + replication factor ≥ 3. |
| 8 | What is `acks=all`? | Producer waits for ALL ISR replicas to acknowledge. Strongest durability guarantee. |
| 9 | Exactly-once semantics? | Idempotent producer + transactions + `read_committed` isolation on consumer. |
| 10 | What is log compaction? | Keeps only the **latest** value per key. Like a changelog table. |
| 11 | Kafka vs RabbitMQ? | Kafka = pull-based log, retains messages, high throughput. RabbitMQ = push-based queue, deletes after ACK, complex routing. |
| 12 | What is the Controller? | One special broker managing partition leadership, broker lifecycle. Elected via ZK/KRaft. |
| 13 | What is `__consumer_offsets`? | Internal compacted topic storing consumer group offset commits. |
| 14 | What is KRaft? | Kafka Raft — replaces ZooKeeper. Metadata stored in internal topic. Faster failover, more scalable. |
| 15 | How to scale Kafka? | More partitions = more parallelism. More brokers = more storage/throughput. More consumers (up to partition count). |

---

## Quick Reference

```
Broker         → Kafka server. Stores partitions on disk.
Topic          → Named stream of events.
Partition      → Ordered, immutable, append-only log segment of a topic.
Offset         → Unique ID per message within a partition.
Producer       → Writes events to topics.
Consumer       → Reads events from topics (pull-based).
Consumer Group → Consumers sharing partition load. Each partition → 1 consumer.
Replica        → Copy of partition on another broker.
Leader         → Replica handling all reads/writes.
ISR            → In-Sync Replicas — caught up followers eligible for leadership.
Controller     → One broker managing cluster state.
ZooKeeper      → External coordination (deprecated).
KRaft          → Kafka-native coordination (the future).
acks=all       → Wait for all ISR replicas. Strongest durability.
Compaction     → Keep latest value per key. Delete old duplicates.
Rebalance      → Partition reassignment when consumers join/leave.
EOS            → Exactly-once semantics via idempotence + transactions.
```

---

> **Bottom line:** Kafka is an **append-only distributed log** — not a traditional message queue. Messages are retained, consumers pull at their own pace, and ordering is per-partition. Master partitions, consumer groups, ISR, and `acks` — and you'll ace any Kafka interview.

