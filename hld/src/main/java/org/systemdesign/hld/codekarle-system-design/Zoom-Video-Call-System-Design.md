# 🎥 Zoom / WhatsApp / FB Video Calling — System Design Interview Notes

> Source:
- [Github Design Images](https://github.com/codekarle/system-design/blob/master/system-design-prep-material/architecture-diagrams/Zoom%20System%20Design.png)
- [Blog](https://www.codekarle.com/system-design/Zoom-system-design.html)
- [YouTube](https://www.youtube.com/watch?v=G32ThJakeHk&list=PLhgw50vUymycJPN6ZbGTpVKAJ0cL4OEH3&index=11)

It also covers extending the design into a **live-streaming** platform at the end.

> 📌 Prerequisite: This design **builds on the WhatsApp Chat System Design**. The whole
> signalling/handshake flow reuses the `WebSocket Handler` + `WebSocket Manager` from that
> design. Skim the WhatsApp note first if the chat parts feel unfamiliar.

---

## ⚡ 30-Second Interview Cheat Sheet (Read This First!)

> **The core problem:** *"Let two (or many) people exchange live audio/video with almost-zero
> lag, at global scale, while tolerating a little data loss."*

- 🚀 **UDP, not TCP, for the video/audio stream.** Latency > reliability. TCP's 3-way handshake, ACKs, retries, congestion control and 20-byte header add lag. UDP is lossy + out-of-order but fast (8-byte header). A few dropped/reordered frames on a call = nobody cares.
- 🔌 **TCP/WebSocket is still used for control/signalling** (call setup, "who's online", IP exchange). **Only the media stream rides UDP.**
- 🤝 **The whole call-setup dance = WebRTC.** Memorize the 3 WebRTC pieces:
  - **Signalling** = the `WebSocket Handler` handshake (offer/accept, exchange IPs & codecs).
  - **STUN Server** = the `Connector` — tells each client its **own public IP:port** (punches through NAT).
  - **TURN Server** = the `Call Server` — a **relay** used when peer-to-peer fails.
- 🌍 **Default to Peer-to-Peer (P2P)** — lower latency + cheaper (server never touches media). Fall back to **Call Server (TURN)** when P2P is impossible.
- 🧱 **Why P2P fails → use TURN:** (1) Firewall blocks UDP, (2) **Symmetric NAT** blocks the peer, (3) no public IP, (4) **recording required**, (5) **large group**.
- 👥 **Groups:** small (≤ ~5, dynamic) → P2P mesh; large → route through **Call Server** (server aggregates + **transcodes** per receiver's bandwidth). Mesh cost per user = **(N-1)×** bandwidth → explodes with N.
- 🔐 **P2P is still secure** — clients exchange crypto keys during signalling → end-to-end encrypted.
- 🤔 **CAP tradeoff:** sacrifice **Consistency** (data loss OK) to win **Latency + Availability**.
- 🧠 **Mnemonic:** *"**S**ignal · **S**TUN · **T**URN"* = **S**ignalling(WebSocket) → **S**TUN(Connector/find my IP) → **T**URN(Call Server/relay).

---

## 🧭 How to Approach This in an Interview (My Playbook)

1. **State requirements** → 1:1 call, group call, audio/video/screen-share, recording. NFRs: **super-low latency**, high availability, **data loss acceptable**.
2. **Justify UDP over TCP** early — this is the signature insight the interviewer wants. Explain the TCP overhead (handshake, ACK/retry, congestion control, header) vs UDP (lossy, fast).
3. **Reuse the WhatsApp chat backbone** for signalling (WebSocket Handler + Manager). Don't reinvent presence/chat.
4. **Walk the WebRTC flow**: Signalling → STUN (get public IP) → exchange IPs/codecs → try **P2P** → fall back to **TURN (Call Server)**.
5. **Explain the NAT/public-IP problem** and how STUN solves it (router reflects your `A.B.C.D:Port`).
6. **Then scale to groups** (mesh vs Call Server + transcoding) and **recording** (Logger → File Creator → S3).
7. **Finish with live-streaming extension** (Input Manager → transcoders → CDN-like Call Server fan-out close to users).
8. Sprinkle in **analytics via Kafka** and **client-side intelligence** (dynamic switch P2P↔TURN, bandwidth renegotiation).

---

## 1. Requirements

### ✅ Functional
| # | Feature | Notes |
|---|---------|-------|
| 1 | **1:1 video/audio call** | Core |
| 2 | **Group call** (5–10+ people) | See each other's video/screen |
| 3 | **Audio / Video / Screen-share** | Screen-share == video, just a different **input source** (screen vs camera). Same pipeline. |
| 4 | **Recording** | Zoom-style. Personal apps (WhatsApp) usually don't. |

### 🚦 Non-Functional (this drives every decision)
| Property | Requirement | Consequence |
|----------|-------------|-------------|
| ⚡ **Latency** | **Must feel instantaneous** — a lag on a live call is a dealbreaker (worse than YouTube buffering). | → choose **UDP** |
| 🟢 **Availability** | Very high, fault-tolerant, geo-distributed | → global Call Servers / CDN-style fan-out |
| 🧩 **Consistency** | **Data loss is OK** — a few missing/late frames are unnoticeable | → sacrifice for speed |

> 🎯 **CAP triangle for a call:** prioritize **Latency + Availability**, willingly **drop Consistency**.

---

## 2. Foundation: TCP vs UDP (WHY UDP?)

### 🐢 TCP — reliable but heavy (bad for live media)
```
Client                         Server
  | --- SYN  (can I connect?) --> |
  | <-- SYN-ACK (yes) ----------- |   3-way handshake (setup cost)
  | --- ACK  (great, connected)-> |
  |                               |
  | --- Packet P1 -------------->  |
  | <-- ACK P1 ------------------  |   every packet ACKed
  | --- P1 again (no ACK? retry)-> |   retransmission = lag
```
- ✅ Lossless, **ordered** (packets numbered & reassembled), congestion control.
- ❌ Handshake + ACK + retries + **congestion control deliberately slows sending** + **~20-byte header**.
- ❌ For live video this overhead = lag. **Not worth it.**

### 🐇 UDP — lossy but fast (perfect for live media)
```
Client                         Server
  | --- P1 --> got P1
  | --- P2 --> (dropped! never resent)
  | --- P3 --> got P3        Server has P1, P3 only, maybe out of order
```
- ✅ **~8-byte header**, no handshake, no ACK/retry, no congestion throttling → **fast**.
- ❌ **Lossy** + packets may arrive **out of order**. For video = totally fine.
- 📌 **Rule:** media stream → **UDP**. Everything else (call setup, presence, APIs) → **TCP / WebSocket / HTTPS**.

> 🧱 HTTP, HTTPS and WebSocket are all built on **TCP**. Only the raw media path escapes to UDP.

---

## 3. The NAT / Public-IP Problem (Why STUN Exists)

To send a UDP packet, **U1 needs U2's public `IP:Port`** and vice-versa. But **~70% of devices are on IPv4** behind a **home router → ISP router**, so they only have a **private IP**. They don't know their own public IP.

### 🔎 STUN (the `Connector`) reflects your public IP back to you
```
   Private devices            Home/ISP Router (NAT)        STUN / Connector (1.2.3.4)
 ┌──────────────────┐         Public IP = A.B.C.D
 │ Laptop 192.168.1.1│ --portP1--> [ NAT ] --------------->  "Request came from
 │ Mobile 192.168.1.2│ --portP2--> [ NAT ] --------------->   A.B.C.D:P1"
 │ TV     192.168.1.3│ --portP3--> [ NAT ] --------------->  replies to A.B.C.D:P1
 └──────────────────┘                                       NAT maps P1 back to Laptop
```
- Device asks Connector "what's my public IP?". Connector sees the request arriving from
  **`A.B.C.D:P1`** and **replies with exactly that**. NAT routes the reply back to the right
  private device. Now each device knows its own **public `IP:Port`**.
- Then via **Signalling (WebSocket Handler)**, U1 & U2 **swap public IPs** → they can send UDP to each other.

### 🤝 Capability handshake (before media flows)
Both peers exchange **bandwidth, codec, resolution**. They pick the **minimum common denominator** (weakest link wins) so a 2G user isn't flooded with HD packets they can't process.
- This can **renegotiate mid-call** (network chokes → drop HD→SD) — again via WebSocket Handler + Signalling.

---

## 4. WebRTC — Naming the Pieces

| Our component | WebRTC name | Job |
|---------------|-------------|-----|
| WebSocket Handler handshake | **Signalling** | Offer/accept call, exchange public IPs + codecs |
| `Connector` | **STUN Server** | Tell each client its **own public IP:port** |
| `Call Server` | **TURN Server** | **Relay** media when direct P2P fails |

> This is the **standard protocol** behind WhatsApp / FB Messenger / Zoom video calling.

### 🌐 P2P vs Call Server (TURN)
```
   ✅ Peer-to-Peer (preferred)              🔁 Call Server / TURN (fallback)
   U1  <== UDP media ==>  U2                U1 ==> [ Call Server ] ==> U2
   (low latency, cheap,                     U2 ==> [ Call Server ] ==> U1
    server never sees media)                (used when P2P impossible)
```
**Fall back to TURN when:**
1. 🔥 Firewall **blocks UDP** (must drop to TCP/WebSocket relay).
2. 🚧 **Symmetric NAT** — router only lets the *exact* endpoint it talked to (STUN) reply, so the peer's packets get blocked.
3. 🚫 No public IP obtainable.
4. 🔴 **Recording** is required (see §6).
5. 👥 **Large group** (see §5).

> 🔐 **P2P ≠ insecure.** During signalling the peers also exchange **crypto keys** → media is
> encrypted end-to-end; a middle-man sees gibberish. Works for groups too.

---

## 5. Group Calls — Mesh vs Call Server + Transcoding

### 📈 Why mesh doesn't scale
In a P2P mesh, each of **N** users sends their stream to the other **N-1** users:
> **Per-user bandwidth = (N-1)×.** Fine for 3–4, catastrophic for 100.

### 🧮 The rule
```
 Small group (≤ ~5, DYNAMIC)          Large group (> threshold)
        P2P mesh                        Route via Call Server
   U1 ─ U2                              U1 ┐
   │ ╳ │      each sends to all         U2 ┼──> [ Call Server ] ──> fans out to everyone
   U3 ─ U4                              U3 ┘    (each user: send once, receive once)
```
- The **"5" is dynamic**, decided at call creation from participant **count + network/geography**:
  - All on same office LAN → could support **~20** in mesh.
  - Users across countries → even **3–4** should go via Call Server.

### 🎛️ Transcoding (the group-call twist)
1:1 relay is trivial (just forward). Groups need **transcoding** because receivers have different bandwidth/codec.
```
 U1 (HD) ─talks─> Call Server ──(HD as-is)──> U2 (HD)
                       │
                       └──> Transcoding Svc ──(HD→SD)──> U3 (weak network)

 U3 (SD) ─talks─> Call Server ──(SD as-is)──> U1, U2   (can't upscale SD→HD)
```
- **Transcoding is one-way**: HD→SD ✅, SD→HD ❌.
- Transcoding Svc often runs **on the same machine** as Call Server (minimize latency), logically separate.
- Call Server keeps each user's capabilities in a **Redis cache**; adapts at runtime as bandwidth changes.

---

## 6. Recording Flow (Zoom-style)

Recording **forces the call through the Call Server** (can't record a pure P2P call server-side).
```
 U1 ─┐                          ┌─> U2
     ├─> [ Call Server ] ───────┤
 U2 ─┘        │                 └─> U1
              └──> Logger Service ──> Distributed FS (S3 / NFS / Hadoop)
                        (stores tiny chunks vs meetingId, during call)

 Call ends ──> Signalling Service ──> Kafka ("call finished" event)
                                           │
                                           ▼
                                     File Creator  (listens per meetingId)
                                           │ reads all chunks, stitches into one video
                                           ▼
                                     Amazon S3  ──> notify participants (link)
```

---

## 7. 🏗️ Overall Architecture

**Legend:** 🟩 Client UI (mostly mobile) · ⬛ LB + reverse proxy + auth · 🟦 Web/UDP services · 🟥 DB / datastore / cluster.

```
        🟩 U1 ........ (live WebSocket) ........ 🟩 U2
          │                                        │
          ▼                                        ▼
   ┌──────────────────┐   tracks user↔machine  ┌───────────────────┐
   │  WebSocket        │<---------------------->│  WebSocket Manager │
   │  Handler   🟦     │                        └───────────────────┘
   │ (uses Redis 🟥,   │
   │  persisted to disk)│
   └───────┬──────────┘
           │ initiate call
           ▼
   ┌──────────────────┐      policies/friends?    ┌────────────┐
   │ Signalling Svc 🟦 │<----------------------->│ User Svc 🟦 │──🟥 User DB
   └───────┬──────────┘                           └────────────┘
           │ emits events (bandwidth change, call end)
           │
     ┌─────┴───────────────────────────────────────────┐
     ▼                                                   ▼
 ┌───────────┐   "what's my public IP?"            ┌──────────────┐
 │ Connector │  (STUN) tells each client its       │  Kafka  🟥   │
 │  🟦 STUN  │   public IP:port                    └──────┬───────┘
 └───────────┘                                            │
           │                                              ▼
   try P2P (UDP) ── fails ──> ┌───────────────┐    ┌───────────────┐
                              │ Call Server 🟦 │    │ Analytics     │
                              │  (TURN)       │    │ Engine 🟦     │
                              │ +Transcoding  │    │ TSDB / Hadoop │
                              │  Svc (Redis🟥)│    │ + Spark       │
                              └──────┬────────┘    └───────────────┘
                                     │ if recording
                                     ▼
                              Logger Svc ─> Dist FS ─> File Creator ─> S3 🟥
```

### 🔩 Component responsibilities
| Component | Role |
|-----------|------|
| **WebSocket Handler** | Holds persistent live connections to all active users (Redis-backed, disk-persisted for fault tolerance). Carries all signalling/analytics messages. |
| **WebSocket Manager** | Orchestration: tracks *which* Handler machine (of 100s/1000s) is talking to *which* user. |
| **Signalling Service** | Initiates call, enforces policies (e.g. "must be friends" for FB; guests allowed for Zoom), emits Kafka events on state changes. |
| **User Service** | Repository of users powering signalling policies. |
| **Connector (STUN)** | Reflects each client's public IP:port. |
| **Call Server (TURN)** | Media relay for fallback / large groups / recording; hosts **Transcoding Service** + Redis capability cache. |
| **Logger Service** | Aggregates media chunks per meetingId into distributed FS during recorded calls. |
| **File Creator** | On "call end" Kafka event, stitches chunks into a final video → S3, notifies users. |
| **Analytics Service / Engine** | Consumes device analytics + Signalling events via Kafka → TSDB (simple) or Hadoop+Spark (advanced reports). |

### 📦 Media chunking
Unlike Netflix (multi-second chunks), a live call uses **tiny chunks (~a few ms up to ~1/4–1/5 sec)** so it feels real-time and seamless.

---

## 8. 🧠 Client-Side Intelligence

The client (app) is smart — it:
- Detects its **bandwidth / bitrate / codec** and negotiates the common config with the peer.
- **Dynamically switches P2P ↔ Call Server** mid-call without user action (e.g. call dropping, public IP changed) — each peer can migrate **independently at its own pace** (U1 may relay via Call Server while U2 still sends P2P until it also switches).
- Renegotiates **HD↔SD** mid-call via WebSocket Handler + Signalling when the network changes.
- Renders incoming stream + captures/sends outgoing stream.

---

## 9. 📡 Extending to LIVE STREAMING (millions of viewers)

Scenario: stream a **India vs Pakistan World Cup final** to millions. Reuse the **group-call + Call Server + transcoding** model, but fan out **CDN-style**.

```
  [ OUT OF SCOPE — the input side ]         [ OUR SYSTEM ]

  🎥 Cameras ┐
  🎙️ Audio  ┤─> Input Manager ──> Call Server ──> Transcoders (one per output format)
             ┘  (picks which cam/mic,           │        │        │
                sends 1 A/V stream)             ▼        ▼        ▼
                                            (Mobile fmt)(TV fmt)(Laptop fmt)
                                                │        │        │
                                                ▼        ▼        ▼
                                          Call Server per format  (edge, geo-distributed)
                                                │
                                    fan out to MANY edge Call Servers
                                    (close to users, like Netflix ISP appliances)
                                                │
                                     🟩 thousands of users of that ONE device type
```
**Key ideas:**
- **Transcoders run in real-time**: 1 input stream → N output formats (one set of transcoders per output format, e.g. mobile / TV / laptop = 3).
- Each **edge Call Server serves ONE stream/device-type** → keeps logic simple (no per-user stream selection).
- **Push edge Call Servers as close to users as possible.** The **user-side path replicates data to many people**, so minimize hops *there*; the input→edge path sends data **once**, so a longer path there is fine.
- A **WebSocket Manager**-style component tracks which edge Call Server serves which users → failover if one dies.

---

## 🗒️ One-Glance Revision Summary

| Question | Answer |
|----------|--------|
| **Transport for media?** | **UDP** (fast, lossy OK). Control/signalling = TCP/WebSocket. |
| **Why not TCP for video?** | Handshake + ACK/retry + congestion control + 20B header = lag. |
| **Call setup protocol?** | **WebRTC** = Signalling + STUN + TURN. |
| **Signalling?** | WebSocket Handler handshake (offer/accept, swap IPs & codecs). |
| **STUN = ?** | `Connector` — tells client its **public IP:port** (beats NAT). |
| **TURN = ?** | `Call Server` — **relay** when P2P fails. |
| **When does P2P fail?** | UDP-blocking firewall, **symmetric NAT**, no public IP, recording, large group. |
| **Small group?** | P2P mesh (≤ ~5, dynamic by count+network). |
| **Large group?** | Call Server relay + **transcoding** per receiver (HD→SD one-way). |
| **Mesh cost?** | **(N-1)×** bandwidth per user. |
| **Recording?** | Force via Call Server → Logger → Dist FS → File Creator (on Kafka call-end) → S3. |
| **Is P2P secure?** | Yes — crypto keys exchanged during signalling → E2E encrypted. |
| **CAP choice?** | Drop **Consistency** for **Latency + Availability**. |
| **Live streaming?** | Input Manager → transcoders → CDN-style edge Call Servers close to users. |
| **Analytics?** | Signalling/device events → Kafka → TSDB or Hadoop+Spark. |
