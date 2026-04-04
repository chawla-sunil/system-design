# 🐘 ZooKeeper & Kafka — Complete Deep Dive (Interview Edition)

> Before Kafka 3.3, you literally **could not run Kafka without ZooKeeper**.  
> Understanding this relationship is a top-tier interview topic for anyone who mentions Kafka on their resume.

---

## Table of Contents

### Part A — Kafka + ZooKeeper Relationship
1. [Quick Kafka Refresher](#1-quick-kafka-refresher)
2. [Why Did Kafka Need ZooKeeper?](#2-why-did-kafka-need-zookeeper)
3. [What ZooKeeper Stores for Kafka — The ZNode Tree](#3-what-zookeeper-stores-for-kafka--the-znode-tree)
4. [Broker Registration & Discovery](#4-broker-registration--discovery)
5. [Controller Election — The Brain of Kafka](#5-controller-election--the-brain-of-kafka)
6. [Topic & Partition Metadata](#6-topic--partition-metadata)
7. [ISR (In-Sync Replicas) Management](#7-isr-in-sync-replicas-management)
8. [Consumer Group Coordination (Legacy)](#8-consumer-group-coordination-legacy)
9. [The Full Picture — How It All Fits Together](#9-the-full-picture--how-it-all-fits-together)
10. [KRaft Mode — Kafka Without ZooKeeper](#10-kraft-mode--kafka-without-zookeeper)
11. [ZooKeeper vs KRaft — Detailed Comparison](#11-zookeeper-vs-kraft--detailed-comparison)
12. [Migration Path — ZooKeeper to KRaft](#12-migration-path--zookeeper-to-kraft)

### Part B — Kafka Core Internals (Deep Dive)
13. [Message Storage Internals — Segments, Indexes & Zero-Copy](#13-message-storage-internals--segments-indexes--zero-copy)
14. [Producer Internals — Batching, Partitioning & Idempotency](#14-producer-internals--batching-partitioning--idempotency)
15. [Replication Protocol — HW, LEO & Follower Fetch](#15-replication-protocol--hw-leo--follower-fetch)
16. [Partition Reassignment Strategy](#16-partition-reassignment-strategy)
17. [Consumer Rebalancing Protocols — Eager vs Cooperative](#17-consumer-rebalancing-protocols--eager-vs-cooperative)
18. [Exactly-Once Semantics (EOS) & Transactions](#18-exactly-once-semantics-eos--transactions)
19. [Log Compaction & Retention Policies](#19-log-compaction--retention-policies)
20. [Why Kafka Is So Fast — Performance Deep Dive](#20-why-kafka-is-so-fast--performance-deep-dive)
21. [Kafka Delivery Guarantees & Ordering](#21-kafka-delivery-guarantees--ordering)
22. [Kafka Connect & Kafka Streams (Overview)](#22-kafka-connect--kafka-streams-overview)

### Part C — Interview Ready
23. [Common Interview Questions](#23-common-interview-questions)
24. [Quick Reference Table](#24-quick-reference-table)

---

## 1. Quick Kafka Refresher

If you need a 30-second Kafka recap:

```
Producer ──→ [ Broker 1 ] ──→ Consumer
             [ Broker 2 ]      (in a Consumer Group)
             [ Broker 3 ]

Topic: "orders"
  ├── Partition 0  → Broker 1 (leader), Broker 2 (replica)
  ├── Partition 1  → Broker 2 (leader), Broker 3 (replica)
  └── Partition 2  → Broker 3 (leader), Broker 1 (replica)
```

| Concept | One-Liner |
|---------|-----------|
| **Broker** | A Kafka server. Stores messages on disk. |
| **Topic** | A logical channel (like a table name). |
| **Partition** | A topic is split into partitions for parallelism. Each partition is an ordered log. |
| **Replica** | Copy of a partition on another broker for fault tolerance. |
| **Leader** | The one replica that handles all reads/writes for a partition. |
| **ISR** | In-Sync Replicas — replicas that are caught up with the leader. |
| **Controller** | ONE special broker that manages partition leadership, broker lifecycle, etc. |
| **Consumer Group** | A group of consumers that divide partitions among themselves. |

---

## 2. Why Did Kafka Need ZooKeeper?

Kafka is a **distributed system** — multiple brokers, multiple partitions, replicas, consumers. Someone has to **coordinate** all of this. That "someone" was ZooKeeper.

### The Coordination Problems Kafka Faces:

| Problem | Why It's Hard | ZooKeeper's Role |
|---------|---------------|-----------------|
| Which brokers are alive? | Brokers can crash anytime | Broker registration via ephemeral znodes |
| Who is the controller? | Only ONE broker should be controller | Leader election via ephemeral znode |
| Which broker leads partition-0? | Must be a broker in the ISR | Controller reads/writes metadata in ZK |
| Topic created — where to put partitions? | Need to balance across brokers | Metadata stored in ZK |
| A broker died — who takes over its partitions? | Need to reassign leaders quickly | Controller watches ZK, triggers reassignment |
| Which consumer owns which partition? | Consumers join/leave dynamically | *(legacy)* Consumer offsets in ZK |

**Key insight:** Kafka brokers are **stateless** in terms of coordination — ALL coordination state lived in ZooKeeper.

---

## 3. What ZooKeeper Stores for Kafka — The ZNode Tree

Here's the actual ZooKeeper tree structure that Kafka creates:

```
/kafka                                  ← root (configurable with chroot)
├── /brokers
│   ├── /ids
│   │   ├── /0    → {"host":"broker1","port":9092,...}   ← ephemeral!
│   │   ├── /1    → {"host":"broker2","port":9092,...}   ← ephemeral!
│   │   └── /2    → {"host":"broker3","port":9092,...}   ← ephemeral!
│   ├── /topics
│   │   ├── /orders
│   │   │   └── /partitions
│   │   │       ├── /0
│   │   │       │   └── /state → {"leader":1,"isr":[1,2]}
│   │   │       ├── /1
│   │   │       │   └── /state → {"leader":2,"isr":[2,0]}
│   │   │       └── /2
│   │   │           └── /state → {"leader":0,"isr":[0,1]}
│   │   └── /payments
│   │       └── /partitions/...
│   └── /seqid                          ← broker ID sequence
├── /controller                         ← {"brokerid":1}  ← ephemeral!
├── /controller_epoch                   ← fencing token, prevents zombie controller
├── /admin
│   ├── /delete_topics                  ← topics pending deletion
│   └── /reassign_partitions            ← partition reassignment in progress
├── /config
│   ├── /topics                         ← per-topic config overrides
│   ├── /clients                        ← per-client config (quotas)
│   └── /brokers                        ← per-broker config overrides
├── /isr_change_notification            ← ISR change events
└── /log_dir_event_notification         ← log directory changes
```

**Interview Q:** *"What data does Kafka store in ZooKeeper?"*  
**A:** Broker registrations (ephemeral), controller election, topic and partition metadata (which broker leads which partition), ISR lists, topic configurations, ACLs, and admin operations like partition reassignment. Kafka does NOT store actual messages in ZooKeeper — only metadata.

---

## 4. Broker Registration & Discovery

When a Kafka broker starts up:

```
Broker 1 starts
    │
    ├── 1. Connects to ZooKeeper
    │
    ├── 2. Creates ephemeral znode:
    │      /kafka/brokers/ids/1 → {"host":"10.0.0.1","port":9092,"jmx_port":9999}
    │
    ├── 3. ZooKeeper confirms creation
    │
    └── 4. Broker is now "registered" and visible to the cluster
```

### What Happens When a Broker Crashes:

```
Broker 1 crashes 💥
    │
    ├── 1. ZooKeeper session expires (no heartbeats)
    │
    ├── 2. Ephemeral znode /kafka/brokers/ids/1 auto-deleted
    │
    ├── 3. The Controller broker (watching /brokers/ids) gets notified via watch
    │
    ├── 4. Controller sees: "Broker 1 is gone!"
    │
    ├── 5. Controller checks: which partitions had Broker 1 as leader?
    │
    ├── 6. For each affected partition:
    │      - Pick new leader from ISR (in-sync replicas)
    │      - Update /kafka/brokers/topics/{topic}/partitions/{p}/state in ZK
    │
    └── 7. Controller sends LeaderAndISR requests to affected brokers
           → They start serving the new partition leadership
```

**This entire process takes seconds** — and it's all coordinated through ZooKeeper.

---

## 5. Controller Election — The Brain of Kafka

The **Controller** is the most important broker in Kafka. There's exactly **one** at any time.

### What the Controller Does:

- Monitors broker liveness (watches `/brokers/ids`)
- Assigns partition leaders when brokers join/leave
- Handles topic creation/deletion
- Manages partition reassignment
- Updates metadata in ZooKeeper

### How Controller Election Works:

```
All brokers start up simultaneously:

Broker 0: tries to create /kafka/controller → SUCCESS! I'm the controller! ✅
Broker 1: tries to create /kafka/controller → ALREADY EXISTS. Sets watch. ❌
Broker 2: tries to create /kafka/controller → ALREADY EXISTS. Sets watch. ❌

/kafka/controller → {"brokerid": 0, "timestamp": "..."}  (ephemeral znode)
/kafka/controller_epoch → 1
```

### Controller Failover:

```
Controller (Broker 0) crashes 💥
    │
    ├── 1. /kafka/controller ephemeral znode deleted
    │
    ├── 2. Broker 1 and Broker 2 get watch notification
    │
    ├── 3. Both race to create /kafka/controller
    │      Broker 1: SUCCESS → New controller! ✅
    │      Broker 2: FAILED → Sets watch again ❌
    │
    ├── 4. /kafka/controller_epoch → 2 (incremented)
    │      This prevents the old controller (zombie) from making changes
    │      If old controller somehow sends commands with epoch=1,
    │      brokers reject them (they expect epoch=2)
    │
    └── 5. New controller (Broker 1) reads all metadata from ZK
           and rebuilds its in-memory state
```

**Interview Q:** *"What is the controller epoch and why is it needed?"*  
**A:** The controller epoch is a **fencing token** — a monotonically increasing number that increments with every new controller election. If the old controller had a temporary network issue and comes back thinking it's still the controller, its commands carry the old epoch number. All brokers compare the epoch and reject stale commands. This prevents **zombie controllers** from corrupting the cluster.

---

## 6. Topic & Partition Metadata

When you create a topic:

```bash
kafka-topics.sh --create --topic orders --partitions 3 --replication-factor 2
```

Here's what happens behind the scenes:

```
1. CLI sends request to any broker

2. Request forwarded to Controller

3. Controller decides partition assignment:
   Partition 0: Leader=Broker1, Replicas=[Broker1, Broker2]
   Partition 1: Leader=Broker2, Replicas=[Broker2, Broker0]
   Partition 2: Leader=Broker0, Replicas=[Broker0, Broker1]

4. Controller writes to ZooKeeper:
   /kafka/brokers/topics/orders → {"partitions":{"0":[1,2],"1":[2,0],"2":[0,1]}}

5. Controller writes partition state:
   /kafka/brokers/topics/orders/partitions/0/state → {"leader":1,"isr":[1,2]}
   /kafka/brokers/topics/orders/partitions/1/state → {"leader":2,"isr":[2,0]}
   /kafka/brokers/topics/orders/partitions/2/state → {"leader":0,"isr":[0,1]}

6. Controller sends LeaderAndISR requests directly to brokers
   → Brokers create log directories and start serving partitions
```

### Metadata Propagation:

```
                  ┌──────────────┐
                  │  ZooKeeper   │  ← Source of truth
                  └──────┬───────┘
                         │
                  ┌──────┴───────┐
                  │  Controller  │  ← Reads ZK, builds in-memory cache
                  └──────┬───────┘
                         │
            ┌────────────┼────────────┐
            │            │            │
      ┌─────┴─────┐ ┌───┴───┐ ┌─────┴─────┐
      │  Broker 0 │ │Broker1│ │  Broker 2 │  ← Get metadata from Controller
      └───────────┘ └───────┘ └───────────┘
            ↑            ↑            ↑
      ┌─────┴─────┐ ┌───┴───┐ ┌─────┴─────┐
      │ Producers │ │Consumers│ │  Clients  │  ← Get metadata from ANY broker
      └───────────┘ └─────────┘ └───────────┘
```

**Key insight:** Producers/consumers don't talk to ZooKeeper directly. They get metadata from brokers. The controller keeps brokers updated.

---

## 7. ISR (In-Sync Replicas) Management

The ISR list is critical for Kafka's durability guarantee.

### What is ISR?

```
Partition 0:
  Leader (Broker 1):  [msg1, msg2, msg3, msg4, msg5]    ← latest
  Replica (Broker 2): [msg1, msg2, msg3, msg4, msg5]    ← in sync ✅  → IN ISR
  Replica (Broker 3): [msg1, msg2, msg3]                 ← lagging ❌  → NOT in ISR
```

A replica is "in-sync" if it has replicated all messages within `replica.lag.time.max.ms` (default 30 seconds).

### How ISR Changes Flow Through ZooKeeper:

```
1. Leader Broker detects: Broker 3 is lagging behind

2. Leader updates ISR in ZooKeeper:
   /kafka/brokers/topics/orders/partitions/0/state
   Before: {"leader":1, "isr":[1,2,3]}
   After:  {"leader":1, "isr":[1,2]}      ← Broker 3 removed

3. Leader writes to /kafka/isr_change_notification/isr_change_XXX

4. Controller watches this path → gets notified

5. Controller updates its in-memory metadata

6. When Broker 3 catches up → Leader adds it back to ISR → writes to ZK again
```

### Why ISR Matters for Interviews:

| Scenario | What Happens |
|----------|-------------|
| `acks=all` + ISR=[1,2,3] | Producer write succeeds only after ALL 3 replicas ACK |
| `acks=all` + ISR=[1,2] | Producer write succeeds after 2 replicas ACK (Broker 3 is lagging, not in ISR) |
| Leader dies, ISR=[1,2] | Broker 2 (in ISR) becomes new leader. No data loss! |
| Leader dies, ISR=[1] (only leader) | If `unclean.leader.election.enable=true` → out-of-sync replica becomes leader → **data loss possible**. If `false` → partition goes offline until leader comes back. |

**Interview Q:** *"What's the difference between ISR and all replicas?"*  
**A:** All replicas is the total set of brokers that have a copy of the partition. ISR is the subset that is **caught up** with the leader. Only ISR members are eligible to become the new leader (by default). A replica drops out of ISR if it falls behind by more than `replica.lag.time.max.ms`.

---

## 8. Consumer Group Coordination (Legacy)

> ⚠️ **Important:** Since Kafka 0.9+ (2015), consumer offsets are stored in the internal `__consumer_offsets` topic, NOT in ZooKeeper. But this is still asked in interviews.

### Old Way (ZooKeeper-based, pre-0.9):

```
/kafka/consumers/
├── /my-consumer-group
│   ├── /ids
│   │   ├── consumer-1  (ephemeral)
│   │   └── consumer-2  (ephemeral)
│   ├── /offsets
│   │   └── /orders
│   │       ├── /0 → "12345"    ← consumer-1 has read up to offset 12345 in partition 0
│   │       └── /1 → "67890"    ← consumer-2 has read up to offset 67890 in partition 1
│   └── /owners
│       └── /orders
│           ├── /0 → "consumer-1"  ← consumer-1 owns partition 0
│           └── /1 → "consumer-2"  ← consumer-2 owns partition 1
```

### Why ZooKeeper Was Removed From Consumer Path:

| Problem | Details |
|---------|---------|
| **Write amplification** | Every consumer commit = ZK write. High-throughput consumers hammered ZK |
| **ZK not designed for high writes** | ZK is optimized for reads. Thousands of offset commits/sec overloaded it |
| **Session management overhead** | Consumer rebalances triggered through ZK watches were slow and noisy |
| **Tight coupling** | Every consumer client needed ZK connection string |

### New Way (Kafka-native, 0.9+):

```
Consumer → Sends offsets to Group Coordinator (a Kafka broker)
        → Stored in __consumer_offsets internal topic (compacted)
        → Rebalancing handled by Kafka's Group Protocol

ZooKeeper is NO LONGER involved in consumer operations.
```

---

## 9. The Full Picture — How It All Fits Together

Here's the complete flow showing ZooKeeper's role in Kafka:

```
                          ┌─────────────────────┐
                          │     ZooKeeper        │
                          │  Ensemble (3 nodes)  │
                          └──────────┬──────────┘
                                     │
                 Stores:             │          Watches:
                 - Broker IDs        │          - Controller watches /brokers/ids
                 - Controller        │          - Controller watches /brokers/topics
                 - Topic metadata    │          - Brokers watch /controller
                 - Partition state   │
                 - ISR lists         │
                 - Configs           │
                 - ACLs              │
                                     │
        ┌────────────────────────────┼─────────────────────────────┐
        │                            │                             │
   ┌────┴────┐                 ┌─────┴────┐                 ┌─────┴────┐
   │ Broker 0│                 │ Broker 1 │                 │ Broker 2 │
   │         │                 │(Controller)│                │          │
   └────┬────┘                 └─────┬────┘                 └─────┬────┘
        │                            │                             │
        │         ┌──────────────────┼──────────────────┐          │
        │         │                  │                  │          │
   ┌────┴────┐  ┌─┴──────┐   ┌──────┴───┐    ┌────────┴┐   ┌────┴────┐
   │  P0-R   │  │  P0-L  │   │  P1-L    │    │  P1-R   │   │  P2-L  │
   │(replica)│  │(leader)│   │(leader)  │    │(replica)│   │(leader)│
   └─────────┘  └────────┘   └──────────┘    └─────────┘   └────────┘

   P0 = Partition 0, L = Leader, R = Replica

        ↑                            ↑                             ↑
   ┌────┴────┐                 ┌─────┴────┐                 ┌─────┴────┐
   │Producer │                 │ Consumer │                 │ Consumer │
   │         │                 │ Group A  │                 │ Group B  │
   └─────────┘                 └──────────┘                 └──────────┘
```

### Startup Sequence (Interview Gold):

```
1. ZooKeeper ensemble starts first (must be running)
2. Broker 0 starts → registers /brokers/ids/0 (ephemeral)
3. Broker 0 tries to create /controller → SUCCESS → Broker 0 is controller
4. Broker 1 starts → registers /brokers/ids/1 (ephemeral)
5. Broker 1 tries to create /controller → FAIL → watches it
6. Broker 2 starts → registers /brokers/ids/2 (ephemeral)
7. Controller (Broker 0) sees new brokers via watches
8. User creates topic → Controller assigns partitions, writes to ZK
9. Producers/consumers get metadata from brokers → start producing/consuming

ZooKeeper is the FIRST thing that must be up, and the LAST thing to shut down.
```

---

## 10. KRaft Mode — Kafka Without ZooKeeper

Starting with Kafka 3.3 (October 2022), Kafka can run **without ZooKeeper** using **KRaft** (Kafka Raft).

### What is KRaft?

KRaft replaces ZooKeeper with Kafka's **own consensus protocol** based on Raft. Metadata is now stored as an **internal Kafka topic** (`__cluster_metadata`) instead of in ZooKeeper.

```
Before (ZooKeeper mode):                 After (KRaft mode):
┌──────────┐    ┌──────────┐             ┌──────────┐
│ ZooKeeper│    │ ZooKeeper│             │  Gone!   │
│ Server 1 │    │ Server 2 │             │          │
└────┬─────┘    └────┬─────┘             └──────────┘
     │               │
┌────┴───┐ ┌────────┴┐ ┌────────┐       ┌──────────┐ ┌──────────┐ ┌──────────┐
│Broker 0│ │ Broker 1│ │Broker 2│       │ Broker 0 │ │ Broker 1 │ │ Broker 2 │
└────────┘ └─────────┘ └────────┘       │(voter)   │ │(voter)   │ │(voter)   │
                                         │+Controller│ │          │ │          │
5 processes total                        └──────────┘ └──────────┘ └──────────┘
                                         3 processes total (or separate controller nodes)
```

### How KRaft Works:

```
1. Some brokers are designated as "controllers" (voters in Raft quorum)
2. Controllers elect a leader among themselves using Raft consensus
3. The active controller stores all metadata in __cluster_metadata topic
4. Metadata is replicated to other controllers via Raft
5. Brokers fetch metadata from the active controller (like log replication)
6. No external dependency — everything is inside Kafka!
```

### KRaft Deployment Modes:

| Mode | Description | When to Use |
|------|-------------|-------------|
| **Combined** | Brokers and controllers run in the same process | Small clusters (dev, testing, <10 brokers) |
| **Isolated** | Controllers are dedicated nodes, separate from brokers | Production (large clusters) |

```
Combined Mode:                    Isolated Mode:
┌──────────────┐                  ┌────────────┐ ┌────────────┐ ┌────────────┐
│ Broker 0     │                  │Controller 0│ │Controller 1│ │Controller 2│
│ + Controller │                  │ (voter)    │ │ (voter)    │ │ (voter)    │
├──────────────┤                  └────────────┘ └────────────┘ └────────────┘
│ Broker 1     │                         │              │              │
│ + Controller │                  ┌──────┴──────┐ ┌────┴────┐ ┌──────┴──────┐
├──────────────┤                  │  Broker 0   │ │ Broker 1│ │  Broker 2   │
│ Broker 2     │                  └─────────────┘ └─────────┘ └─────────────┘
│ + Controller │
└──────────────┘
```

---

## 11. ZooKeeper vs KRaft — Detailed Comparison

| Aspect | ZooKeeper Mode | KRaft Mode |
|--------|---------------|------------|
| **External dependency** | Yes — need ZooKeeper cluster | No — self-contained |
| **Operational complexity** | High — manage 2 systems | Low — manage 1 system |
| **Number of processes** | Kafka + ZK (e.g., 3+3 = 6) | Just Kafka (e.g., 3 or 3+3) |
| **Metadata storage** | ZooKeeper znodes (tree) | `__cluster_metadata` topic (log) |
| **Consensus protocol** | ZAB | Raft |
| **Controller election** | Via ZK ephemeral znode | Via Raft leader election |
| **Metadata propagation** | Controller pushes to brokers | Brokers pull from metadata log |
| **Partition limit** | ~200K (ZK becomes bottleneck) | Millions (metadata is a log) |
| **Controller failover** | Seconds (new controller must reload from ZK) | Milliseconds (new controller already has metadata in memory) |
| **Startup time** | Slow (read all metadata from ZK) | Fast (metadata already local) |
| **Split-brain protection** | ZK quorum + controller epoch | Raft epoch (fencing) |
| **Maturity** | Battle-tested (10+ years) | Production-ready since Kafka 3.3 (2022) |
| **Future** | Deprecated (will be removed) | The future of Kafka |

### The #1 Reason: Scalability

```
With ZooKeeper:
  Creating 100,000 partitions takes minutes
  → ZK writes are slow (disk + quorum for each znode update)
  → Controller must read ALL metadata from ZK on failover

With KRaft:
  Creating 100,000 partitions takes seconds
  → Metadata is a compacted log (sequential writes — fast!)
  → Controller failover is instant (already has metadata in memory)
```

---

## 12. Migration Path — ZooKeeper to KRaft

This is becoming a practical interview question as companies migrate.

### Migration Steps:

```
Phase 1: Running with ZooKeeper
  ┌──────────┐
  │ZooKeeper │ ←── Kafka reads/writes metadata here
  └────┬─────┘
       │
  ┌────┴────┐
  │  Kafka  │
  │ Brokers │
  └─────────┘

Phase 2: Migration Mode (both running)
  ┌──────────┐     ┌──────────────┐
  │ZooKeeper │ ←─→ │ KRaft        │
  └────┬─────┘     │ Controllers  │
       │           └──────┬───────┘
  ┌────┴──────────────────┴────┐
  │       Kafka Brokers        │
  │  (dual-write: ZK + KRaft) │
  └────────────────────────────┘

Phase 3: KRaft Only
  ┌──────────────┐
  │    KRaft     │ ←── All metadata here now
  │ Controllers  │
  └──────┬───────┘
         │
  ┌──────┴───────┐
  │ Kafka Brokers│
  └──────────────┘
  
  ZooKeeper decommissioned 🎉
```

### Migration Commands:
```bash
# Step 1: Format the metadata directory
kafka-storage.sh format -t <cluster-id> -c kraft-controller.properties

# Step 2: Start KRaft controllers alongside ZK
# (configure brokers to talk to both)

# Step 3: Migrate metadata from ZK to KRaft
kafka-metadata.sh --snapshot /path/to/snapshot --cluster-id <id>

# Step 4: Switch brokers to KRaft mode
# Update server.properties: remove zookeeper.connect, add controller.quorum.voters

# Step 5: Shut down ZooKeeper
```

---

---

# Part B — Kafka Core Internals (Deep Dive)

---

## 13. Message Storage Internals — Segments, Indexes & Zero-Copy

Understanding HOW Kafka stores messages on disk is one of the most impressive things you can explain in an interview.

### The Log Directory Structure

Each partition is a **directory** on the broker's filesystem. Inside it, data is split into **segments**.

```
/var/kafka-logs/
├── orders-0/                          ← Topic "orders", Partition 0
│   ├── 00000000000000000000.log       ← Segment file (messages from offset 0)
│   ├── 00000000000000000000.index     ← Offset index
│   ├── 00000000000000000000.timeindex ← Timestamp index
│   ├── 00000000000005242880.log       ← Next segment (starts at offset 5242880)
│   ├── 00000000000005242880.index
│   ├── 00000000000005242880.timeindex
│   ├── 00000000000010485760.log       ← Active segment (being written to)
│   ├── 00000000000010485760.index
│   ├── 00000000000010485760.timeindex
│   └── leader-epoch-checkpoint
├── orders-1/                          ← Topic "orders", Partition 1
│   └── ...
└── __consumer_offsets-23/             ← Internal topic partition
    └── ...
```

**Key insight:** The filename IS the base offset of the first message in that segment.

### Segment Anatomy

```
Segment File (.log):
┌─────────────────────────────────────────────────────┐
│ Record Batch 1                                       │
│ ┌───────────┬──────┬───────────┬──────┬────────────┐│
│ │ Offset: 0 │ Size │ Timestamp │ Key  │ Value      ││
│ └───────────┴──────┴───────────┴──────┴────────────┘│
│ Record Batch 2                                       │
│ ┌───────────┬──────┬───────────┬──────┬────────────┐│
│ │ Offset: 1 │ Size │ Timestamp │ Key  │ Value      ││
│ └───────────┴──────┴───────────┴──────┴────────────┘│
│ ...                                                  │
│ Record Batch N                                       │
│ ┌─────────────┬──────┬───────────┬──────┬──────────┐│
│ │ Offset: N-1 │ Size │ Timestamp │ Key  │ Value    ││
│ └─────────────┴──────┴───────────┴──────┴──────────┘│
└─────────────────────────────────────────────────────┘

Size: up to segment.bytes (default 1GB)
```

### How Kafka Finds a Message by Offset

This is a **two-step binary search** — incredibly efficient:

```
Consumer requests: "Give me messages starting at offset 7,500,000"

Step 1: Find the right SEGMENT
  Files: [0.log, 5242880.log, 10485760.log]
  Binary search on filenames:
  → 7,500,000 falls between 5242880 and 10485760
  → Open segment: 00000000000005242880.log

Step 2: Find the right POSITION within segment using .index file
  The .index file maps: offset → physical byte position in .log

  .index file (sparse — not every offset):
  ┌──────────────┬────────────────┐
  │ Offset Delta │ Position (bytes)│
  ├──────────────┼────────────────┤
  │     0        │      0         │   (offset 5242880 → byte 0)
  │   4096       │   131072       │   (offset 5246976 → byte 131072)
  │   8192       │   262144       │   (offset 5251072 → byte 262144)
  │  ...         │   ...          │
  └──────────────┴────────────────┘

  Binary search in .index:
  → Target: 7500000 - 5242880 = delta 2257120
  → Closest entry ≤ delta: e.g., position at byte X
  → Seek to byte X in .log file
  → Scan forward linearly until offset 7,500,000

Total disk seeks: 2 (one for segment file, one within .index)
```

### Timestamp Index (.timeindex)

```
Same idea, but maps: timestamp → offset

Use case: "Give me all messages after 2024-01-15 10:30:00"
  1. Binary search .timeindex → find offset for that timestamp
  2. Binary search .index → find byte position for that offset
  3. Read from that position
```

### Segment Lifecycle

```
┌──────────────┐    Segment reaches 1GB     ┌──────────────┐
│ Active Segment│ ─── (or time limit) ──────→│ Closed/Sealed│
│ (being written)│                            │ (read-only)  │
└──────────────┘                             └──────┬───────┘
                                                     │
                                        After retention period
                                                     │
                                                     ▼
                                             ┌──────────────┐
                                             │   Deleted     │
                                             │  (or compacted)│
                                             └──────────────┘

Key configs:
  log.segment.bytes = 1073741824    (1 GB — when to roll new segment)
  log.segment.ms    = 604800000     (7 days — time-based roll)
  log.retention.hours = 168         (7 days — when to delete)
  log.retention.bytes = -1          (no size limit by default)
```

### Zero-Copy Transfer (sendfile)

This is WHY Kafka is so fast at reads:

```
Traditional data transfer (4 copies):
  Disk → Kernel Buffer → User Buffer (app) → Socket Buffer → NIC

  1. read() syscall: Disk → Kernel Page Cache → User Space
  2. write() syscall: User Space → Socket Buffer → NIC
  Total: 4 copies, 2 syscalls, 2 context switches

Kafka's zero-copy (sendfile syscall):
  Disk → Kernel Buffer → NIC     (that's it!)

  1. sendfile() syscall: Kernel Page Cache → NIC (via DMA)
  Total: 0 copies to user space, 1 syscall
  
  ┌──────┐     ┌───────────────┐     ┌─────┐
  │ Disk │ ──→ │ Kernel Page   │ ──→ │ NIC │  ← DMA (Direct Memory Access)
  │      │     │ Cache         │     │     │
  └──────┘     └───────────────┘     └─────┘
                  (no user space copy!)
```

**Interview Q:** *"Why is Kafka so fast at serving consumers?"*  
**A:** Kafka uses `sendfile()` (zero-copy) to transfer data directly from the OS page cache to the network socket without copying data into user space. Combined with sequential disk I/O and the OS page cache acting as a read-ahead buffer, this makes consumer reads extremely fast — often served entirely from memory.

---

## 14. Producer Internals — Batching, Partitioning & Idempotency

### Producer Architecture

```
                           KafkaProducer (Thread-Safe)
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  Your Thread(s)                                                      │
│  ┌────────────┐   ┌────────────────┐   ┌──────────────────────────┐ │
│  │ serialize() │──→│ partitioner()  │──→│  RecordAccumulator       │ │
│  │ key + value │   │ choose partition│   │  (per-partition batches) │ │
│  └────────────┘   └────────────────┘   │                          │ │
│                                         │  Topic-A, P0: [batch]   │ │
│                                         │  Topic-A, P1: [batch]   │ │
│                                         │  Topic-B, P0: [batch]   │ │
│                                         └──────────┬───────────────┘ │
│                                                     │                │
│  Sender Thread (background, single)                 │                │
│  ┌──────────────────────────────────────────────────┘                │
│  │                                                                   │
│  │  1. Drain ready batches                                           │
│  │  2. Group by destination broker (node)                            │
│  │  3. Create ProduceRequest per broker                              │
│  │  4. Send via NetworkClient (NIO Selector)                         │
│  │  5. Handle response → complete futures / retry                    │
│  └───────────────────────────────────────────────────────────────────┘
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Partitioning Strategy

How does the producer decide WHICH partition a message goes to?

```java
// Default partitioner logic (Kafka 3.x+):
if (record.partition() != null) {
    return record.partition();               // 1. Explicit partition
} else if (record.key() != null) {
    return murmur2(key) % numPartitions;     // 2. Key-based (deterministic)
} else {
    return stickyPartition();                // 3. Sticky partitioner (no key)
}
```

| Strategy | When | Behavior | Ordering Guarantee |
|----------|------|----------|-------------------|
| **Explicit partition** | `partition` set in ProducerRecord | Goes to that exact partition | Full ordering in that partition |
| **Key-based** | Key is non-null | `murmur2(key) % numPartitions` | Same key → same partition → ordered |
| **Sticky** (default, no key) | Key is null | Sticks to one partition until batch is full, then picks another | No ordering across partitions |
| **Round-robin** (old default) | Key is null, old Kafka | Rotate through partitions one message at a time | No ordering |
| **Custom** | Implement `Partitioner` | Your logic | Depends on implementation |

**Why Sticky > Round-Robin:**
```
Round-Robin (old, key=null):
  msg1 → P0  (batch for P0 has 1 msg)
  msg2 → P1  (batch for P1 has 1 msg)
  msg3 → P2  (batch for P2 has 1 msg)
  msg4 → P0  (batch for P0 has 2 msgs)
  → 3 small batches sent = 3 network requests = slow

Sticky Partitioner (new, key=null):
  msg1 → P0  (batch for P0 has 1 msg)
  msg2 → P0  (batch for P0 has 2 msgs)
  msg3 → P0  (batch for P0 has 3 msgs)
  msg4 → P0  (batch for P0 has 4 msgs — FULL!)
  msg5 → P1  (switch to P1)
  → 1 large batch sent = 1 network request = fast!
```

### Batching & Compression

```
Key Producer Configs:

batch.size = 16384           (16 KB — max batch size per partition)
linger.ms = 0                (how long to wait before sending — 0 = send immediately)
buffer.memory = 33554432     (32 MB — total memory for all batches)
compression.type = none      (options: none, gzip, snappy, lz4, zstd)

Typical production tuning:
  linger.ms = 5              (wait 5ms to fill batches → better throughput)
  batch.size = 65536          (64 KB batches)
  compression.type = lz4      (best speed/ratio tradeoff)
```

```
Without batching (linger.ms=0):
  msg1 ──→ [ProduceRequest with 1 msg] ──→ Broker
  msg2 ──→ [ProduceRequest with 1 msg] ──→ Broker
  msg3 ──→ [ProduceRequest with 1 msg] ──→ Broker
  → 3 network round-trips

With batching (linger.ms=5):
  msg1 ─┐
  msg2 ─┼─→ [ProduceRequest with 3 msgs, compressed] ──→ Broker
  msg3 ─┘
  → 1 network round-trip, compressed = way less bandwidth
```

### Producer Acknowledgments (acks) — Deep Dive

```
acks=0  "Fire and Forget"
  Producer ──→ Broker (leader)
  Producer doesn't wait. Moves on.
  Risk: message lost if broker crashes before writing.
  Throughput: MAXIMUM
  Latency: MINIMUM

acks=1  "Leader Acknowledged" (default)
  Producer ──→ Broker (leader) writes to local log ──→ ACK back to producer
  Risk: message lost if leader crashes BEFORE replicas fetch it.
  Throughput: HIGH
  Latency: LOW

acks=all (or -1)  "All In-Sync Replicas Acknowledged"
  Producer ──→ Broker (leader) writes to local log
                    ↓
               Follower 1 fetches → writes → ack
               Follower 2 fetches → writes → ack
                    ↓
               Leader: "All ISR replicas have it!" ──→ ACK back to producer
  Risk: NO data loss (as long as min.insync.replicas is met)
  Throughput: LOWER
  Latency: HIGHER

  Critical companion config:
  min.insync.replicas = 2    ← with acks=all, at least 2 replicas must ACK
                                If ISR drops below 2 → producer gets NotEnoughReplicasException
```

**The Gold Standard for Durability:**
```
acks = all
min.insync.replicas = 2
replication.factor = 3

This means: 3 copies exist, at least 2 must ACK, tolerates 1 broker failure
             with ZERO data loss guaranteed.
```

### Idempotent Producer

```
Problem: Network timeout during produce
  Producer sends msg → Network timeout → Producer retries → Broker got BOTH!
  Result: DUPLICATE message in the log!

Solution: Idempotent Producer (enable.idempotence=true, default since Kafka 3.0)

How it works:
  1. Each producer gets a unique Producer ID (PID) from the broker
  2. Each message gets a sequence number (per partition)
  3. Broker tracks: {PID, Partition} → last sequence number

  Producer sends: (PID=5, Partition=0, Seq=42, msg="order-123")
  Broker writes it. Stores: last_seq[PID=5, P=0] = 42

  Producer retries: (PID=5, Partition=0, Seq=42, msg="order-123")
  Broker checks: "Seq 42? I already have 42. DUPLICATE!" → Silently ignores
  → Returns success to producer (no duplicate in log)

  Guarantees:
  ✅ No duplicates (per partition)
  ✅ In-order delivery (per partition) — even with retries
  ✅ Automatic — just set enable.idempotence=true
```

### Retries & Delivery Ordering

```
Without idempotence (max.in.flight.requests.per.connection > 1):

  Batch 1 (offsets 100-110) ──→ FAIL (timeout)
  Batch 2 (offsets 111-120) ──→ SUCCESS ✅
  Batch 1 retry              ──→ SUCCESS ✅
  
  Log order: [111-120, 100-110]  ← OUT OF ORDER!

With idempotence:
  max.in.flight.requests.per.connection is limited to 5
  Broker rejects out-of-order sequence numbers
  
  Batch 1 (seq 0-10) ──→ FAIL
  Batch 2 (seq 11-20) ──→ Broker queues, waits for seq 0-10 first
  Batch 1 retry (seq 0-10) ──→ SUCCESS
  Batch 2 ──→ SUCCESS
  
  Log order: [0-10, 11-20]  ← IN ORDER! ✅
```

---

## 15. Replication Protocol — HW, LEO & Follower Fetch

This is THE most important Kafka internal to understand for senior interviews.

### Key Concepts

```
LEO (Log End Offset): The offset of the NEXT message to be written.
                       Each replica tracks its own LEO.

HW (High Watermark):  The offset up to which ALL ISR replicas have replicated.
                       Consumers can only read up to HW.
                       Only the leader maintains HW.

Committed Message:     A message at offset < HW. It's "safe" — won't be lost.
```

### Visual Example

```
Replication Factor = 3, ISR = [Broker0 (leader), Broker1, Broker2]

Time T1: Producer sends messages 0-4 to leader

  Broker 0 (Leader):    [msg0, msg1, msg2, msg3, msg4]  LEO=5, HW=0
  Broker 1 (Follower):  []                               LEO=0
  Broker 2 (Follower):  []                               LEO=0

  Consumer can read: NOTHING (HW=0)

Time T2: Follower 1 fetches, Follower 2 partially fetches

  Broker 0 (Leader):    [msg0, msg1, msg2, msg3, msg4]  LEO=5, HW=0
  Broker 1 (Follower):  [msg0, msg1, msg2, msg3, msg4]  LEO=5
  Broker 2 (Follower):  [msg0, msg1, msg2]              LEO=3

  HW = min(LEO of all ISR) = min(5, 5, 3) = 3
  
  Leader updates HW to 3:
  Broker 0 (Leader):    [msg0, msg1, msg2, msg3, msg4]  LEO=5, HW=3

  Consumer can read: msg0, msg1, msg2 (offsets 0, 1, 2)
  msg3 and msg4 are NOT visible to consumers yet!

Time T3: Follower 2 catches up

  Broker 0 (Leader):    [msg0, msg1, msg2, msg3, msg4]  LEO=5, HW=5
  Broker 1 (Follower):  [msg0, msg1, msg2, msg3, msg4]  LEO=5
  Broker 2 (Follower):  [msg0, msg1, msg2, msg3, msg4]  LEO=5

  Consumer can now read: ALL messages (offsets 0-4)
```

### How Followers Fetch

```
Followers are essentially consumers of the leader!

Follower → sends FetchRequest to Leader:
  "I need data starting from offset X"

Leader responds:
  - Messages from offset X onwards
  - Current HW

Follower:
  1. Appends messages to its local log
  2. Updates its LEO
  3. Updates its local HW = min(leader's HW, own LEO)

Leader tracks:
  - Each follower's LEO (updated when follower fetches)
  - HW = min(LEO of all ISR members)
  - HW only advances when ALL ISR replicas have caught up

Fetch loop (simplified):
┌──────────────────────────────────────────────────────────┐
│  Follower                              Leader            │
│                                                          │
│  FetchRequest(offset=0) ──────────→                      │
│                          ←────────── messages[0..100]    │
│  Write to local log                   + HW=0            │
│  LEO = 101                                               │
│                                                          │
│  FetchRequest(offset=101) ─────────→                     │
│                           ←────────── messages[101..200] │
│  Write to local log                   + HW=101          │
│  LEO = 201                                               │
│                                                          │
│  ... continues forever ...                               │
└──────────────────────────────────────────────────────────┘
```

### Leader Epoch (Preventing Log Divergence)

```
Problem: Leader A crashes after writing msg5. Follower B becomes leader.
         New leader B writes msg5' (different message at same offset!).
         Old leader A comes back. Who is right?

Solution: Leader Epoch — a monotonically increasing number for each new leader.

  Epoch 0: Leader=A → writes [msg0..msg4] at offsets 0-4
  A crashes. B becomes leader.
  Epoch 1: Leader=B → writes [msg5'..msg8'] at offsets 5-8

  A comes back online:
  A: "My last message was at offset 4, epoch 0"
  B: "Epoch 0 ended at offset 5. You need to truncate anything after offset 4"
  A: truncates → fetches from B → data is consistent

  leader-epoch-checkpoint file:
  ┌───────┬──────────────┐
  │ Epoch │ Start Offset │
  ├───────┼──────────────┤
  │   0   │      0       │
  │   1   │      5       │
  └───────┴──────────────┘
```

**Interview Q:** *"How does Kafka prevent data divergence after a leader failover?"*  
**A:** Through **Leader Epochs**. Each new leader increments the epoch. When a failed broker comes back, it asks the current leader "where did my epoch end?" and truncates its log to that point before re-syncing. This prevents log divergence where the same offset could have different messages on different replicas.

---

## 16. Partition Reassignment Strategy

Partition reassignment is one of the most operationally critical Kafka tasks. It involves moving partition replicas between brokers.

### When Do You Need Partition Reassignment?

| Scenario | Why Reassignment Needed |
|----------|------------------------|
| **New broker added** | Existing partitions don't auto-migrate. New broker sits idle! |
| **Broker decommission** | Must move partitions off the broker before removing it |
| **Uneven load** | Some brokers have more partitions/leaders → hot spots |
| **Rack awareness** | Ensure replicas are spread across different racks/AZs |
| **Disk rebalancing** | Move partitions between log directories on the same broker |
| **Increasing replication factor** | Need to add replicas on new brokers |

### The Reassignment Process — Step by Step

```
Goal: Move Partition 0 from [Broker 0, Broker 1] → [Broker 1, Broker 2]

Step 1: Generate reassignment plan (or create manually)
┌───────────────────────────────────────┐
│  Reassignment JSON:                    │
│  {                                     │
│    "partitions": [{                    │
│      "topic": "orders",               │
│      "partition": 0,                   │
│      "replicas": [1, 2]  ← new set    │
│    }]                                  │
│  }                                     │
└───────────────────────────────────────┘

Step 2: Submit to Controller

Step 3: Controller orchestrates the reassignment:

  Phase A: Add new replicas (expanding the replica set)
  ┌────────────────────────────────────────────────────┐
  │  Current:  Partition 0 → Replicas: [Broker0, Broker1]        │
  │  Target:   Partition 0 → Replicas: [Broker1, Broker2]        │
  │                                                    │
  │  Intermediate state (adding Broker2 as new replica):         │
  │  Partition 0 → Replicas: [Broker0, Broker1, Broker2]         │
  │                                  (Broker2 starts fetching    │
  │                                   data from leader)          │
  └────────────────────────────────────────────────────┘

  Phase B: New replica catches up
  ┌────────────────────────────────────────────────────┐
  │  Broker 2 fetches all data from leader              │
  │  Once caught up → added to ISR                     │
  │  ISR: [Broker0, Broker1, Broker2]                  │
  └────────────────────────────────────────────────────┘

  Phase C: Remove old replicas (shrinking replica set)
  ┌────────────────────────────────────────────────────┐
  │  Remove Broker 0 from replica set                  │
  │  Partition 0 → Replicas: [Broker1, Broker2]        │
  │  If Broker 0 was leader → elect new leader         │
  │  Broker 0 deletes local partition data              │
  └────────────────────────────────────────────────────┘

  Final state:
  Partition 0 → Leader: Broker1, Replicas: [Broker1, Broker2] ✅
```

### Commands for Partition Reassignment

```bash
# Step 1: Generate a reassignment plan
# Create topics-to-move.json:
{
  "topics": [
    {"topic": "orders"},
    {"topic": "payments"}
  ],
  "version": 1
}

# Generate plan (Kafka suggests reassignment to brokers 1,2,3):
kafka-reassign-partitions.sh \
  --bootstrap-server localhost:9092 \
  --topics-to-move-json-file topics-to-move.json \
  --broker-list "1,2,3" \
  --generate

# Output: Current assignment + Proposed assignment JSON

# Step 2: Execute the reassignment
kafka-reassign-partitions.sh \
  --bootstrap-server localhost:9092 \
  --reassignment-json-file reassignment.json \
  --execute

# Step 3: Verify progress
kafka-reassign-partitions.sh \
  --bootstrap-server localhost:9092 \
  --reassignment-json-file reassignment.json \
  --verify

# Output: "Reassignment of partition orders-0 is complete"
```

### Throttling Reassignment (Critical for Production!)

```bash
# Problem: Reassignment copies data between brokers.
# Without throttling, it can saturate the network and impact live traffic!

# Set throttle to 50 MB/s:
kafka-reassign-partitions.sh \
  --bootstrap-server localhost:9092 \
  --reassignment-json-file reassignment.json \
  --execute \
  --throttle 50000000    # 50 MB/s

# How throttling works:
# Controller sets inter-broker replication throttle:
#   leader.replication.throttled.rate = 50000000
#   follower.replication.throttled.rate = 50000000
#
# These are per-broker limits.
# Reassignment data transfer is rate-limited.
# Normal replication for non-reassigning partitions is NOT affected.

# Remove throttle after reassignment completes:
kafka-reassign-partitions.sh \
  --bootstrap-server localhost:9092 \
  --reassignment-json-file reassignment.json \
  --verify
# (--verify automatically removes throttle when complete)
```

### Preferred Leader Election

After reassignment, the first replica in the list is the **preferred leader**. But the current leader might be a different broker.

```
Example after reassignment:
  Partition 0: Replicas [2, 1], Current Leader: 1

  Broker 2 is "preferred leader" (first in replica list) but not actual leader.
  This causes uneven leader distribution!

Solution: Trigger preferred leader election

kafka-leader-election.sh \
  --bootstrap-server localhost:9092 \
  --election-type preferred \
  --topic orders \
  --partition 0

# Or for ALL partitions:
kafka-leader-election.sh \
  --bootstrap-server localhost:9092 \
  --election-type preferred \
  --all-topic-partitions

# auto.leader.rebalance.enable=true (default)
#   → Controller periodically checks and triggers preferred election automatically
#   → leader.imbalance.check.interval.seconds = 300 (5 min)
#   → leader.imbalance.per.broker.percentage = 10 (trigger if >10% imbalanced)
```

### Reassignment Strategies Comparison

| Strategy | How It Works | When to Use |
|----------|-------------|-------------|
| **kafka-reassign-partitions.sh --generate** | Kafka auto-generates a balanced plan | Adding/removing brokers. Quick and easy. |
| **Manual JSON** | You craft the exact replica assignment | Fine-grained control. Rack-aware placement. |
| **Cruise Control (LinkedIn)** | Automated tool that continuously monitors and rebalances | Large production clusters. Hands-off operations. |
| **Confluent Auto Data Balancer** | Commercial tool from Confluent | Enterprise Confluent Platform users. |
| **Strimzi Cruise Control** | Kubernetes-native rebalancing for Kafka on K8s | Kafka on Kubernetes. |

### Rack-Aware Reassignment

```
Goal: Ensure replicas of the same partition are in DIFFERENT racks/AZs

Configuration (in server.properties):
  broker.rack=us-east-1a    # Broker 0
  broker.rack=us-east-1b    # Broker 1
  broker.rack=us-east-1c    # Broker 2

When Kafka creates/reassigns partitions with rack awareness:
  Topic "orders", replication-factor=3:
  
  ✅ Good (replicas in different racks):
  Partition 0: [Broker0 (1a), Broker1 (1b), Broker2 (1c)]
  
  ❌ Bad (replicas in same rack):
  Partition 0: [Broker0 (1a), Broker3 (1a), Broker1 (1b)]
  → If rack 1a goes down, 2 of 3 replicas are lost!

Kafka's algorithm:
  1. Sort brokers by rack in round-robin
  2. Assign replicas alternating across racks
  3. Shift starting broker for each partition to spread leaders
```

### What Happens During Reassignment — Impact on Clients

```
┌──────────────────────────────────────────────────────────────┐
│  During reassignment:                                         │
│                                                               │
│  Producers:  ✅ Unaffected (leader doesn't change until end)  │
│  Consumers:  ✅ Unaffected (reading from current leader)      │
│  Bandwidth:  ⚠️  Increased (data copying between brokers)     │
│  Disk I/O:   ⚠️  Increased (new replica writing data)         │
│  Latency:    ⚠️  May increase slightly (resource contention)  │
│                                                               │
│  During leader change (end of reassignment):                  │
│  Producers:  ⚡ Brief pause (metadata refresh, ~seconds)      │
│  Consumers:  ⚡ Brief pause (metadata refresh, ~seconds)      │
│                                                               │
│  Best practice: Do reassignment during low-traffic periods    │
│                 with throttling enabled!                       │
└──────────────────────────────────────────────────────────────┘
```

**Interview Q:** *"You added 3 new brokers to a Kafka cluster. How do you rebalance partitions?"*  
**A:** New brokers don't automatically get partitions. You need to:
1. Use `kafka-reassign-partitions.sh --generate` with the new broker list to get a proposed plan
2. Review the plan (check for rack awareness)
3. Execute with `--throttle` to avoid saturating the network
4. Monitor with `--verify` until complete
5. Run preferred leader election to balance leadership
6. Optionally use Cruise Control for automated, ongoing rebalancing

**Interview Q:** *"What is Cruise Control and when would you use it?"*  
**A:** Cruise Control (open-sourced by LinkedIn) is an automated Kafka cluster management tool. It continuously monitors broker load (CPU, disk, network, partition count), computes optimal partition assignments, and can automatically rebalance the cluster. Use it when: you have a large cluster (50+ brokers), frequent scaling events, or you want hands-off operations. It also supports self-healing — if a broker dies, it can automatically reassign partitions.

---

## 17. Consumer Rebalancing Protocols — Eager vs Cooperative

Consumer rebalancing is how Kafka distributes partitions among consumers in a group.

### When Does Rebalancing Happen?

```
Trigger events:
  1. New consumer joins the group
  2. Consumer leaves (graceful shutdown or crash/timeout)
  3. Consumer fails heartbeat (session.timeout.ms exceeded)
  4. New partitions added to a subscribed topic
  5. Subscription pattern changes
```

### Eager Rebalancing (Stop-the-World) — The Old Way

```
Group Coordinator: Special broker that manages the consumer group
                   (partition = hash(group.id) % 50 → assigned to broker owning that partition)

Eager Rebalance Flow:

  Consumer A: [P0, P1, P2]     Consumer B: [P3, P4, P5]

  New Consumer C joins!

  Step 1: All consumers REVOKE all partitions (stop processing!)
    Consumer A: [] (STOPPED!)
    Consumer B: [] (STOPPED!)
    Consumer C: [] (new)

  Step 2: Group Coordinator triggers rebalance
  
  Step 3: Leader consumer (A) computes new assignment:
    Consumer A: [P0, P1]     ← lost P2
    Consumer B: [P2, P3]     ← lost P4, P5, gained P2
    Consumer C: [P4, P5]     ← new

  Step 4: All consumers get new assignment and resume

  Problem: TOTAL DOWNTIME during rebalance!
  Duration: session.timeout.ms (default 10s) + rebalance time
  For a group with 100 consumers → could be MINUTES of zero processing!
```

### Cooperative Rebalancing (Incremental) — The New Way

```
Cooperative Rebalance Flow:

  Consumer A: [P0, P1, P2]     Consumer B: [P3, P4, P5]

  New Consumer C joins!

  Step 1: Rebalance Round 1
    Leader computes: "Only P4, P5 need to move to C"
    Consumer A: [P0, P1, P2]  ← keeps all!  ✅ STILL PROCESSING
    Consumer B: [P3]           ← revokes P4, P5 only
    Consumer C: []             ← waits

  Step 2: Rebalance Round 2 (automatic)
    Consumer A: [P0, P1, P2]  ← unchanged  ✅ STILL PROCESSING
    Consumer B: [P3]           ← unchanged  ✅ STILL PROCESSING
    Consumer C: [P4, P5]      ← gets P4, P5 ✅ STARTS PROCESSING

  Total downtime: ZERO for A, minimal for B (only P4, P5 paused briefly)
```

### Comparison

| Aspect | Eager | Cooperative |
|--------|-------|-------------|
| **Revocation** | ALL partitions from ALL consumers | Only affected partitions |
| **Downtime** | Full stop-the-world | Incremental, near-zero |
| **Rebalance rounds** | 1 | 2+ (but faster overall) |
| **Assignor class** | `RangeAssignor`, `RoundRobinAssignor` | `CooperativeStickyAssignor` |
| **Default (Kafka 3.x)** | No | Yes (CooperativeStickyAssignor) |
| **Best for** | Simple, small groups | Large groups, production |

### Partition Assignment Strategies

```java
// Configuration:
props.put("partition.assignment.strategy", 
    "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");
```

| Strategy | How It Works | Pros | Cons |
|----------|-------------|------|------|
| **RangeAssignor** | Assigns ranges of partitions per topic to each consumer | Simple, predictable | Uneven if #partitions not divisible by #consumers |
| **RoundRobinAssignor** | Round-robin across all partitions of all topics | Even distribution | More partition movement on rebalance |
| **StickyAssignor** | Minimizes partition movement (eager protocol) | Less data re-processing | Still stop-the-world |
| **CooperativeStickyAssignor** | Sticky + cooperative (incremental) | Near-zero downtime + minimal movement | 2 rebalance rounds |

```
RangeAssignor example (2 consumers, 6 partitions):
  Topic: orders (P0,P1,P2,P3,P4,P5)
  Consumer A: P0, P1, P2    (first half)
  Consumer B: P3, P4, P5    (second half)

RoundRobinAssignor example:
  Consumer A: P0, P2, P4
  Consumer B: P1, P3, P5

StickyAssignor (after rebalance — consumer C joins):
  Before: A=[P0,P1,P2], B=[P3,P4,P5]
  After:  A=[P0,P1], B=[P3,P4], C=[P2,P5]  ← minimizes movement
```

### Static Group Membership

```
Problem: Consumer restarts (deploy) → session expires → rebalance → slow!

Solution: Static membership (Kafka 2.3+)
  props.put("group.instance.id", "consumer-host-1");

  With static membership:
  1. Consumer shuts down → group coordinator waits session.timeout.ms
  2. Consumer restarts quickly with SAME group.instance.id
  3. Gets back the SAME partitions — no rebalance at all!

  session.timeout.ms = 300000  (5 minutes — gives time for rolling deploys)

  Perfect for: Kubernetes rolling deployments, routine restarts
```

---

## 18. Exactly-Once Semantics (EOS) & Transactions

This is the hardest Kafka topic. If you can explain this well, you'll impress any interviewer.

### Delivery Semantics Recap

```
At-Most-Once:   Message may be lost, but never duplicated.
                 → acks=0, no retries

At-Least-Once:  Message is never lost, but may be duplicated.
                 → acks=all, retries=MAX, no idempotence

Exactly-Once:   Message is never lost AND never duplicated.
                 → Idempotent producer + Transactions
```

### Why Is Exactly-Once Hard?

```
Scenario: Read from topic A → Process → Write to topic B

  1. Consumer reads msg from topic A at offset 100
  2. App processes msg → produces result to topic B
  3. Consumer commits offset 100 to __consumer_offsets

  What if crash happens between step 2 and step 3?
  → On restart, consumer re-reads offset 100 → processes again → DUPLICATE in topic B!

  What if crash happens between step 1 and step 2?
  → On restart, consumer re-reads offset 100 → processes it. OK. But what if step 2 partially succeeded?
  → It's a mess.
```

### Kafka Transactions — The Solution

```java
// Transactional Producer setup:
Properties props = new Properties();
props.put("transactional.id", "order-processor-1");  // Must be unique and stable!
props.put("enable.idempotence", "true");              // Required for transactions

KafkaProducer<String, String> producer = new KafkaProducer<>(props);
producer.initTransactions();

// Consume-Transform-Produce loop:
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    producer.beginTransaction();
    try {
        for (ConsumerRecord<String, String> record : records) {
            // Process and produce result
            String result = process(record.value());
            producer.send(new ProducerRecord<>("output-topic", record.key(), result));
        }
        
        // Commit consumer offsets AS PART OF the transaction
        producer.sendOffsetsToTransaction(
            offsets,                    // consumer offsets to commit
            consumer.groupMetadata()    // consumer group
        );
        
        producer.commitTransaction();   // ATOMIC: either ALL writes + offset commit succeed, or NONE
    } catch (Exception e) {
        producer.abortTransaction();    // Roll back everything
    }
}
```

### How Transactions Work Internally

```
Components:
  1. Transaction Coordinator: A special broker (like Group Coordinator)
     → Manages transaction state in __transaction_state topic
  2. Transactional ID: Stable identifier for the producer instance
  3. Producer Epoch: Fencing mechanism (prevents zombie producers)

Flow:
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  1. producer.initTransactions()                                      │
│     Producer → Transaction Coordinator:                              │
│     "I'm transactional.id=order-processor-1, give me a PID + epoch" │
│     Coordinator: PID=42, epoch=1                                     │
│     (If old instance with same transactional.id exists, its epoch    │
│      is fenced — can no longer produce)                              │
│                                                                      │
│  2. producer.beginTransaction()                                      │
│     (Local state change only — no network call)                      │
│                                                                      │
│  3. producer.send(record)                                            │
│     First send to a new partition in this txn:                       │
│     Producer → Coordinator: "Add partition P0 to my transaction"     │
│     Coordinator writes to __transaction_state                        │
│     Producer → Broker: Write record with PID + epoch to P0           │
│                                                                      │
│  4. producer.sendOffsetsToTransaction(offsets, groupMetadata)        │
│     Producer → Coordinator: "Add __consumer_offsets partition to txn"│
│     Coordinator writes offset commit as part of transaction          │
│                                                                      │
│  5. producer.commitTransaction()                                     │
│     Producer → Coordinator: "Commit!"                                │
│     Coordinator:                                                     │
│       a) Writes PREPARE_COMMIT to __transaction_state                │
│       b) Writes COMMIT markers to ALL partitions in the transaction  │
│       c) Writes COMMITTED to __transaction_state                     │
│                                                                      │
│  Consumer with isolation.level=read_committed:                       │
│     → Only sees messages where COMMIT marker exists                  │
│     → Messages in an aborted transaction are skipped                 │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Transaction Isolation Levels (Consumer Side)

```
isolation.level=read_uncommitted (default):
  Consumer sees ALL messages, including those in open/aborted transactions.
  → Fast, but you may see duplicates or phantom reads.

isolation.level=read_committed:
  Consumer only sees committed messages.
  → Messages in open transactions are buffered (not delivered to app).
  → Aborted transaction messages are filtered out.
  
  How? Each partition has a Last Stable Offset (LSO):
    LSO = the offset of the first message in an open transaction
    Consumer can only read up to LSO (not up to HW).

  If a transaction stays open for too long:
    → LSO doesn't advance → consumer is stuck!
    → transaction.timeout.ms = 60000 (1 min default) prevents this
```

### Zombie Fencing

```
Problem: Two instances with the same transactional.id running simultaneously.

  Instance A (old): transactional.id = "processor-1", PID=42, epoch=0
  Instance B (new): transactional.id = "processor-1"

  B calls initTransactions():
  → Coordinator assigns PID=42, epoch=1
  → Coordinator fences epoch=0

  If A tries to produce: 
  → Broker rejects (invalid epoch) → ProducerFencedException
  → A must shut down

  This prevents zombie producers from corrupting data!
```

---

## 19. Log Compaction & Retention Policies

### Two Cleanup Policies

```
cleanup.policy = delete    (default)
  → Delete segments older than retention.ms or when log exceeds retention.bytes
  
cleanup.policy = compact
  → Keep only the LATEST value for each key (like a KV store)

cleanup.policy = delete,compact
  → Both! Compact first, then delete old compacted segments
```

### Log Compaction — How It Works

```
Before compaction:
  Offset:  0    1    2    3    4    5    6    7    8
  Key:     A    B    A    C    B    A    D    C    A
  Value:   v1   v1   v2   v1   v2   v3   v1   v2   v4

After compaction:
  Offset:  5    6    7    8
  Key:     A    D    C    A
  Value:   v3   v1   v2   v4

  Wait — offset 8 (A=v4) is the latest for key A. But offset 5 (A=v3) is also there?
  Actually: compaction keeps the LAST occurrence. So:
  
  Final compacted log:
  Offset:  4    6    7    8
  Key:     B    D    C    A
  Value:   v2   v1   v2   v4

  → For each key, only the latest value survives.
  → Offsets are preserved (no renumbering).
  → Old messages are removed, but offsets still increase monotonically.
```

### Compaction Internals

```
Log structure:
  ┌──────────────────┬──────────────────┐
  │    Clean Part     │    Dirty Part    │ ← Active segment
  │  (already         │  (not yet        │
  │   compacted)      │   compacted)     │
  └──────────────────┴──────────────────┘
                      ↑
                 Cleaner Offset

Log Cleaner Thread (background):
  1. Builds an in-memory offset map: key → latest offset (for dirty part)
  2. Reads clean segments
  3. For each message: if there's a newer offset for this key → skip
  4. Writes surviving messages to a new clean segment
  5. Swaps old segments with new compacted ones
  6. Updates cleaner offset

Key configs:
  min.cleanable.dirty.ratio = 0.5   (compact when 50% of log is dirty)
  min.compaction.lag.ms = 0          (how long to wait before compacting)
  delete.retention.ms = 86400000     (24h — keep tombstones for this long)
```

### Tombstones (Deleting a Key)

```
To DELETE a key from a compacted topic: produce a message with that key and NULL value.

  Producer sends: key="user-123", value=null   ← this is a "tombstone"

  After compaction:
  → All previous values for "user-123" are removed
  → The tombstone itself is kept for delete.retention.ms (24h default)
  → After that, the tombstone is also removed
  → "user-123" no longer exists in the log

Use case: __consumer_offsets topic is compacted.
  When a consumer group is deleted, a tombstone is written for its offsets.
```

### Use Cases for Compaction

| Topic | Why Compaction | What's the Key |
|-------|---------------|----------------|
| `__consumer_offsets` | Keep latest offset per group+partition | group+topic+partition |
| `__transaction_state` | Keep latest txn state per transactional.id | transactional.id |
| User profiles CDC | Keep latest profile per user | user_id |
| Configuration store | Keep latest config per key | config_key |
| Kafka Connect offsets | Keep latest offset per connector | connector_id |

---

## 20. Why Kafka Is So Fast — Performance Deep Dive

### The 6 Reasons Kafka Achieves Millions of Messages/Second

```
1. SEQUENTIAL I/O
   ─────────────
   Kafka appends to the END of a log file. No random seeks.
   Sequential disk writes on modern hardware: 600+ MB/s
   Random disk writes: ~100 IOPS × 4KB = 0.4 MB/s
   → Sequential is 1500x faster!

   SSD: random ≈ 100K IOPS, sequential ≈ 500+ MB/s
   HDD: random ≈ 100 IOPS, sequential ≈ 100+ MB/s

2. ZERO-COPY (sendfile)
   ────────────────────
   Data goes from disk → kernel → NIC without touching user space.
   (Explained in detail in Section 13)

3. OS PAGE CACHE
   ─────────────
   Kafka delegates caching to the OS. Writes go to page cache (RAM),
   OS flushes to disk asynchronously. Recent messages served from RAM.
   
   "Kafka's cache is the OS page cache" — no JVM heap overhead!
   → No GC pauses for cached data
   → Survives broker restarts (page cache is in kernel, not process)

4. BATCHING EVERYWHERE
   ───────────────────
   Producer: batches messages before sending (batch.size, linger.ms)
   Broker:   writes batches to disk in one I/O operation
   Consumer: fetches in batches (fetch.min.bytes, fetch.max.wait.ms)
   
   1 network round-trip for 1000 messages instead of 1000 round-trips.

5. COMPRESSION
   ───────────
   Producers compress batches: gzip, snappy, lz4, zstd
   Brokers store compressed data as-is (no decompression!)
   Consumers decompress.
   
   → Less network bandwidth
   → Less disk space
   → Less disk I/O
   
   Broker doesn't decompress (mostly):
   Exception: if producer and topic compression differ,
              or for validation with message format conversion.

6. PARTITIONING = PARALLELISM
   ──────────────────────────
   Each partition is an independent log.
   Multiple consumers can read different partitions in parallel.
   Multiple producers can write to different partitions in parallel.
   Scale horizontally by adding partitions + consumers.
```

### Benchmark Numbers (Typical Production)

```
Single broker:
  Write throughput: 100-200 MB/s (with replication)
  Read throughput:  300-500 MB/s (from page cache, zero-copy)
  Messages/sec:    500K-2M (depending on message size)

Three-broker cluster:
  Write throughput: 300-600 MB/s aggregate
  Read throughput:  1-1.5 GB/s aggregate
  
End-to-end latency (producer → consumer):
  p50: 2-5 ms
  p99: 10-50 ms (depends on acks, replication)
  
With acks=1, linger.ms=0: ~2 ms
With acks=all, linger.ms=5: ~10-15 ms
```

---

## 21. Kafka Delivery Guarantees & Ordering

### Ordering Guarantees

```
Within a SINGLE partition:
  ✅ Messages are strictly ordered by offset.
  ✅ Consumers see messages in offset order.
  ✅ With idempotent producer: producer retries maintain order.

Across MULTIPLE partitions:
  ❌ NO ordering guarantee.
  ❌ Consumer may process partition 1's offset 5 before partition 0's offset 3.

Implication:
  If you need messages about the same entity to be ordered:
  → Use the entity ID as the message key
  → Same key → same partition → ordered!

  Example:
  Key = user_id → all events for user-123 go to the same partition → ordered
  Key = order_id → all events for order-456 go to the same partition → ordered
```

### Delivery Guarantees Summary

| Producer Config | Consumer Config | Delivery Semantic | Data Loss? | Duplicates? |
|----------------|-----------------|-------------------|------------|-------------|
| `acks=0` | auto-commit | At-most-once | Yes | No |
| `acks=all` + retries | auto-commit | At-least-once | No | Yes |
| `acks=all` + idempotent | manual commit | At-least-once (deduped per partition) | No | Possible (across partitions) |
| `acks=all` + transactions | `read_committed` | Exactly-once | No | No |

### The `enable.auto.commit` Trap

```
enable.auto.commit = true (default):
  Consumer automatically commits offsets every auto.commit.interval.ms (5s).

  Problem:
  1. Consumer reads offset 100
  2. Auto-commit fires → commits offset 100
  3. Consumer crashes BEFORE processing offset 100
  4. On restart → consumer starts at 101 → OFFSET 100 IS LOST!

  This is at-most-once delivery!

enable.auto.commit = false:
  Consumer manually commits after processing.
  
  consumer.poll();
  process(records);
  consumer.commitSync();   // or commitAsync()
  
  Problem: If crash after process() but before commitSync():
  → On restart, re-processes the same messages → at-least-once delivery.
  
  For exactly-once: use transactions (Section 18).
```

---

## 22. Kafka Connect & Kafka Streams (Overview)

### Kafka Connect

```
Kafka Connect = Framework for moving data IN and OUT of Kafka.

  ┌──────────┐    ┌──────────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────┐
  │  MySQL   │ ──→│ Source       │ ──→│  Kafka   │ ──→│ Sink         │ ──→│ Elastic  │
  │  (DB)    │    │ Connector   │    │  Topic   │    │ Connector   │    │  Search  │
  └──────────┘    └──────────────┘    └──────────┘    └──────────────┘    └──────────┘

Source Connectors: Read from external system → Write to Kafka
  Examples: Debezium (CDC from MySQL/Postgres), JDBC Source, File Source

Sink Connectors: Read from Kafka → Write to external system
  Examples: Elasticsearch Sink, S3 Sink, JDBC Sink, HDFS Sink

Key benefits:
  ✅ No custom code — just configuration
  ✅ Scalable (distributed workers)
  ✅ Fault-tolerant (automatic task failover)
  ✅ Exactly-once delivery (with supported connectors)
  ✅ Schema management (with Schema Registry)
```

### Kafka Streams

```
Kafka Streams = Library for stream processing (runs inside your app — no separate cluster!)

  Unlike Flink, Spark Streaming: No separate cluster to manage.
  It's a Java library. Your app IS the stream processor.

  ┌──────────────────────────────────────┐
  │         Your Java Application         │
  │  ┌──────────────────────────────────┐│
  │  │       Kafka Streams Library       ││
  │  │                                   ││
  │  │  Input Topic → Transform → Output ││
  │  │               ↓                   ││
  │  │          State Store              ││
  │  │       (RocksDB local)             ││
  │  └──────────────────────────────────┘│
  └──────────────────────────────────────┘

Key operations:
  - filter(), map(), flatMap()      ← Stateless
  - groupByKey(), aggregate()       ← Stateful
  - join() (KStream-KStream, KStream-KTable, KTable-KTable)
  - windowing (tumbling, hopping, sliding, session)
  
State stores:
  - Backed by RocksDB (local disk)
  - Changelog topic for fault tolerance
  - Interactive queries (query state from outside)
```

---

# Part C — Interview Ready

---

## 23. Common Interview Questions

### Q1: "What role does ZooKeeper play in Kafka?"
**A:** ZooKeeper serves as the **central metadata store and coordination service** for Kafka. It handles: broker registration (tracking which brokers are alive via ephemeral znodes), controller election (ensuring exactly one controller exists), storing topic/partition metadata (which broker leads which partition), maintaining ISR lists, and storing cluster configuration. Essentially, ZooKeeper is Kafka's **brain** — it knows the state of the entire cluster.

### Q2: "What happens when a Kafka broker goes down?"
**A:** 
1. The broker's ephemeral znode in ZooKeeper (`/brokers/ids/{id}`) is automatically deleted when the session expires.
2. The Controller broker (watching `/brokers/ids`) gets a watch notification.
3. The Controller identifies all partitions where the dead broker was leader.
4. For each affected partition, the Controller picks a new leader from the ISR list.
5. The Controller updates the partition state in ZooKeeper and sends LeaderAndISR requests to the remaining brokers.
6. Producers and consumers get updated metadata on their next metadata refresh.

### Q3: "How does Kafka ensure there's only one Controller?"
**A:** Using ZooKeeper's ephemeral znode. All brokers race to create `/kafka/controller`. Only one succeeds (ZooKeeper guarantees atomic create). Others set a watch. If the controller dies, the ephemeral znode is deleted, triggering watch notifications, and a new election starts. The **controller epoch** (a monotonically increasing number) prevents zombie controllers from issuing stale commands.

### Q4: "Why is Kafka moving away from ZooKeeper?"
**A:** Several reasons:
- **Operational burden** — you had to manage two distributed systems instead of one
- **Scalability bottleneck** — ZooKeeper limited Kafka to ~200K partitions because ZK writes are slow and the controller must reload all metadata from ZK on failover
- **Slow controller failover** — new controller had to read all metadata from ZK (could take minutes for large clusters)
- **Architectural mismatch** — ZooKeeper is a general coordination service, but Kafka needed something optimized for its specific metadata patterns

### Q5: "What is KRaft and how does it differ from ZooKeeper mode?"
**A:** KRaft (Kafka Raft) is Kafka's built-in consensus protocol that replaces ZooKeeper. Instead of storing metadata in ZooKeeper znodes, metadata is stored in an internal Kafka topic (`__cluster_metadata`). Controllers elect a leader using Raft consensus. Key benefits: no external dependency, faster controller failover (milliseconds vs seconds), better scalability (millions of partitions vs ~200K), and simpler operations.

### Q6: "How does Kafka handle split-brain with ZooKeeper?"
**A:** Multiple layers of protection:
1. **ZooKeeper quorum** — only the majority partition can process writes
2. **Controller epoch** — prevents old controller from issuing commands after a new one is elected
3. **Leader epoch in partitions** — each partition leader has an epoch; followers reject requests from leaders with stale epochs
4. **ISR** — only brokers in the ISR can become leaders, ensuring no under-replicated broker accidentally becomes leader

### Q7: "If ZooKeeper goes down completely, what happens to Kafka?"
**A:** 
- **Existing producers/consumers continue working** — they talk to brokers, not ZK
- **No new topics** can be created (controller can't write metadata)
- **No partition reassignment** — if a broker dies, no leader election can happen
- **No new consumer group rebalancing** (in legacy mode)
- **No broker can join** the cluster
- Basically: **the cluster is frozen** — reads and writes to existing partitions work, but no management operations

### Q8: "In a production Kafka setup, how many ZooKeeper nodes do you need?"
**A:** Minimum **3**, recommended **5** for production. ZooKeeper needs a quorum (majority), so:
- 3 nodes → tolerates 1 failure
- 5 nodes → tolerates 2 failures
- **Important:** ZooKeeper nodes should be on **separate physical machines** or availability zones. They should have **fast SSDs** (ZK writes to disk on every transaction) and dedicated resources (don't co-locate with Kafka brokers in production).

### Q9: "What is the `__consumer_offsets` topic?"
**A:** It's an internal Kafka topic that stores consumer group offset commits. Before Kafka 0.9, offsets were stored in ZooKeeper, but high-throughput consumers overwhelmed ZK with writes. Now offsets are stored as messages in this compacted topic with 50 partitions (default). Each consumer group's offsets are stored in a specific partition based on `hash(group.id) % 50`. This moved a high-write workload from ZK (bad at writes) to Kafka (great at writes).

### Q10: "Draw the interaction between Kafka and ZooKeeper when a new topic is created."
**A:**
```
1. Admin/Client → Broker (any broker)
2. Broker forwards to Controller
3. Controller computes partition assignment (which broker gets which partition)
4. Controller writes to ZK:
   - /kafka/brokers/topics/{name} → partition-to-replica mapping
   - /kafka/brokers/topics/{name}/partitions/{p}/state → leader, ISR
5. Controller sends LeaderAndISR to each involved broker (via direct RPC)
6. Brokers create log directories and start serving partitions
7. Controller sends UpdateMetadata to ALL brokers
8. All brokers now know about the new topic
9. Next time a producer asks for metadata → it learns about the new topic
```

### Q11: "Explain the difference between High Watermark and Log End Offset."
**A:** LEO (Log End Offset) is the offset of the NEXT message to be written — each replica tracks its own LEO. HW (High Watermark) is the offset up to which ALL ISR replicas have replicated — it's the "committed" boundary. Consumers can only read up to HW, not LEO. This ensures consumers never see messages that could be lost if the leader fails. HW advances only when the leader sees that all ISR followers have fetched up to that offset.

### Q12: "How does the idempotent producer prevent duplicates?"
**A:** The broker assigns each producer a unique Producer ID (PID) and the producer attaches a monotonically increasing sequence number to each message per partition. The broker maintains a map of `{PID, Partition} → last sequence number`. If a retry comes in with the same sequence number, the broker silently deduplicates it. This guarantees exactly-once delivery per partition without duplicates, even across retries.

### Q13: "Explain Eager vs Cooperative rebalancing."
**A:** Eager rebalancing is stop-the-world — when ANY consumer joins/leaves, ALL consumers revoke ALL their partitions, stop processing, and wait for a new assignment. This causes total downtime. Cooperative rebalancing (incremental) only revokes the specific partitions that need to move, in two rounds. Consumers that aren't affected keep processing. This is the default in Kafka 3.x via CooperativeStickyAssignor and reduces rebalance downtime to near-zero.

### Q14: "What happens when you add new brokers to a Kafka cluster?"
**A:** Nothing automatic! New brokers sit idle. Existing partitions don't migrate. You must manually trigger partition reassignment using `kafka-reassign-partitions.sh --generate` (creates a plan), then `--execute` (with `--throttle` for production!), then `--verify`. Alternatively, use LinkedIn's Cruise Control for automated, continuous rebalancing. After reassignment, run preferred leader election to balance leadership across brokers.

### Q15: "How do Kafka transactions provide exactly-once semantics?"
**A:** Transactions make consume-transform-produce atomic. The producer gets a stable transactional.id and calls `beginTransaction()`. All produces and offset commits happen within the transaction. On `commitTransaction()`, the Transaction Coordinator writes COMMIT markers to all involved partitions atomically. Consumers with `isolation.level=read_committed` only see committed messages. If the producer crashes, the transaction is aborted and consumers never see the uncommitted messages.

### Q16: "Why does Kafka store messages on disk but is still so fast?"
**A:** Six reasons: (1) Sequential I/O — append-only writes are 1500x faster than random writes, (2) Zero-copy via `sendfile()` — data goes from page cache to NIC without user-space copy, (3) OS Page Cache — Kafka delegates caching to the OS, not JVM heap, so no GC pressure, (4) Batching — producer, broker, and consumer all batch messages, (5) Compression — data is compressed in transit and at rest, (6) Partitioning — horizontal parallelism across partitions.

### Q17: "What is log compaction and when would you use it?"
**A:** Log compaction keeps only the latest value for each message key, turning a Kafka topic into an append-only key-value store. Instead of deleting entire segments by time/size, the log cleaner removes older entries for the same key. Use it for: changelog topics (CDC), __consumer_offsets (latest offset per group), configuration stores, and any case where you need "latest state per entity" forever. To delete a key, produce a tombstone (null value).

### Q18: "How does Kafka handle leader failover without data loss?"
**A:** When a leader fails, the Controller elects a new leader from the ISR (In-Sync Replicas). Since ISR replicas are fully caught up with the leader, no committed data is lost. With `acks=all` and `min.insync.replicas=2`, the producer only gets an ACK when at least 2 replicas have the data. Even if the leader crashes immediately after ACK, the new leader (from ISR) has the data. Leader Epochs prevent log divergence when the old leader comes back.

### Q19: "What is a zombie producer and how does Kafka fence it?"
**A:** A zombie is an old producer instance that's still running after a new instance with the same `transactional.id` has started (e.g., after failover). When the new instance calls `initTransactions()`, the Transaction Coordinator bumps the producer epoch. Any produce request from the old instance with the stale epoch is rejected with `ProducerFencedException`. This prevents two instances from writing conflicting data within the same transaction scope.

### Q20: "Explain the Sticky Partitioner and why it replaced Round-Robin."
**A:** For messages without a key, the old Round-Robin partitioner assigned each message to a different partition, resulting in many small batches (one per partition). The Sticky Partitioner (default since Kafka 2.4) sticks to ONE partition until the batch is full, then switches. This produces fewer, larger batches → fewer network round-trips → higher throughput. It trades perfect distribution for significantly better batching efficiency.

---

## 24. Quick Reference Table

| Concept | Key Point |
|---------|-----------|
| **Broker registration** | Ephemeral znode at `/brokers/ids/{id}`. Auto-deletes on crash. |
| **Controller** | ONE broker. Elected via ephemeral znode `/controller`. Manages cluster. |
| **Controller epoch** | Fencing token. Prevents zombie controllers. Increments on each election. |
| **Topic metadata** | Stored in `/brokers/topics/{name}`. Partition→replica mapping. |
| **Partition state** | `/brokers/topics/{name}/partitions/{p}/state` → leader + ISR |
| **ISR** | In-Sync Replicas. Stored in ZK. Updated by partition leader. |
| **Consumer offsets** | **NOT in ZK** since 0.9+. Stored in `__consumer_offsets` topic. |
| **KRaft** | Kafka Raft. Replaces ZK. Metadata in `__cluster_metadata` topic. |
| **KRaft benefit** | No ZK dependency, faster failover, millions of partitions |
| **ZK for Kafka future** | Deprecated. Will be removed in a future Kafka version. |
| **ZK ensemble for Kafka** | 3 nodes (min), 5 nodes (recommended production) |
| **What ZK does NOT store** | Actual messages! Only metadata. |
| **What happens if ZK dies** | Cluster frozen. Existing reads/writes work. No management ops. |
| **Segments** | Partition data split into ~1GB files. Filename = base offset. |
| **Offset index** | Sparse mapping: offset → byte position in .log file. |
| **Zero-copy** | `sendfile()` — disk to NIC without user-space copy. |
| **Page cache** | Kafka delegates caching to OS. No JVM heap overhead. |
| **HW (High Watermark)** | Offset up to which ALL ISR replicas have data. Consumer read limit. |
| **LEO (Log End Offset)** | Next offset to be written. Each replica has its own LEO. |
| **Leader Epoch** | Prevents log divergence after leader failover. Monotonically increasing. |
| **Idempotent Producer** | PID + sequence number. Deduplication at broker. Default since Kafka 3.0. |
| **Sticky Partitioner** | Sticks to one partition until batch is full. Better batching than round-robin. |
| **acks=all** | Producer waits for all ISR replicas. Strongest durability. |
| **min.insync.replicas** | Minimum ISR count for acks=all to succeed. Typically 2. |
| **Partition reassignment** | Manual process. New brokers don't auto-get partitions. Use throttling! |
| **Preferred leader election** | Elect the first replica in the list as leader. Balances leadership. |
| **Cruise Control** | LinkedIn's tool for automated partition rebalancing. |
| **Eager rebalance** | Stop-the-world. ALL consumers revoke ALL partitions. |
| **Cooperative rebalance** | Incremental. Only affected partitions revoked. Near-zero downtime. |
| **CooperativeStickyAssignor** | Default in Kafka 3.x. Cooperative + minimal partition movement. |
| **Static membership** | `group.instance.id` prevents rebalance on restart. Great for K8s. |
| **Transactions** | Atomic consume-transform-produce. Uses transactional.id + epoch. |
| **read_committed** | Consumer only sees committed transaction messages. |
| **Zombie fencing** | New producer epoch invalidates old instance with same transactional.id. |
| **Log compaction** | Keeps latest value per key. Topic as KV store. |
| **Tombstone** | Key + null value. Deletes a key from compacted topic. |
| **Kafka Connect** | Framework for moving data in/out of Kafka. Source + Sink connectors. |
| **Kafka Streams** | Library (not cluster) for stream processing inside your app. |
| **Migration** | ZK → dual-write → KRaft only. Zero-downtime possible. |

---

## Timeline: Kafka's ZooKeeper Journey

```
2011 │ Kafka created at LinkedIn — deeply coupled with ZooKeeper
     │
2015 │ Kafka 0.9 — Consumer offsets moved from ZK to __consumer_offsets
     │          — First step in reducing ZK dependency
     │
2017 │ KIP-500 proposed — "Replace ZooKeeper with a self-managed metadata quorum"
     │
2021 │ Kafka 2.8 — First early-access KRaft mode (not production ready)
     │
2022 │ Kafka 3.3 — KRaft marked production-ready
     │
2023 │ Kafka 3.5 — ZooKeeper mode officially deprecated
     │
2024+│ Kafka 4.0 — ZooKeeper support planned for removal
```

---

> **Bottom line for interviews:** ZooKeeper was Kafka's **external brain** — storing who's alive, who's the leader, and what goes where. But Kafka outgrew it. KRaft moves that brain **inside Kafka itself**, making it faster, simpler, and able to handle millions of partitions. Know both — because most production clusters are still migrating.

