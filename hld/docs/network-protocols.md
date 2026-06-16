# Network Protocols — HLD Interview (1 Hour)

> **Simulated Interview Format**
> Interviewer asks broad questions → Candidate (you, a 6-7 YoE engineer) answers step by step. The flow mimics a real 60-minute system-design round focused on network protocols.

---

## Table of Contents

1. [Opening — Clarify the Scope (~3 min)](#1-opening--clarify-the-scope-3-min)
2. [The OSI & TCP/IP Models (~5 min)](#2-the-osi--tcpip-models-5-min)
3. [TCP vs UDP (~10 min)](#3-tcp-vs-udp-10-min)
4. [HTTP/1.1, HTTP/2, HTTP/3 (~10 min)](#4-http11-http2-http3-10-min)
5. [HTTPS / TLS (~5 min)](#5-https--tls-5-min)
6. [WebSockets, SSE, Long Polling (~8 min)](#6-websockets-sse-long-polling-8-min)
7. [gRPC & Protocol Buffers (~5 min)](#7-grpc--protocol-buffers-5-min)
8. [Messaging Protocols: MQTT, AMQP, Kafka (~5 min)](#8-messaging-protocols-mqtt-amqp-kafka-5-min)
9. [DNS, IP, Routing Basics (~5 min)](#9-dns-ip-routing-basics-5-min)
10. [Choosing the Right Protocol — Decision Framework (~4 min)](#10-choosing-the-right-protocol--decision-framework-4-min)

---

## 1. Opening — Clarify the Scope (~3 min)

### Interviewer's Question
> "Walk me through the network protocols you'd consider when designing a large-scale distributed system. Compare them, explain trade-offs, and tell me when you'd use each."

### Candidate's Response

Before I jump in, let me set the context:

- Networking protocols span **multiple OSI layers** — I'll cover the ones that matter for system design.
- I'll group them into:
  1. **Transport layer**: TCP, UDP, QUIC.
  2. **Application layer (request/response)**: HTTP/1.1, HTTP/2, HTTP/3, gRPC.
  3. **Application layer (real-time/bi-directional)**: WebSockets, SSE, Long Polling.
  4. **Messaging**: MQTT, AMQP, Kafka protocol.
  5. **Supporting**: DNS, TLS, IP.
- I'll cover **what they are, when to use them, and trade-offs**.

---

## 2. The OSI & TCP/IP Models (~5 min)

```
┌─────────────────────────────────────────────────────────────────┐
│             OSI MODEL (7 layers)        TCP/IP (4 layers)        │
├─────────────────────────────────────────────────────────────────┤
│  7. Application   (HTTP, gRPC, DNS)  │                          │
│  6. Presentation  (TLS, encoding)    │  Application             │
│  5. Session       (sockets)          │                          │
├──────────────────────────────────────┼──────────────────────────┤
│  4. Transport     (TCP, UDP, QUIC)   │  Transport               │
├──────────────────────────────────────┼──────────────────────────┤
│  3. Network       (IP, ICMP)         │  Internet                │
├──────────────────────────────────────┼──────────────────────────┤
│  2. Data Link     (Ethernet, MAC)    │                          │
│  1. Physical      (cables, radio)    │  Network Access (Link)   │
└──────────────────────────────────────┴──────────────────────────┘
```

**Why this matters in interviews:**
- Helps you reason about where a problem lives.
- "TLS handshake slow" → presentation/transport issue.
- "Packet loss" → transport / network.
- "DNS timeout" → application but supporting infrastructure.

### Data Encapsulation

```
   Application Data
        │
        ▼  + HTTP headers
   ┌────────────────┐
   │ HTTP Message    │  (Application)
   └────────┬───────┘
            ▼  + TCP header (port, seq, ack)
   ┌────────────────┐
   │ TCP Segment    │  (Transport)
   └────────┬───────┘
            ▼  + IP header (src/dst IP)
   ┌────────────────┐
   │ IP Packet      │  (Network)
   └────────┬───────┘
            ▼  + Ethernet header (MAC)
   ┌────────────────┐
   │ Frame          │  (Data Link)
   └────────────────┘
            ▼
        Wire / Radio
```

---

## 3. TCP vs UDP (~10 min)

### TCP (Transmission Control Protocol)

**Connection-oriented**, reliable, ordered byte stream.

**3-way handshake:**
```
   Client                                Server
     │  ─── SYN (seq=x) ──────────────►   │
     │                                    │
     │  ◄── SYN-ACK (seq=y, ack=x+1) ──   │
     │                                    │
     │  ─── ACK (ack=y+1) ─────────────►  │
     │                                    │
     │  ←─── DATA EXCHANGE ──→           │
```

**Features:**
- Guaranteed delivery (retransmits lost packets).
- Ordered delivery (sequence numbers).
- Flow control (sliding window).
- Congestion control (slow start, AIMD).
- Full-duplex.

**Cost:**
- Handshake overhead (3 RTTs minimum before data).
- Head-of-line blocking — one lost packet blocks all subsequent ones.
- Higher latency.

### UDP (User Datagram Protocol)

**Connectionless**, fire-and-forget.

```
   Client ──── UDP Datagram ───► Server
   (no handshake, no ack, no retransmit)
```

**Features:**
- No connection setup → minimal latency.
- No retransmission, no ordering → application must handle.
- Smaller header (8 bytes vs TCP's 20+).
- Supports multicast/broadcast.

### Comparison

```
┌────────────────────┬────────────────────┬────────────────────┐
│ Feature            │ TCP                │ UDP                │
├────────────────────┼────────────────────┼────────────────────┤
│ Connection         │ Yes (handshake)    │ No                 │
│ Reliability        │ Guaranteed         │ Best effort        │
│ Ordering           │ Guaranteed         │ Not guaranteed     │
│ Flow control       │ Yes                │ No                 │
│ Congestion control │ Yes                │ No                 │
│ Header size        │ 20-60 bytes        │ 8 bytes            │
│ Speed              │ Slower             │ Faster             │
│ Use cases          │ HTTP, SSH, DB,    │ DNS, VoIP, gaming, │
│                    │ email, file xfer  │ video streaming,    │
│                    │                    │ DHCP, SNMP          │
└────────────────────┴────────────────────┴────────────────────┘
```

### When to use what?

- **TCP**: when correctness > speed. Web, DB, anything where dropped data = corruption.
- **UDP**: when low latency > correctness, or when app can tolerate/fix loss.
  - Live video/audio (a missing frame is fine).
  - DNS (small query, retransmit handled by client).
  - Gaming (latest position matters; old packets are useless).

### QUIC — Modern Alternative

Built on UDP, but adds reliability + multiplexing without TCP's HOL blocking. Powers HTTP/3.

---

## 4. HTTP/1.1, HTTP/2, HTTP/3 (~10 min)

### HTTP/1.1 (1997)

```
   Client ──── GET /resource ──────► Server
   Client ◄─── 200 OK + body ──────  Server
```

**Issues:**
- **One request at a time per connection** (head-of-line blocking).
- Workaround: **pipelining** (rarely used due to bugs) or open **6 parallel connections** per host.
- Plain text headers → verbose.
- No header compression.

**Keep-Alive:** persistent connections reuse TCP socket.

### HTTP/2 (2015)

Key improvements:
- **Binary protocol** (not text).
- **Multiplexing** — many requests over a single TCP connection (no app-level HOL).
- **Header compression** (HPACK).
- **Server push** (server can pre-send resources; mostly deprecated).
- **Stream prioritization**.

```
   Client                Server
     │ ─── Stream 1: GET /a ──►│
     │ ─── Stream 3: GET /b ──►│   ← parallel streams over 1 connection
     │ ─── Stream 5: GET /c ──►│
     │ ◄── Stream 3: data ────│
     │ ◄── Stream 1: data ────│
     │ ◄── Stream 5: data ────│
```

**Limitation:** Still over TCP → if one packet is lost, the entire connection stalls (TCP-level HOL).

### HTTP/3 (2022, RFC 9114)

Runs over **QUIC (UDP)** instead of TCP.

**Improvements:**
- **No TCP HOL blocking** — independent streams in QUIC.
- **0-RTT connection resumption** — repeat visitors skip handshake.
- **Connection migration** — survives network changes (Wi-Fi → 4G).
- Built-in TLS 1.3.

### Comparison

```
┌─────────────────┬────────────┬────────────┬────────────┐
│                 │ HTTP/1.1   │ HTTP/2     │ HTTP/3     │
├─────────────────┼────────────┼────────────┼────────────┤
│ Transport       │ TCP        │ TCP        │ QUIC (UDP) │
│ Multiplexing    │ No         │ Yes        │ Yes        │
│ Header comp.    │ No         │ HPACK      │ QPACK      │
│ TLS             │ Optional   │ De facto   │ Mandatory  │
│ HOL blocking    │ App + TCP  │ TCP only   │ None       │
│ 0-RTT resumption│ No         │ No         │ Yes        │
│ Conn. migration │ No         │ No         │ Yes        │
└─────────────────┴────────────┴────────────┴────────────┘
```

### HTTP Methods & Status Codes (quick recap)

```
GET     — read, idempotent, safe, cacheable
POST    — create/process, NOT idempotent
PUT     — full replace, idempotent
PATCH   — partial update, NOT necessarily idempotent
DELETE  — remove, idempotent
HEAD    — like GET but no body
OPTIONS — query allowed methods (CORS preflight)

2xx Success      — 200 OK, 201 Created, 204 No Content
3xx Redirect     — 301 Moved Permanently, 302 Found, 304 Not Modified
4xx Client Error — 400 Bad Request, 401 Unauthorized, 403 Forbidden,
                   404 Not Found, 409 Conflict, 429 Too Many Requests
5xx Server Error — 500 Internal, 502 Bad Gateway, 503 Unavailable, 504 Timeout
```

---

## 5. HTTPS / TLS (~5 min)

**HTTPS = HTTP over TLS.** TLS (Transport Layer Security) provides:
- **Encryption** (confidentiality).
- **Integrity** (no tampering).
- **Authentication** (you're talking to the real server).

### TLS 1.3 Handshake (1-RTT)

```
   Client                                    Server
     │ ── ClientHello + key share ──────────►│
     │                                        │
     │ ◄── ServerHello + key share +         │
     │     cert + Finished ──────────────────│
     │                                        │
     │ ── Finished + (encrypted data) ─────► │
     │                                        │
     │ ◄── Application data ─────────────────│
```

TLS 1.2 used 2-RTT; TLS 1.3 cuts it to 1-RTT (or 0-RTT for resumption).

### Certificate Validation
1. Server sends X.509 certificate.
2. Client checks chain of trust → root CA.
3. Verifies common name (CN) / SAN matches hostname.
4. Checks expiration, revocation (OCSP/CRL).

### Symmetric vs Asymmetric
- **Asymmetric** (RSA, ECDHE): used in handshake for key exchange.
- **Symmetric** (AES-GCM, ChaCha20): used for bulk encryption (faster).

---

## 6. WebSockets, SSE, Long Polling (~8 min)

When you need **server → client push** or **bi-directional** communication, you can't rely on plain request/response. Options:

### 6.1 Short Polling

```
   Client: GET /messages ── (every 5s) ──► Server
   Server: returns latest or empty
```
- ❌ Wasteful, high latency, scales poorly.

### 6.2 Long Polling

```
   Client: GET /messages ──────────────► Server
                                          (holds connection)
                                          ...waits for data...
   Client: ◄── data (when available) ── Server
   Client: immediately re-polls
```
- ✅ Real-time-ish, works over HTTP/1.1.
- ❌ Connection overhead, single direction (server → client), tricky timeouts.

### 6.3 Server-Sent Events (SSE)

One-way **server → client** stream over HTTP. Text-based event stream.

```
   Client: GET /events  (Accept: text/event-stream)
   Server: keeps connection open, pushes:
       event: message
       data: {"user":"alice","msg":"hi"}

       event: message
       data: {"user":"bob","msg":"hello"}
```
- ✅ Simple, runs over HTTP/2.
- ✅ Auto-reconnect built into browsers.
- ❌ One-way only (client → server needs separate request).
- ❌ Text only (no binary).
- ❌ Some proxies/firewalls drop long-lived HTTP.

### 6.4 WebSockets (RFC 6455)

**Full-duplex** persistent connection. Starts as HTTP, **upgrades** to WS protocol.

```
   Client: GET /chat HTTP/1.1
           Upgrade: websocket
           Connection: Upgrade
           Sec-WebSocket-Key: ...

   Server: HTTP/1.1 101 Switching Protocols
           Upgrade: websocket
           Sec-WebSocket-Accept: ...

   ── WebSocket frames (binary or text) flow both ways ──
```

- ✅ Bi-directional, low overhead per message.
- ✅ Binary support.
- ❌ Stateful — harder to scale (sticky sessions or shared state).
- ❌ Requires special LB config (e.g., AWS ALB supports it).
- ❌ Reconnection logic is your problem.

### Comparison

```
┌──────────────────┬─────────────���────────────┬────────────┬──────────────┐
│                  │ Short Poll  │ Long Poll  │ SSE        │ WebSockets   │
├──────────────────┼─────────────┼────────────┼────────────┼──────────────┤
│ Direction        │ C→S, S→C    │ C→S, S→C   │ S→C only   │ Bi-directional│
│ Real-time        │ Poor        │ Decent     │ Good       │ Excellent    │
│ Overhead         │ High        │ Medium     │ Low        │ Lowest       │
│ Binary support   │ Yes (HTTP)  │ Yes        │ No         │ Yes          │
│ Browser support  │ All         │ All        │ All modern │ All modern   │
│ Use case         │ Status pages│ Notif      │ Stock feed,│ Chat, gaming,│
│                  │             │            │ live score │ trading      │
└──────────────────┴─────────────┴────────────┴────────────┴──────────────┘
```

---

## 7. gRPC & Protocol Buffers (~5 min)

### What is gRPC?

- **High-performance RPC framework** by Google.
- Runs over **HTTP/2**.
- Uses **Protocol Buffers (protobuf)** as IDL and binary serialization.
- Supports streaming (unary, server-streaming, client-streaming, bi-directional).

### Example Proto

```protobuf
syntax = "proto3";

service UserService {
  rpc GetUser (GetUserRequest) returns (User);
  rpc ListUsers (ListUsersRequest) returns (stream User);  // server stream
}

message GetUserRequest { string id = 1; }
message User { string id = 1; string name = 2; int32 age = 3; }
```

### Why gRPC?

- ✅ **Smaller payloads** — binary protobuf is 3-10x smaller than JSON.
- ✅ **Faster** — HTTP/2 + binary + multiplexed streams.
- ✅ **Strongly typed** — code-generated clients in 10+ languages.
- ✅ **Streaming** built in.
- ✅ Excellent for **service-to-service** in microservices.

### When NOT to use gRPC

- ❌ Browser clients (limited gRPC-Web support, needs proxy).
- ❌ Public APIs (REST/JSON more familiar).
- ❌ Human-debuggable APIs (binary harder to inspect).

### REST vs gRPC

```
┌──────────────┬──────────────────────┬──────────────────────┐
│              │ REST                 │ gRPC                 │
├──────────────┼──────────────────────┼──────────────────────┤
│ Transport    │ HTTP/1.1, 2          │ HTTP/2               │
│ Payload      │ JSON (text)          │ Protobuf (binary)    │
│ Contract     │ OpenAPI (loose)      │ .proto (strict)      │
│ Streaming    │ No (or SSE/WS)       │ Yes (4 modes)        │
│ Browser      │ Native               │ Needs gRPC-Web       │
│ Latency      │ Higher               │ Lower                │
│ Best for     │ Public APIs, simple  │ Microservices, low   │
│              │ CRUD                 │ latency, polyglot    │
└──────────────┴──────────────────────┴──────────────────────┘
```

---

## 8. Messaging Protocols: MQTT, AMQP, Kafka (~5 min)

### MQTT (Message Queuing Telemetry Transport)

- Pub/Sub, runs over TCP.
- **Tiny header** (2 bytes minimum) → great for **IoT**, low-bandwidth.
- 3 QoS levels: 0 (at most once), 1 (at least once), 2 (exactly once).
- Used by: AWS IoT, Azure IoT Hub, automotive, smart home.

### AMQP (Advanced Message Queuing Protocol)

- Used by **RabbitMQ**.
- Rich routing (exchanges, queues, bindings).
- Reliable, transactional messaging.
- Good for **enterprise integration**, complex routing.

### Kafka Protocol

- **TCP-based binary protocol**.
- Durable, partitioned log.
- High throughput (millions msgs/sec).
- Consumers track their own offset.
- Used for: event streaming, log aggregation, real-time analytics.

```
┌────────────────┬──────────────┬────────────────┬────────────────┐
│                │ MQTT         │ AMQP/RabbitMQ  │ Kafka          │
├────────────────┼──────────────┼────────────────┼────────────────┤
│ Model          │ Pub/Sub      │ Queue + Pub/Sub│ Distributed log│
│ Throughput     │ Medium       │ High           │ Very high      │
│ Latency        │ Low          │ Low            │ Low-medium     │
│ Persistence    │ Optional     │ Yes            │ Always (log)   │
│ Use case       │ IoT, mobile  │ Enterprise msg │ Event streaming│
└────────────────┴──────────────┴────────────────┴────────────────┘
```

---

## 9. DNS, IP, Routing Basics (~5 min)

### DNS Resolution Flow

```
   Browser wants: www.example.com
        │
        ▼
   1. Browser cache?  ─── miss
        │
        ▼
   2. OS cache?  ─── miss
        │
        ▼
   3. Recursive resolver (ISP / 1.1.1.1 / 8.8.8.8)
        │
        ├─► 4. Root nameserver  → "ask .com TLD"
        ├─► 5. .com TLD          → "ask example.com NS"
        └─► 6. example.com NS    → "192.0.2.1"
        │
        ▼
   IP returned → cached at each level (TTL)
```

### DNS Record Types

| Record | Purpose                                  |
|--------|------------------------------------------|
| A      | hostname → IPv4                          |
| AAAA   | hostname → IPv6                          |
| CNAME  | alias to another hostname                |
| MX     | mail server for the domain               |
| TXT    | arbitrary text (SPF, DKIM, verification) |
| NS     | nameservers for the zone                 |
| SRV    | service location (port + host)           |

### CDN & DNS-based Load Balancing
- **GeoDNS**: return nearest edge IP based on resolver location.
- **Anycast**: same IP advertised from multiple locations; routing picks nearest.
- **Health-aware DNS**: Route 53 with health checks.

### IP & Subnetting (brief)
- IPv4: 32-bit (e.g., 192.168.1.1).
- IPv6: 128-bit.
- CIDR notation: `10.0.0.0/16` = 65536 addresses.
- Private ranges: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16.

---

## 10. Choosing the Right Protocol — Decision Framework (~4 min)

```
                  ┌─────────────────────────┐
                  │  What's the use case?    │
                  └────────────┬─────────────┘
                               │
        ┌──────────────────────┼─────────────────────┐
        │                      │                     │
   Request/Response      Real-time push        Messaging /
        │                      │                  Event-driven
        ▼                      ▼                     ▼
   ┌────────────┐        ┌────────────┐        ┌─────────────┐
   │ External?  │        │ Direction? │        │ Throughput? │
   └─────┬──────┘        └─────┬──────┘        └──────┬──────┘
         │                     │                      │
    ┌────┼────┐         ┌──────┼──────┐        ┌─────┼─────┐
    │         │         │      │      │        │           │
   YES       NO       S→C    Bi-dir   │      Low/Med    Very High
    │         │         │      │      │        │           │
    ▼         ▼         ▼      ▼      │        ▼           ▼
   REST    gRPC        SSE   WebSocket│     RabbitMQ     Kafka
   /JSON  (HTTP/2)                    │     (AMQP)
                                      │
                              ┌───────┴───────┐
                              │ IoT/low-power?│
                              │      MQTT     │
                              └───────────────┘
```

### Quick Cheat Sheet

| Need | Protocol |
|------|----------|
| Reliable bytes between two endpoints | TCP |
| Fast best-effort delivery | UDP |
| Modern web requests | HTTP/2 or /3 |
| Lossy/mobile network optimization | HTTP/3 (QUIC) |
| Microservice RPC | gRPC |
| Server → browser push | SSE |
| Bi-directional real-time | WebSocket |
| IoT telemetry | MQTT |
| Enterprise messaging w/ routing | AMQP/RabbitMQ |
| Event streaming at scale | Kafka |
| Encrypted transport | TLS 1.3 |
| Service discovery / lookup | DNS |

---

## Common Interview Follow-up Questions

**Q: Why does HTTP/2 multiplexing solve some problems but not all?**
> It solves application-level HOL blocking but not TCP-level. A single TCP packet loss stalls all streams. HTTP/3 (QUIC over UDP) solves this with independent streams.

**Q: When would you prefer UDP for a custom protocol?**
> When the application can either tolerate loss (live media) or implements its own reliability/ordering (QUIC, real-time game engines).

**Q: How do WebSockets scale?**
> They are stateful. Either:
> - Sticky sessions at LB (simple but uneven load).
> - Stateless tier + Redis Pub/Sub for cross-node messaging.
> - Dedicated WS gateway (e.g., AWS API Gateway WebSocket, Pusher, Ably).

**Q: Why does gRPC need HTTP/2?**
> For multiplexed streams, binary framing, header compression, and bidirectional streaming primitives.

**Q: How do you debug a TLS handshake failure?**
> `openssl s_client -connect host:443 -showcerts`, check cert chain, expiration, SNI, cipher mismatch, TLS version mismatch.

---

## Appendix: Quick Reference Card

```
┌────────────────────────────────────────────────────────────────┐
│              NETWORK PROTOCOLS CHEAT SHEET                     │
├────────────────────────────────────────────────────────────────┤
│ TCP   → reliable, ordered, slower (web, DB, SSH)               │
│ UDP   → fast, lossy (DNS, VoIP, gaming)                        │
│ QUIC  → UDP + reliability + multiplexing (HTTP/3)              │
│                                                                │
│ HTTP/1.1 → text, one req/conn (legacy)                          │
│ HTTP/2   → binary, multiplex, HPACK (TCP)                       │
│ HTTP/3   → over QUIC, 0-RTT, no HOL                             │
│ HTTPS    → HTTP + TLS 1.3 (1-RTT handshake)                    │
│                                                                │
│ SSE        → server push, one-way, HTTP                        │
│ WebSocket  → bi-dir, persistent, low overhead                  │
│ Long Poll  → fallback when WS/SSE blocked                      │
│                                                                │
│ gRPC  → HTTP/2 + protobuf, microservices                       │
│ REST  → HTTP + JSON, public APIs                                │
│                                                                │
│ MQTT  → tiny, IoT, Pub/Sub                                      │
│ AMQP  → RabbitMQ, enterprise routing                            │
│ Kafka → distributed log, high throughput                        │
│                                                                │
│ DNS   → name → IP (cached at many layers, TTLs)                │
│ TLS   → encrypt + auth + integrity                              │
└────────────────────────────────────────────────────────────────┘
```

---

*This document simulates a complete 1-hour HLD interview on Network Protocols, from layer fundamentals to choosing the right protocol for each scenario.*

