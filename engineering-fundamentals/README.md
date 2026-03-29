# 📚 Engineering Fundamentals — Interview Prep

Cheat sheets and deep dives on tools, concepts, and technologies every senior engineer should know.

> This isn't about LLD or design patterns — it's about the **tools you use every day** and the **questions you get asked about them**.

---

## 📁 Folder Structure

```
docs/
├── build-tools-and-vcs/          🔧 Maven, Git
├── docker-and-cicd/              🐳 Docker, CI/CD Pipelines
├── java-internals/               ☕ Concurrency, JVM, GC
├── messaging-and-coordination/   📮 RabbitMQ, ZooKeeper, Kafka
└── databases-and-storage/        🗄️ HBase, Aerospike
```

---

## 📄 Docs

### 🔧 Build Tools & Version Control — `docs/build-tools-and-vcs/`

| Doc | What It Covers | Read Time |
|-----|---------------|-----------|
| [**Maven Interview Cheatsheet**](docs/build-tools-and-vcs/maven-interview-cheatsheet.md) | Quick-fire: lifecycle, scopes, commands, POM structure, dependency conflicts | 5 min |
| [**Maven Deep Dive**](docs/build-tools-and-vcs/maven-deep-dive.md) | Exhaustive: every POM tag, reactor, BOMs, profiles, plugins, settings.xml, troubleshooting | 30 min |
| [**Git Important Commands**](docs/build-tools-and-vcs/git-important-commands.md) | All essential commands with examples: rebase, cherry-pick, bisect, reflog, stash, reset, interactive rebase, interview scenarios | 25 min |

### 🐳 Docker & CI/CD — `docs/docker-and-cicd/`

| Doc | What It Covers | Read Time |
|-----|---------------|-----------|
| [**Docker Interview Cheatsheet**](docs/docker-and-cicd/docker-interview-cheatsheet.md) | Quick-fire: containers vs VMs, Dockerfile, commands, networking, multi-stage builds, compose | 5 min |
| [**Docker Deep Dive**](docs/docker-and-cicd/docker-deep-dive.md) | Exhaustive: architecture, layers, caching, networking, volumes, compose, security, debugging, production Dockerfiles | 30 min |
| [**CI/CD Interview Cheatsheet**](docs/docker-and-cicd/cicd-interview-cheatsheet.md) | Quick-fire: CI vs CD, pipeline stages, GitHub Actions, Jenkins, deployment strategies (blue-green, canary) | 5 min |
| [**CI/CD Deep Dive**](docs/docker-and-cicd/cicd-deep-dive.md) | Exhaustive: GitHub Actions, Jenkins, GitLab CI, GitOps, ArgoCD, testing strategy, secrets, IaC, DevSecOps, rollback | 30 min |

### ☕ Java Internals — `docs/java-internals/`

| Doc | What It Covers | Read Time |
|-----|---------------|-----------|
| [**Java Concurrency Interview Cheatsheet**](docs/java-internals/java-concurrency-interview-cheatsheet.md) | Quick-fire: threads, synchronized, volatile, ExecutorService, CompletableFuture, locks, virtual threads | 5 min |
| [**Java Concurrency Deep Dive**](docs/java-internals/java-concurrency-deep-dive.md) | Exhaustive: JMM, happens-before, ReentrantLock, StampedLock, atomics, Fork/Join, virtual threads, structured concurrency, patterns | 35 min |
| [**JVM Internals Interview Cheatsheet**](docs/java-internals/jvm-internals-interview-cheatsheet.md) | Quick-fire: memory model, heap/stack, GC algorithms, JIT compilation, class loading, JVM flags | 5 min |
| [**JVM Internals Deep Dive**](docs/java-internals/jvm-internals-deep-dive.md) | Exhaustive: class loading, heap generations, G1/ZGC, JIT tiered compilation, escape analysis, JFR, containers, GraalVM, tuning | 35 min |

### 📮 Messaging & Coordination — `docs/messaging-and-coordination/`

