# HLD Interview Prep — Topic Index

A growing collection of 1-hour HLD interview simulations, each written from the perspective of a 6-7 YoE software engineer. Every doc follows the same format: clarification → theory → patterns → real-world examples → trade-offs → cheat sheet → common follow-up Q&A.

## Topics

| # | Topic | File |
|---|-------|------|
| 1 | Distributed Cache & Caching Strategies | [`distributed-cache-and-caching-strategies.md`](./distributed-cache-and-caching-strategies.md) |
| 2 | Handling Distributed Transactions (2PC, Saga, Outbox) | [`distributed-transactions.md`](./distributed-transactions.md) |
| 3 | Network Protocols (TCP/UDP, HTTP/1-2-3, gRPC, WS, MQTT...) | [`network-protocols.md`](./network-protocols.md) |
| 4 | CAP Theorem & PACELC | [`cap-theorem.md`](./cap-theorem.md) |
| 5 | Microservices Design Patterns | [`microservices-design-patterns.md`](./microservices-design-patterns.md) |
| 6 | Consistent Hashing | [`consistent-hashing.md`](./consistent-hashing.md) |
| 7 | Designing Idempotent APIs | [`idempotency.md`](./idempotency.md) |
| 8 | Database Indexing (B+Tree, LSM, GIN, BRIN, query planning) | [`database-indexing.md`](./database-indexing.md) |
| 9 | Concurrency Control — Optimistic & Pessimistic Locking | [`concurrency-control-distributed-systems.md`](./concurrency-control-distributed-systems.md) |

## How to Use

- Read top-to-bottom for a structured study session (~1 hour each).
- Each doc ends with a **cheat sheet** for quick pre-interview review.
- The **Common Follow-up Questions** section mimics real interviewer probes.
- ASCII diagrams keep everything portable and reviewable in any editor.

## Cross-References

- `distributed-transactions.md` references `idempotency.md` for the Saga compensation patterns.
- `microservices-design-patterns.md` references `distributed-transactions.md` and `idempotency.md`.
- `consistent-hashing.md` complements `distributed-cache-and-caching-strategies.md` (used by Memcached/Cassandra/DynamoDB).
- `cap-theorem.md` underpins decisions in `distributed-transactions.md` and `microservices-design-patterns.md`.

