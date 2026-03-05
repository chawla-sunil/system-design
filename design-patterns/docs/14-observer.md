# Observer Pattern

## What is it? (One-liner)
Observer defines a **one-to-many dependency** between objects so that when one object changes state, all its dependents are **notified automatically**.

## When to Use (Interview Answer)
> "I'd use Observer when changes in one object should trigger updates in multiple other objects, and I don't want tight coupling between them. Classic examples: event handling systems, stock tickers, notification systems, and MVC architecture."

## How to Implement
```java
// Observer
public interface EventListener {
    void update(String eventType, String data);
}

// Subject (Observable)
public class StockExchange {
    private Map<String, List<EventListener>> listeners = new HashMap<>();

    public void subscribe(String event, EventListener listener) {
        listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(listener);
    }

    public void unsubscribe(String event, EventListener listener) {
        listeners.getOrDefault(event, List.of()).remove(listener);
    }

    private void notify(String event, String data) {
        for (EventListener l : listeners.getOrDefault(event, List.of())) {
            l.update(event, data);
        }
    }

    public void updatePrice(String symbol, double price) {
        notify("PRICE_CHANGE", symbol + ": $" + price);
    }
}
```

## Push vs Pull Model
| Push Model | Pull Model |
|-----------|------------|
| Subject sends data to observers | Subject notifies; observers pull data |
| `update(data)` | `update()` then `subject.getState()` |
| Simple but may send unnecessary data | Flexible but requires reference to subject |

## UML Structure
```
┌───────────────────┐       ┌──────────────────┐
│    StockExchange  │──────>│  <<interface>>   │
│    (Subject)      │ 1..*  │  EventListener   │
│ + subscribe()     │       │  + update()      │
│ + unsubscribe()   │       └────────┬─────────┘
│ + notify()        │           ┌────┴────┐
└───────────────────┘           ▼         ▼
                          PriceDisplay AlertSystem
```

## Real-World Examples
- **Java**: `java.util.Observer` / `Observable` (deprecated in Java 9)
- **Swing/AWT**: `ActionListener`, `MouseListener`
- **JavaBeans**: `PropertyChangeListener`
- **Spring**: `ApplicationEventPublisher` / `@EventListener`
- **Reactive Streams**: `Publisher` / `Subscriber` (java.util.concurrent.Flow)
- **JavaScript**: DOM event listeners
- **Architecture**: Event-driven microservices (Kafka, RabbitMQ)

## Interview Deep-Dive Questions

**Q: Observer vs Pub/Sub?**
| Observer | Pub/Sub |
|----------|---------|
| Observers know the subject | Publishers and subscribers are decoupled |
| Direct communication | Communication via message broker |
| Synchronous (usually) | Often asynchronous |
| In-process | Can be cross-process/network |

**Q: What about memory leaks?**
> "If observers aren't unsubscribed, the subject holds references preventing garbage collection. Solution: use `WeakReference` or ensure proper cleanup."

**Q: How to handle observer exceptions?**
> "One observer's exception shouldn't prevent others from being notified. Wrap each `update()` call in try-catch, or use asynchronous notification."

## Key Points to Mention in Interview
1. One-to-many: subject notifies all observers automatically
2. Loose coupling: subject doesn't know concrete observer types
3. Mention push vs pull model
4. Be aware of memory leaks (dangling observers)
5. Modern replacement: Reactive Streams (Flow API in Java 9+)
6. Spring `@EventListener` as practical example