| Doc | What It Covers | Read Time |
|-----|---------------|-----------|
| [**RabbitMQ Interview Cheatsheet**](docs/messaging-and-coordination/rabbitmq-interview-cheatsheet.md) | Quick-fire: AMQP, exchanges (direct/fanout/topic), queues, ACK, DLQ, RabbitMQ vs Kafka | 5 min |
| [**RabbitMQ Deep Dive**](docs/messaging-and-coordination/rabbitmq-deep-dive.md) | Exhaustive: exchange types, reliability, publisher confirms, DLX, quorum queues, streams, Spring Boot integration, clustering | 30 min |
| [**ZooKeeper Deep Dive**](docs/messaging-and-coordination/zookeeper-deep-dive.md) | ZNodes, watches, ZAB protocol, leader election, distributed locking, service discovery, CAP theorem, interview Q&A | 25 min |
| [**ZooKeeper & Kafka**](docs/messaging-and-coordination/zookeeper-and-kafka.md) | Broker registration, controller election, ISR, metadata flow, KRaft mode, ZK→KRaft migration, interview Q&A | 30 min |

### 🗄️ Databases & Storage — `docs/databases-and-storage/`

| Doc | What It Covers | Read Time |
|-----|---------------|-----------|
| [**HBase Interview Cheatsheet**](docs/databases-and-storage/hbase-interview-cheatsheet.md) | Quick-fire: data model, architecture, read/write path, RowKey design, compaction, HBase vs Cassandra | 5 min |
| [**HBase Deep Dive**](docs/databases-and-storage/hbase-deep-dive.md) | Exhaustive: regions, WAL, MemStore, HFiles, compaction, Bloom filters, Phoenix, coprocessors, tuning, production best practices | 35 min |
| [**Aerospike Interview Cheatsheet**](docs/databases-and-storage/aerospike-interview-cheatsheet.md) | Quick-fire: data model, SSD architecture, partitioning, consistency modes, Aerospike vs Redis/Cassandra | 5 min |
| [**Aerospike Deep Dive**](docs/databases-and-storage/aerospike-deep-dive.md) | Exhaustive: storage engine, write/read path, Smart Client, XDR, secondary indexes, TTL, Java client, monitoring, tuning | 35 min |

---

## 🔜 Coming Soon

### 🗄️ Databases & SQL

| Topic | What To Cover |
|-------|--------------|
| **RDBMS Fundamentals** | ACID, normalization (1NF–BCNF), joins (inner/outer/cross/self), indexes (B-tree, composite, covering), execution plans, query optimization |
| **PostgreSQL / MySQL Deep Dive** | Storage engines (InnoDB vs MyISAM), MVCC, WAL, replication (master-slave, master-master), partitioning, connection pooling |
| **Database Indexing** | B-tree, B+tree, hash index, GIN, GiST, composite indexes, covering indexes, index-only scans, when NOT to index |
| **SQL Query Optimization** | EXPLAIN plans, slow query analysis, N+1 problem, denormalization trade-offs, pagination (offset vs cursor) |
| **Database Transactions & Isolation** | Read committed, repeatable read, serializable, phantom reads, dirty reads, deadlocks, optimistic vs pessimistic locking |
| **Redis Deep Dive** | Data structures, persistence (RDB/AOF), replication, sentinel, clustering, Lua scripts, pub/sub, eviction policies |
| **MongoDB** | Document model, aggregation pipeline, sharding, replica sets, indexing, transactions, schema design patterns |

### 📮 Messaging & Streaming

| Topic | What To Cover |
|-------|--------------|
| **Kafka Deep Dive** | Producers, consumers, consumer groups, partitioning, exactly-once semantics, log compaction, Kafka Streams, Connect |
| **Event-Driven Architecture** | Event sourcing, CQRS, saga pattern, outbox pattern, eventual consistency patterns |

### ☁️ Infrastructure & DevOps

| Topic | What To Cover |
|-------|--------------|
| **Kubernetes** | Pods, Deployments, Services, ConfigMaps, Secrets, Ingress, HPA, networking, Helm, debugging |
| **Linux Essentials** | Process management, file permissions, networking (netstat, ss, iptables), cron, systemd, shell scripting, log analysis |
| **Networking for Devs** | HTTP/1.1 vs HTTP/2 vs HTTP/3, gRPC, WebSockets, TLS/SSL handshake, DNS resolution, TCP/UDP, load balancing algorithms |
| **Terraform / IaC** | State management, modules, providers, remote backends, drift detection, best practices |

