# 📚 Engineering Fundamentals — Interview Prep

Cheat sheets and deep dives on tools, concepts, and technologies every senior engineer should know.

> This isn't about LLD or design patterns — it's about the **tools you use every day** and the **questions you get asked about them**.

---

## 📄 Docs

| Doc | What It Covers | Read Time |
|-----|---------------|-----------|
| [**Maven Interview Cheatsheet**](docs/maven-interview-cheatsheet.md) | Quick-fire: lifecycle, scopes, commands, POM structure, dependency conflicts | 5 min |
| [**Maven Deep Dive**](docs/maven-deep-dive.md) | Exhaustive: every POM tag, reactor, BOMs, profiles, plugins, settings.xml, troubleshooting | 30 min |
| [**Git Important Commands**](docs/git-important-commands.md) | All essential commands with examples: rebase, cherry-pick, bisect, reflog, stash, reset, interactive rebase, interview scenarios | 25 min |
| [**ZooKeeper Deep Dive**](docs/zookeeper-deep-dive.md) | ZNodes, watches, ZAB protocol, leader election, distributed locking, service discovery, CAP theorem, interview Q&A | 25 min |
| [**ZooKeeper & Kafka**](docs/zookeeper-and-kafka.md) | Broker registration, controller election, ISR, metadata flow, KRaft mode, ZK→KRaft migration, interview Q&A | 30 min |

---

## 🔜 Coming Soon

- Docker — Dockerfile best practices, multi-stage builds, compose
- CI/CD — GitHub Actions, Jenkins pipelines
- Linux — Commands every dev should know
- Concurrency in Java — Threads, Executors, CompletableFuture, Virtual Threads
- JVM Internals — GC, Memory model, Class loading
- Kafka Deep Dive — Producers, consumers, partitioning, exactly-once semantics

---

## 💡 Why "Engineering Fundamentals"?

In interviews, they don't just ask "design a parking lot." They also ask:

- *"How do you manage dependencies in a multi-module project?"* → Maven
- *"How would you resolve a merge conflict?"* → Git
- *"How does Kafka know which broker is the leader?"* → ZooKeeper
- *"What is KRaft and why is Kafka removing ZooKeeper?"* → ZooKeeper & Kafka
- *"How do you optimize your Docker image size?"* → Docker
- *"What happens when you type `mvn clean install`?"* → Build tools

This module is for those questions.

