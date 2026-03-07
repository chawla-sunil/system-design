# Design Patterns - Interview Preparation Guide

A comprehensive collection of **20 Gang of Four (GoF) design patterns** implemented in Java 17, with real-world examples and interview-focused documentation.

## Project Structure

```
design-patterns/
├── src/main/java/org/designpatterns/
│   ├── Main.java                          # Demo runner (run all or specific pattern)
│   ├── creational/
│   │   ├── singleton/                     # Database Connection + Enum Config
│   │   ├── factory/                       # Notification System
│   │   ├── abstractfactory/               # Cross-Platform UI (Windows/Mac)
│   │   ├── builder/                       # HTTP Request Builder
│   │   └── prototype/                     # Document Cloning with Registry
│   ├── structural/
│   │   ├── adapter/                       # Payment Gateway Adapter (Stripe/PayPal)
│   │   ├── bridge/                        # Remote Control + Device
│   │   ├── composite/                     # File System (Files & Directories)
│   │   ├── decorator/                     # Coffee Shop Add-ons
│   │   ├── facade/                        # Order Fulfillment System
│   │   ├── flyweight/                     # Text Editor Character Styles
│   │   └── proxy/                         # Lazy Loading + Access Control Image Proxy
│   └── behavioral/
│       ├── strategy/                      # Sorting Algorithms (Bubble/Quick)
│       ├── observer/                      # Stock Exchange Price Monitoring
│       ├── command/                       # Text Editor Undo/Redo
│       ├── chainofresponsibility/         # Support Ticket Escalation
│       ├── iterator/                      # Employee Collection with Filters
│       ├── mediator/                      # Chat Room
│       ├── state/                         # Vending Machine
│       └── templatemethod/                # Data Processing Pipeline (CSV/JSON)
└── docs/                                  # Interview guides for each pattern
    ├── 01-singleton.md
    ├── 02-factory-method.md
    ├── 03-abstract-factory.md
    ├── ...
    └── 20-template-method.md
    ├── 21-visitor.md
    ├── 22-memento.md
    └── 23-interpreter.md
```

## Quick Start

```bash
# Build
mvn clean compile -pl design-patterns

# Run all demos
mvn exec:java -pl design-patterns -Dexec.mainClass="org.designpatterns.Main"

# Run a specific pattern
mvn exec:java -pl design-patterns -Dexec.mainClass="org.designpatterns.Main" -Dexec.args="strategy"
```

## Pattern Catalog

### Creational Patterns (5) - "How objects are created"

| # | Pattern | Key Idea | Example | Guide |
|---|---------|----------|---------|-------|
| 1 | **Singleton** | One instance, global access | DB Connection Pool | [Guide](docs/01-singleton.md) |
| 2 | **Factory Method** | Delegate instantiation to subclasses | Notification System | [Guide](docs/02-factory-method.md) |
| 3 | **Abstract Factory** | Create families of related objects | Cross-platform UI | [Guide](docs/03-abstract-factory.md) |
| 4 | **Builder** | Step-by-step complex object construction | HTTP Request | [Guide](docs/04-builder.md) |
| 5 | **Prototype** | Clone existing objects | Document Templates | [Guide](docs/05-prototype.md) |

### Structural Patterns (7) - "How objects are composed"

| # | Pattern | Key Idea | Example | Guide |
|---|---------|----------|---------|-------|
| 6 | **Adapter** | Convert incompatible interfaces | Payment Gateway | [Guide](docs/06-adapter.md) |
| 7 | **Bridge** | Decouple abstraction from implementation | Remote + Device | [Guide](docs/07-bridge.md) |
| 8 | **Composite** | Tree structures, uniform treatment | File System | [Guide](docs/08-composite.md) |
| 9 | **Decorator** | Add behavior dynamically | Coffee Add-ons | [Guide](docs/09-decorator.md) |
| 10 | **Facade** | Simplify complex subsystem | Order Fulfillment | [Guide](docs/10-facade.md) |
| 11 | **Flyweight** | Share objects to save memory | Text Styles | [Guide](docs/11-flyweight.md) |
| 12 | **Proxy** | Control access to an object | Image Lazy Loading | [Guide](docs/12-proxy.md) |