### ☕ Java & Spring Ecosystem

| Topic | What To Cover |
|-------|--------------|
| **Spring Boot Internals** | Auto-configuration, starter mechanism, actuator, profiles, bean lifecycle, dependency injection, AOP |
| **Spring Security** | Authentication vs authorization, OAuth2, JWT, CORS, CSRF, method-level security, filter chain |
| **Java Collections Internals** | HashMap internals (buckets, rehashing, treeification), ConcurrentHashMap, LinkedHashMap, TreeMap, WeakHashMap |
| **Java Streams & Functional** | map/filter/reduce, collectors, parallel streams pitfalls, Optional best practices |
| **Java 17/21 Features** | Records, sealed classes, pattern matching, text blocks, virtual threads, structured concurrency |

### 🏗️ Architecture & Distributed Systems

| Topic | What To Cover |
|-------|--------------|
| **API Design** | REST best practices, versioning, pagination, rate limiting, idempotency, HATEOAS, GraphQL vs REST vs gRPC |
| **Microservices Patterns** | Service discovery, circuit breaker, bulkhead, retry, timeout, sidecar, API gateway, distributed tracing |
| **Caching Strategies** | Cache-aside, read-through, write-through, write-behind, cache invalidation, distributed cache, CDN caching |
| **System Design Building Blocks** | Consistent hashing, bloom filters, rate limiter, distributed locks, leader election, merkle trees |
| **CAP Theorem & Consistency** | CAP, PACELC, eventual consistency, quorum reads/writes, vector clocks, conflict resolution |
| **Observability** | Logging (ELK/Loki), metrics (Prometheus/Grafana), tracing (Jaeger/Zipkin), alerting strategies |

### 🔒 Security & Auth

| Topic | What To Cover |
|-------|--------------|
| **OAuth2 & OpenID Connect** | Authorization code flow, PKCE, client credentials, token refresh, scopes, ID vs access tokens |
| **OWASP Top 10** | SQL injection, XSS, CSRF, SSRF, broken authentication, security headers, input validation |

### 📐 Software Engineering Practices

| Topic | What To Cover |
|-------|--------------|
| **Testing Strategy** | Unit (JUnit 5, Mockito), integration (TestContainers), E2E, TDD, BDD, test pyramid, mutation testing |
| **12-Factor App** | Codebase, dependencies, config, backing services, build-release-run, processes, port binding, disposability |
| **Agile & Estimation** | Story points, sprint planning, retrospectives, tech debt management, CI/CD culture |

---

## 💡 Why "Engineering Fundamentals"?

In interviews, they don't just ask "design a parking lot." They also ask:

- *"How do you manage dependencies in a multi-module project?"* → Maven
- *"How would you resolve a merge conflict?"* → Git
- *"How does Kafka know which broker is the leader?"* → ZooKeeper
- *"What is KRaft and why is Kafka removing ZooKeeper?"* → ZooKeeper & Kafka
- *"How do you optimize your Docker image size?"* → Docker
- *"What happens when you type `mvn clean install`?"* → Build tools
- *"Explain thread pool sizing for I/O-bound vs CPU-bound tasks."* → Java Concurrency
- *"What GC would you use for a low-latency trading system?"* → JVM Internals
- *"How does RabbitMQ guarantee no message loss?"* → RabbitMQ
- *"How would you design the RowKey for a time-series table?"* → HBase
- *"Why is Aerospike faster than Cassandra on reads?"* → Aerospike
- *"Explain blue-green vs canary deployments."* → CI/CD
- *"What's the difference between clustered and non-clustered index?"* → RDBMS
- *"How does MVCC work in PostgreSQL?"* → Databases
- *"Explain optimistic vs pessimistic locking."* → Transactions
- *"How do you handle cache invalidation?"* → Caching
- *"What is the circuit breaker pattern?"* → Microservices

This module is for those questions.
