# 🦁 Apache ZooKeeper — Deep Dive for Interviews

> ZooKeeper is boring by itself — until you realize **every distributed system you admire** relies on it under the hood.  
> Kafka, HBase, Hadoop, SolrCloud, Clickhouse — all of them use or used ZooKeeper.

---

## Table of Contents

1. [What is ZooKeeper?](#1-what-is-zookeeper)
2. [Why Do We Even Need It?](#2-why-do-we-even-need-it)
3. [Core Concepts — The Mental Model](#3-core-concepts--the-mental-model)
4. [ZooKeeper Data Model — ZNodes](#4-zookeeper-data-model--znodes)
5. [ZNode Types — The Interview Favorite](#5-znode-types--the-interview-favorite)
6. [Watches — The Notification System](#6-watches--the-notification-system)
7. [ZooKeeper Architecture — How It Works Internally](#7-zookeeper-architecture--how-it-works-internally)
8. [Leader Election — The Famous Use Case](#8-leader-election--the-famous-use-case)
9. [Distributed Locking](#9-distributed-locking)
10. [Service Discovery](#10-service-discovery)
11. [Configuration Management](#11-configuration-management)
12. [ZAB Protocol — ZooKeeper Atomic Broadcast](#12-zab-protocol--zookeeper-atomic-broadcast)
13. [Session Management](#13-session-management)
14. [CAP Theorem — Where Does ZooKeeper Sit?](#14-cap-theorem--where-does-zookeeper-sit)
15. [ZooKeeper CLI Commands](#15-zookeeper-cli-commands)
16. [Common Interview Questions](#16-common-interview-questions)
17. [Quick Reference Table](#17-quick-reference-table)

---

## 1. What is ZooKeeper?

**One line:** ZooKeeper is a **centralized coordination service** for distributed systems — it helps multiple servers agree on things like "who is the leader?", "what's the current config?", and "is this server alive?".

**Think of it as:** A **shared notebook** that all your servers can read and write to, with **instant notifications** when something changes — and it **never loses data** even if some servers crash.

**Created by:** Yahoo! (open-sourced via Apache). The name comes from — it manages the "zoo" of distributed services (Pig, Hive, Elephant/Hadoop 🐘… get it?).

---

## 2. Why Do We Even Need It?

In a distributed system you have dozens or hundreds of servers. They face **hard problems**:

| Problem | Without ZooKeeper | With ZooKeeper |
|---------|-------------------|----------------|
| **Who is the leader?** | Servers argue, split-brain happens | ZooKeeper elects one, everyone agrees |
| **Is Server-3 alive?** | You build your own heartbeat system | Ephemeral znodes + watches handle it |
| **What's the current DB config?** | Config scattered across servers | One source of truth in ZooKeeper |
| **Two servers updating same resource?** | Race conditions, data corruption | Distributed lock via ZooKeeper |
| **Where is the order-service running?** | Hardcoded IPs, manual updates | Service discovery via ZooKeeper |

**The golden rule:** ZooKeeper solves **coordination**, not computation. It doesn't process your data — it helps your servers **cooperate**.

---

## 3. Core Concepts — The Mental Model

Think of ZooKeeper as a **tiny, reliable, highly-available file system** with superpowers:

```
┌───────────────────────────────────────────┐
│            ZooKeeper Ensemble             │
│                                           │
│   Server 1 ←──┐                           │
│   Server 2 ←──┼── Quorum (majority)       │
│   Server 3 ←──┘                           │
│   (Server 4)  ← follower                  │
│   (Server 5)  ← follower                  │
│                                           │
│   Data: tree of ZNodes (like a filesystem)│
│   Watches: event notifications            │
│   Sessions: client connections            │
└───────────────────────────────────────────┘
```

### Key Properties:

| Property | What It Means |
|----------|---------------|
| **Sequential Consistency** | Updates from a client are applied in order |
| **Atomicity** | Updates either fully succeed or fully fail |
| **Single System Image** | A client sees the same view regardless of which server it connects to |
| **Reliability** | Once an update is applied, it persists until overwritten |
| **Timeliness** | Clients see updates within a bounded time |

---

## 4. ZooKeeper Data Model — ZNodes

ZooKeeper stores data in a **hierarchical tree**, just like a file system. Each node is called a **ZNode**.

```
/                              ← root
├── /app1                      ← znode
│   ├── /app1/leader           ← stores which server is leader
│   └── /app1/config           ← stores app configuration
├── /app2
│   ├── /app2/members
│   │   ├── /app2/members/server-1   ← ephemeral node
│   │   ├── /app2/members/server-2
│   │   └── /app2/members/server-3
│   └── /app2/locks
└── /kafka
    ├── /kafka/brokers
    ├── /kafka/topics
    └── /kafka/controller
```

### ZNode Properties:

| Property | Description |
|----------|-------------|
| **Path** | Unique identifier, like `/app1/config` |
| **Data** | Byte array (up to **1 MB** by default — ZooKeeper is NOT for large data!) |
| **Version** | Increments on every update (used for optimistic locking) |
| **ACL** | Access Control List — who can read/write |
| **Stat** | Metadata — creation time, modification time, data version, children version, etc. |
| **Children** | A znode can have child znodes |

**Interview Q:** *"Can a ZNode have both data AND children?"*  
**A:** Yes! Unlike a regular filesystem where files have data and directories have children, a ZNode can have **both**. But in practice, you usually separate them.

---

## 5. ZNode Types — The Interview Favorite

This is asked in **every** ZooKeeper interview. There are **4 types**:

### 1. Persistent ZNode (default)
```
create /app/config "db_host=10.0.0.1"
```
- Survives even after the client that created it disconnects
- You must explicitly delete it
- **Use case:** storing configuration, metadata

### 2. Ephemeral ZNode ⭐
```
create -e /app/members/server-1 "alive"
```
- **Automatically deleted** when the client session ends (client disconnects/crashes)
- **Cannot have children**
- **Use case:** detecting which servers are alive, leader election
- This is the **killer feature** of ZooKeeper!

### 3. Persistent Sequential ZNode
```
create -s /app/locks/lock- "data"
# Creates: /app/locks/lock-0000000001
# Next:    /app/locks/lock-0000000002
```
- Persistent + ZooKeeper appends a **monotonically increasing counter** to the name
- **Use case:** ordering, queuing

### 4. Ephemeral Sequential ZNode ⭐⭐
```
create -e -s /app/election/candidate- "server-1"
# Creates: /app/election/candidate-0000000001
```
- Ephemeral + Sequential
- Auto-deleted on disconnect + auto-numbered
- **Use case:** leader election, distributed locking (this is the most powerful type!)

### Quick Summary Table:

| Type | Survives Disconnect? | Auto-Numbered? | Has Children? | Common Use |
|------|---------------------|----------------|---------------|------------|
| Persistent | ✅ Yes | ❌ No | ✅ Yes | Config, metadata |
| Ephemeral | ❌ No | ❌ No | ❌ No | Heartbeat, membership |
| Persistent Sequential | ✅ Yes | ✅ Yes | ✅ Yes | Queues, ordering |
| Ephemeral Sequential | ❌ No | ✅ Yes | ❌ No | Leader election, locks |

---

## 6. Watches — The Notification System

Instead of polling ("is anything changed? is anything changed?"), ZooKeeper lets you **watch** a znode and get **notified** when it changes.

```
           Client                    ZooKeeper
             |                           |
             |--- getData(/config, watch=true) --->|
             |<-- data="v1" ----------------------|
             |                           |
             |       (some other client updates /config to "v2")
             |                           |
             |<-- WATCH EVENT: NodeDataChanged ---|
             |                           |
             |--- getData(/config, watch=true) --->|  ← must re-register!
             |<-- data="v2" ----------------------|
```

### Watch Rules (interview-critical!):

| Rule | Explanation |
|------|-------------|
| **One-time trigger** | A watch fires **once**. After that, you must re-register it. |
| **Ordered** | Watch events are delivered in the order they happened |
| **You see the event before the new data** | You get notified, THEN you read the new value |
| **Types of watches** | `getData()` and `exists()` → watch data changes. `getChildren()` → watch child changes |

### Watch Event Types:

| Event | When It Fires |
|-------|---------------|
| `NodeCreated` | A znode that didn't exist is created |
| `NodeDeleted` | A znode is deleted |
| `NodeDataChanged` | A znode's data is updated |
| `NodeChildrenChanged` | A child is added/removed from a znode |

**Interview Q:** *"What happens if ZooKeeper goes down — are watches lost?"*  
**A:** When a client reconnects, all previously registered watches are re-registered automatically. But if events happened while disconnected, the client may miss intermediate events (it gets the latest state).

---

## 7. ZooKeeper Architecture — How It Works Internally

### The Ensemble

ZooKeeper runs as a **cluster** called an **ensemble**. You always run an **odd number** of servers (3, 5, 7).

```
┌──────────────────────────────────────────────────┐
│                ZooKeeper Ensemble                 │
│                                                  │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│   │ Server 1 │  │ Server 2 │  │ Server 3 │      │
│   │ (Leader) │  │(Follower)│  │(Follower)│      │
│   └────┬─────┘  └────┬─────┘  └────┬─────┘      │
│        │              │              │            │
│        └──────────────┼──────────────┘            │
│                       │                           │
│              All writes go to Leader              │
│              Reads can go to any server            │
│                                                  │
│   Quorum = majority = ⌊N/2⌋ + 1                  │
│   For 3 servers: quorum = 2                       │
│   For 5 servers: quorum = 3                       │
└──────────────────────────────────────────────────┘
```

### Server Roles:

| Role | Responsibility |
|------|---------------|
| **Leader** | Handles ALL write requests. Proposes changes to followers. |
| **Follower** | Serves read requests. Votes on write proposals. Participates in leader election. |
| **Observer** (optional) | Serves reads but does NOT vote. Used to scale read throughput without hurting write performance. |

### Write Path (critical for interviews!):

```
1. Client sends write to any server
2. If it's a follower → forwards write to Leader
3. Leader creates a proposal (transaction)
4. Leader sends proposal to ALL followers
5. Followers write to disk and send ACK to Leader
6. Once Leader gets ACKs from MAJORITY (quorum) → COMMIT
7. Leader sends COMMIT to all followers
8. Response sent to client
```

**Interview Q:** *"Why odd number of servers?"*  
**A:** Quorum = majority. With 3 servers, you can tolerate 1 failure. With 4 servers, you can STILL only tolerate 1 failure (need 3 out of 4). So 4 gives you no extra fault tolerance over 3, but adds overhead. Odd numbers are optimal.

### Why 3 or 5?

| Ensemble Size | Quorum | Failures Tolerated | Recommended? |
|--------------|--------|-------------------|-------------|
| 1 | 1 | 0 | ❌ Dev only |
| 3 | 2 | 1 | ✅ Minimum for production |
| 5 | 3 | 2 | ✅ Standard production |
| 7 | 4 | 3 | ⚠️ Rare, for extreme reliability |

---

## 8. Leader Election — The Famous Use Case

This is the **#1 use case** interviewers ask about. Here's how it works:

### Algorithm Using Ephemeral Sequential Znodes:

```
Step 1: Each server creates an ephemeral sequential znode
        Server A → /election/candidate-0000000001
        Server B → /election/candidate-0000000002
        Server C → /election/candidate-0000000003

Step 2: Each server checks — "Am I the LOWEST numbered node?"
        Server A: I'm 001 → I'M THE LEADER! ✅
        Server B: I'm 002 → Not leader, watch 001
        Server C: I'm 003 → Not leader, watch 002

Step 3: Server A crashes 💥
        → ephemeral node 001 auto-deleted
        → Server B gets watch notification
        → Server B checks: "Am I lowest now?" → YES → NEW LEADER! ✅
        → Server C is still watching 002, undisturbed
```

### Why Watch Only the Previous Node?

If ALL servers watched the leader node, when the leader dies, ALL servers get notified simultaneously → **thundering herd problem**. Instead, each server only watches the node **just before** it. This creates a chain — only one server needs to react.

```
Not this (herd effect):          This (chain watch):
   C watches A                      C watches B
   B watches A                      B watches A
   D watches A                      D watches C
       💥 A dies                        💥 A dies
   B, C, D all react!              Only B reacts!
```

---

## 9. Distributed Locking

### Simple Lock (has issues):

```
1. Client creates ephemeral znode: /locks/my-lock
2. If created successfully → you have the lock
3. If it already exists → watch it, wait for deletion
4. When done → delete /locks/my-lock (or disconnect, and ephemeral node auto-deletes)
```

**Problem:** Herd effect — when the lock is released, ALL waiting clients get notified.

### Better Lock (using sequential znodes):

```
1. Client creates ephemeral sequential: /locks/lock-0000000001
2. Client calls getChildren(/locks)
3. If my znode is the LOWEST → I have the lock!
4. If not → watch the znode just before mine
5. When notified → recheck if I'm lowest
6. When done → delete my znode
```

This is **fair** (FIFO order) and avoids the herd effect.

**Interview Q:** *"What if a client gets the lock and then crashes?"*  
**A:** The ephemeral znode auto-deletes when the session ends → the lock is automatically released → the next waiter gets the lock. No deadlocks! This is why ephemeral znodes are a killer feature.

---

## 10. Service Discovery

Servers register themselves, clients discover them:

```
                  ZooKeeper
                     |
     /services/order-service/
         ├── instance-001  → "10.0.0.1:8080"  (ephemeral)
         ├── instance-002  → "10.0.0.2:8080"  (ephemeral)
         └── instance-003  → "10.0.0.3:8080"  (ephemeral)

1. Each service instance creates an ephemeral znode with its address
2. Client calls getChildren(/services/order-service) to get all instances
3. Client picks one (round-robin, random, etc.)
4. If instance-002 crashes → ephemeral node deleted → client is notified via watch
```

**vs Eureka / Consul:** ZooKeeper is CP (Consistent, Partition-tolerant), Eureka is AP (Available, Partition-tolerant). ZooKeeper guarantees you see the **correct** list of servers. Eureka may show stale data but is always available.

---

## 11. Configuration Management

```
/config/database
    ├── host     → "db-primary.internal:5432"
    ├── pool-size → "20"
    └── timeout  → "5000"

1. All application servers read from /config/database/*
2. All set watches on these znodes
3. DevOps updates /config/database/pool-size to "50"
4. ALL application servers instantly get notified
5. They re-read the new value and apply it — no restart needed!
```

**Why not just use a properties file or env vars?**
- Properties file → need to restart all servers
- Env vars → same, need restart
- ZooKeeper → **live config updates** across all servers simultaneously

---

## 12. ZAB Protocol — ZooKeeper Atomic Broadcast

ZAB is the **consensus protocol** that ZooKeeper uses internally (similar to Raft/Paxos but different).

### Two Phases:

#### Phase 1: Leader Election (Recovery)
```
1. Servers exchange their last seen transaction ID (zxid)
2. The server with the highest zxid proposes itself as leader
3. If it gets majority votes → becomes leader
4. New leader syncs all followers to the same state
```

#### Phase 2: Atomic Broadcast (Normal Operation)
```
1. Leader receives write request
2. Leader assigns a zxid (monotonically increasing transaction ID)
3. Leader sends PROPOSAL to all followers
4. Followers write to disk → send ACK
5. Leader gets majority ACKs → sends COMMIT
6. Transaction is applied to in-memory data tree
```

### ZXID (ZooKeeper Transaction ID):

```
ZXID = 64-bit number
     = [epoch (32 bits)] [counter (32 bits)]

epoch   → increments with every new leader election
counter → increments with every transaction

Example: epoch=3, counter=145 → this is the 145th transaction under the 3rd leader
```

**Interview Q:** *"How does ZAB differ from Paxos?"*  
**A:** Paxos is a general consensus algorithm. ZAB is specifically designed for **primary-backup** systems where there's a leader that broadcasts updates. ZAB guarantees **total order** of all transactions and handles leader recovery cleanly. It's simpler for the ZooKeeper use case.

---

## 13. Session Management

When a client connects to ZooKeeper, it establishes a **session**.

```
Client ──── Session (timeout=30s) ────── ZooKeeper Ensemble
   |                                            |
   |── heartbeat (ping) every 10s ──────────>   |
   |<─────────────── pong ──────────────────    |
   |                                            |
   |  (if no heartbeat for 30s → session expires)
   |  → all ephemeral znodes created by this client are DELETED
```

### Session States:

```
CONNECTING → CONNECTED → (DISCONNECTED → CONNECTED) → ... → EXPIRED
                              ↑  reconnect  ↑
```

| State | What's Happening |
|-------|-----------------|
| `CONNECTING` | Client trying to connect to ensemble |
| `CONNECTED` | All good, session is active |
| `DISCONNECTED` | Lost connection, client trying to reconnect. Ephemeral nodes still exist (within timeout). |
| `EXPIRED` | Timed out. All ephemeral nodes deleted. Client must create a new session. |

**Interview Q:** *"What happens if the ZooKeeper server a client is connected to dies?"*  
**A:** The client automatically reconnects to another server in the ensemble. The session is maintained (same session ID). Ephemeral nodes survive. The client just sees a brief DISCONNECTED state.

---

## 14. CAP Theorem — Where Does ZooKeeper Sit?

```
       C (Consistency)
      / \
     /   \
    /  ZK  \        ← ZooKeeper is CP
   /   HERE  \
  /___________\
 A             P
(Availability)  (Partition tolerance)
```

| Aspect | ZooKeeper's Choice |
|--------|-------------------|
| **Consistency** | ✅ Strong — reads see the latest committed write (if you read from leader) |
| **Partition Tolerance** | ✅ Yes — continues working if minority of servers are unreachable |
| **Availability** | ⚠️ Sacrificed — if majority goes down, ZooKeeper becomes **unavailable** (can't form quorum) |

**Interview Q:** *"Is ZooKeeper read always consistent?"*  
**A:** By default, reads may be served by followers which might be slightly behind the leader (stale reads). For guaranteed consistency, use the `sync()` command before reading — this forces the follower to catch up with the leader.

---

## 15. ZooKeeper CLI Commands

```bash
# Connect to ZooKeeper
./zkCli.sh -server localhost:2181

# Create znodes
create /app "mydata"                          # persistent
create -e /app/server1 "alive"                # ephemeral
create -s /app/lock- "data"                   # sequential
create -e -s /app/election/n- "server1"       # ephemeral + sequential

# Read
get /app                          # get data + metadata
ls /app                           # list children
stat /app                         # metadata only

# Update
set /app "newdata"                # update data
set /app "data" -v 3              # update only if version=3 (optimistic lock)

# Delete
delete /app/server1               # delete node (must have no children)
deleteall /app                    # recursive delete

# Watches
get -w /app                       # get data + set watch
ls -w /app                        # list children + set watch

# ACLs
getAcl /app                       # get ACL
setAcl /app world:anyone:cdrwa    # set ACL (create, delete, read, write, admin)
```

---

## 16. Common Interview Questions

### Q1: "What is ZooKeeper and when would you use it?"
**A:** ZooKeeper is a distributed coordination service. I'd use it for: leader election (picking one master among replicas), distributed locking (preventing two services from updating the same resource simultaneously), service discovery (tracking which service instances are alive), and dynamic configuration management (pushing config changes to all servers without restart).

### Q2: "How does ZooKeeper handle network partitions?"
**A:** ZooKeeper is CP. If a network partition splits the cluster, only the side with the **majority** (quorum) continues serving. The minority side becomes read-only / unavailable. This prevents split-brain — you can never have two leaders.

### Q3: "What's the difference between ephemeral and persistent znodes?"
**A:** Persistent znodes survive client disconnection — you have to explicitly delete them. Ephemeral znodes are automatically deleted when the client session expires. Ephemeral znodes are used for heartbeats, membership, and leader election because they naturally handle server crashes.

### Q4: "Why is ZooKeeper not suitable for storing large amounts of data?"
**A:** ZooKeeper keeps **all data in memory** for fast reads. Each znode is limited to 1 MB. The entire data tree should be in the range of MBs, not GBs. It's designed for coordination metadata (small config values, flags, locks), not as a database.

### Q5: "How does ZooKeeper ensure consistency?"
**A:** Through the ZAB protocol. All writes go through the leader. The leader assigns a monotonically increasing transaction ID (zxid), broadcasts to followers, and commits only after majority ACK. This guarantees total order of all state changes.

### Q6: "Can ZooKeeper have split-brain?"
**A:** No. By design, any operation requires a quorum (majority). If the network splits, at most one partition can have the majority. The minority partition cannot process writes. This is the core safety guarantee.

### Q7: "What are the alternatives to ZooKeeper?"
**A:** 
- **etcd** — Used by Kubernetes. Also CP. Uses Raft consensus. Simpler API (key-value, not tree).
- **Consul** — By HashiCorp. Service discovery + KV store + health checking. Can run in CP or AP mode.
- **Kafka KRaft** — Kafka's built-in replacement for ZooKeeper (since Kafka 3.3+). Removes ZooKeeper dependency entirely.

### Q8: "What happens when the ZooKeeper leader dies?"
**A:** 
1. Followers detect leader is gone (missed heartbeats)
2. They start a new leader election (ZAB Phase 1)
3. Server with the highest zxid (most up-to-date) is likely elected
4. New leader syncs all followers to the same state
5. New leader starts accepting writes
6. Entire process takes ~200ms to a few seconds

---

## 17. Quick Reference Table

| Concept | Key Point |
|---------|-----------|
| **ZNode** | Node in ZooKeeper's tree. Has data (≤1MB), version, ACL, children |
| **Ephemeral ZNode** | Auto-deleted when client session expires. No children allowed. |
| **Sequential ZNode** | ZooKeeper appends monotonic counter to name |
| **Watch** | One-time notification on znode change. Must re-register after trigger. |
| **Ensemble** | Cluster of ZooKeeper servers (odd number: 3, 5, 7) |
| **Quorum** | Majority of ensemble. Required for any write. ⌊N/2⌋ + 1 |
| **Leader** | Handles all writes. Elected via ZAB. |
| **Follower** | Serves reads. Votes on writes. |
| **Observer** | Serves reads. Does NOT vote. Scales read throughput. |
| **ZAB** | ZooKeeper Atomic Broadcast — consensus protocol |
| **ZXID** | Transaction ID = epoch (32b) + counter (32b) |
| **Session** | Client connection. Has timeout. Ephemeral nodes tied to it. |
| **CAP** | ZooKeeper is CP — consistent and partition-tolerant |
| **sync()** | Forces follower to catch up with leader before read |
| **1 MB limit** | Max data per znode. ZK is for metadata, not bulk data. |

---

> **Bottom line:** ZooKeeper is the **glue** of distributed systems. It's small, it's boring, but without it, your distributed system has no way to **agree on anything**. And in distributed systems, agreement is everything.