### Behavioral Patterns (11) - "How objects communicate"

| # | Pattern | Key Idea | Example | Guide |
|---|---------|----------|---------|-------|
| 13 | **Strategy** | Swap algorithms at runtime | Sorting Algorithms | [Guide](docs/13-strategy.md) |
| 14 | **Observer** | One-to-many notification | Stock Price Monitor | [Guide](docs/14-observer.md) |
| 15 | **Command** | Encapsulate request as object | Undo/Redo Editor | [Guide](docs/15-command.md) |
| 16 | **Chain of Responsibility** | Pass request along handler chain | Support Escalation | [Guide](docs/16-chain-of-responsibility.md) |
| 17 | **Iterator** | Sequential access without exposing internals | Employee Filters | [Guide](docs/17-iterator.md) |
| 18 | **Mediator** | Centralize complex communication | Chat Room | [Guide](docs/18-mediator.md) |
| 19 | **State** | Behavior changes with state | Vending Machine | [Guide](docs/19-state.md) |
| 20 | **Template Method** | Skeleton algorithm, subclass steps | Data Pipeline | [Guide](docs/20-template-method.md) |
| 21 | **Visitor** | Add operations without modifying classes | Shopping Cart Price/Tax | [Guide](docs/21-visitor.md) |
| 22 | **Memento** | Capture & restore object state | Text Editor Snapshots | [Guide](docs/22-memento.md) |
| 23 | **Interpreter** | Define grammar & interpret expressions | Math Expression Evaluator | [Guide](docs/23-interpreter.md) |

## Interview Cheat Sheet

### How to Approach a Design Pattern Question

1. **Name it** - State the pattern name and category (creational/structural/behavioral)
2. **One-liner** - Give a concise definition
3. **Real-world analogy** - Make it relatable (e.g., "Adapter is like a power plug converter")
4. **When to use** - Describe the problem it solves
5. **UML/Structure** - Draw the key classes and relationships
6. **Code it** - Write a minimal implementation
7. **Trade-offs** - Mention pros, cons, and alternatives
8. **Java examples** - Reference JDK/framework usage (Spring, etc.)

### Most Frequently Asked in Interviews

| Rank | Pattern | Why It's Asked |
|------|---------|---------------|
| 1 | Singleton | Thread safety, enum approach, breaking methods |
| 2 | Factory | OCP, decoupling, very common in frameworks |
| 3 | Observer | Event systems, Spring events, reactive programming |
| 4 | Strategy | Comparator, algorithm swapping, lambdas |
| 5 | Builder | Immutability, fluent APIs, Lombok |
| 6 | Decorator | Java I/O streams, adding behavior dynamically |
| 7 | Proxy | Spring AOP, JPA lazy loading |
| 8 | State | State machines, workflow engines |

### Key Comparisons Interviewers Love

- **Factory vs Abstract Factory** - Single product vs family of products
- **Adapter vs Facade** - Interface conversion vs simplification
- **Adapter vs Decorator** - Changes interface vs adds behavior
- **Decorator vs Proxy** - Extends functionality vs controls access
- **Strategy vs State** - Client swaps algorithm vs internal state transitions
- **Strategy vs Template Method** - Composition vs inheritance
- **Observer vs Mediator** - Unidirectional vs bidirectional communication
- **Command vs Strategy** - Request/action with undo vs algorithm replacement
- **Memento vs Command** - Full state snapshot vs action + inverse action for undo
- **Visitor vs Strategy** - Operations on multiple types (double dispatch) vs swappable algorithm
- **Interpreter vs Composite** - Grammar tree evaluation vs uniform part-whole treatment
