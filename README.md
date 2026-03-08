# 🏗️ System Design — Interview Preparation Repository

A comprehensive, **interview-focused** collection of Low-Level Design (LLD) problems and Design Patterns, implemented in **Java 17** with Maven.

> **Goal:** Walk into any LLD / Machine Coding interview with a mental framework — know what questions to ask, how to think about the problem, which patterns to apply, and how to present your solution.

---

## 📦 Modules

| Module | Description | Key Patterns | Doc |
|--------|-------------|-------------|-----|
| [**design-patterns**](design-patterns/) | 23 GoF design patterns with real-world examples | Singleton, Factory, Strategy, Observer, Builder, etc. | [README](design-patterns/README.md) |
| [**parking-lot**](parking-lot/) | Parking Lot LLD — multi-floor, multi-gate, billing | Singleton, Strategy, Observer, Factory | [LLD Guide](parking-lot/parking-lot-lld.md) |
| [**tic-tac-toe**](tic-tac-toe/) | Tic-Tac-Toe LLD — N×N board, O(1) win detection, undo | Strategy, Factory, Immutable Records | [LLD Guide](tic-tac-toe/tic-tac-toe-lld.md) |
| [**engineering-fundamentals**](engineering-fundamentals/) | Maven, Git, Docker, CI/CD — tools & concepts cheat sheets | — | [README](engineering-fundamentals/README.md) |

---

## 🚀 Quick Start

```bash
# Build everything
mvn clean compile

# Run Parking Lot demo
mvn -pl parking-lot exec:java -Dexec.mainClass="org.systemdesign.parkinglot.Main"

# Run Tic-Tac-Toe demo
mvn -pl tic-tac-toe exec:java -Dexec.mainClass="org.tictactoe.Main"

# Run Design Patterns demo
mvn -pl design-patterns exec:java -Dexec.mainClass="org.designpatterns.Main"
```

**Prerequisites:** Java 17+, Maven 3.8+

---

## 🎯 How to Use This Repo for Interview Prep

### 1. Study the Approach, Not Just the Code

Each LLD module has a **detailed markdown guide** (e.g., `parking-lot-lld.md`, `tic-tac-toe-lld.md`) that walks through:

- **Clarifying questions** to ask the interviewer
- **Mental model** — how to derive classes from nouns/verbs
- **Design patterns** used and WHY
- **Key algorithms** with complexity analysis
- **Edge cases** they will ask about
- **How to present** the solution (timed breakdown)
- **Follow-up questions** and answers

### 2. Practice the Pattern

For every LLD problem, follow this framework:

```
1. Clarify requirements          (2 min)  — Ask 5-8 questions
2. Identify entities             (2 min)  — Nouns → Classes
3. Identify behaviors            (1 min)  — Verbs → Methods/Services
4. Choose design patterns        (2 min)  — Strategy, Observer, Factory, etc.
5. Code the solution             (15 min) — Start with models, then services
6. Walk through edge cases       (3 min)  — Invalid input, concurrency, overflow
```

### 3. Know Your Design Patterns

The [design-patterns](design-patterns/) module covers all 23 GoF patterns with interview-ready examples. Start with the most frequently asked:

| Frequency | Patterns |
|-----------|----------|
| 🔥 Very Common | Strategy, Observer, Factory, Singleton, Builder |
| ⚡ Common | Decorator, Adapter, Command, State, Template Method |
| 📘 Good to Know | Composite, Proxy, Flyweight, Chain of Responsibility, Mediator |

---

## 🏛️ Project Structure

```
system-design/
├── pom.xml                          ← Parent POM (Java 17, multi-module)
├── README.md                        ← You are here
│
├── design-patterns/                 ← 23 GoF patterns with examples
│   ├── docs/                        ← Interview guide per pattern
│   └── src/main/java/org/designpatterns/
│       ├── creational/              ← Singleton, Factory, Builder, Prototype, Abstract Factory
│       ├── structural/              ← Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy
│       └── behavioral/             ← Strategy, Observer, Command, Chain of Responsibility, etc.
│
├── parking-lot/                     ← Parking Lot LLD
│   ├── parking-lot-lld.md           ← Complete interview guide
│   └── src/main/java/org/systemdesign/parkinglot/
│       ├── model/                   ← ParkingLot, Floor, Spot, Vehicle, Ticket, Payment
│       ├── strategy/                ← NearestSpot, RandomSpot
│       ├── billing/                 ← Hourly, FlatRate
│       ├── observer/                ← DisplayBoard
│       ├── factory/                 ← VehicleFactory
│       ├── service/                 ← ParkingService, TicketService, PaymentService
│       └── exception/               ← ParkingLotFull, InvalidTicket, PaymentFailed
│
└── tic-tac-toe/                     ← Tic-Tac-Toe LLD
    ├── tic-tac-toe-lld.md           ← Complete interview guide
    └── src/main/java/org/tictactoe/
        ├── model/                   ← Board, Cell, Player, Move, PieceType, GameStatus
        ├── strategy/                ← RowWin, ColumnWin, DiagonalWin, AntiDiagonalWin
        ├── factory/                 ← GameFactory
        ├── service/                 ← GameService (game loop, undo)
        ├── validator/               ← MoveValidator
        └── exception/               ← InvalidMove, GameOver

├── engineering-fundamentals/        ← Tools & concepts cheat sheets
│   ├── README.md
│   └── docs/
│       ├── maven-interview-cheatsheet.md  ← 5 min quick reference
│       └── maven-deep-dive.md             ← 30 min senior-level guide
```

---

## 📋 LLD Problems Covered

### ✅ Parking Lot
- Multi-floor, multi-gate parking system
- Hourly & flat-rate billing (Strategy pattern)
- O(1) spot assignment with per-spot locking (concurrency)
- DisplayBoard auto-update (Observer pattern)
- [→ Full Interview Guide](parking-lot/parking-lot-lld.md)

### ✅ Tic-Tac-Toe
- N×N board with O(1) win detection using counting strategies
- Undo support via reversible counters (Event Sourcing style)
- Clean separation: Board (state) → Validator (rules) → Strategy (win) → Service (flow)
- [→ Full Interview Guide](tic-tac-toe/tic-tac-toe-lld.md)

### 🔜 Coming Soon
- Elevator System
- BookMyShow / Movie Ticket Booking
- Snake & Ladder
- Chess
- Library Management System
- Splitwise

---

## 🧠 Interview Tips

1. **Always start with clarifying questions.** It shows you think before coding.
2. **Name your patterns.** Saying "I'll use Strategy here because..." impresses interviewers.
3. **Think in nouns and verbs.** Nouns → Classes. Verbs → Methods. It's that simple.
4. **Mention trade-offs.** "Singleton is convenient but hard to test — in production I'd prefer DI."
5. **Talk about concurrency** even if not asked. It shows depth.
6. **Cover edge cases proactively.** Don't wait for the interviewer to catch you.
7. **Keep services thin.** Each class should have ONE reason to change (SRP).
8. **Use enums for fixed sets.** `VehicleType`, `PieceType`, `GameStatus` — not strings.

---

## 📄 License

This project is for educational/interview preparation purposes.
