# Factory Method Pattern

## What is it? (One-liner)
Factory Method defines an interface for creating objects but **lets subclasses decide** which class to instantiate. It delegates instantiation to subclasses.

## When to Use (Interview Answer)
> "I'd use Factory Method when a class can't anticipate the type of objects it needs to create, or when I want to **decouple client code from concrete classes**. The client works with the interface, and the factory handles which implementation to create."

## Real-World Examples
- `java.util.Calendar.getInstance()`
- `java.text.NumberFormat.getInstance()`
- `java.nio.charset.Charset.forName()`
- Spring's `BeanFactory`
- JDBC `DriverManager.getConnection()`

## How to Implement
```java
// Product interface
public interface Notification {
    void notifyUser(String message);
}

// Concrete Products
public class EmailNotification implements Notification { ... }
public class SMSNotification implements Notification { ... }
public class PushNotification implements Notification { ... }

// Factory
public class NotificationFactory {
    public static Notification createNotification(String channel) {
        return switch (channel.toUpperCase()) {
            case "EMAIL" -> new EmailNotification();
            case "SMS"   -> new SMSNotification();
            case "PUSH"  -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown: " + channel);
        };
    }
}

// Client - decoupled from concrete classes
Notification n = NotificationFactory.createNotification("EMAIL");
n.notifyUser("Hello!");
```

## UML Structure
```
         ┌─────────────────┐
         │  <<interface>>  │
         │   Notification  │
         └────────┬────────┘
        ┌─────────┼──────────┐
        ▼         ▼          ▼
┌───────────┐┌──────────┐┌────────────┐
│   Email   ││   SMS    ││   Push     │
│Notification││Notification││Notification│
└───────────┘└──────────┘└────────────┘
                  ▲
         ┌────────┴────────┐
         │ NotificationFactory │
         │ +create(type): Notification │
         └─────────────────┘
```

## Interview Deep-Dive Questions

**Q: Factory Method vs Simple Factory?**
| Simple Factory | Factory Method |
|---------------|----------------|
| One factory class with if/switch | Each product has its own factory subclass |
| Not a GoF pattern (it's an idiom) | GoF design pattern |
| Violates Open/Closed Principle | Follows Open/Closed Principle |
| Good enough for most cases | Use when you need extensibility |

**Q: When would you NOT use Factory?**
> "If there's only one type of product or creation logic is trivial, a factory adds unnecessary complexity. YAGNI applies."

**Q: How does Factory Method follow SOLID?**
- **S** — Factory has single responsibility: object creation
- **O** — New products added without modifying existing code
- **D** — Client depends on abstraction (interface), not concrete classes

## Key Points to Mention in Interview
1. Encapsulates object creation logic
2. Client is decoupled from concrete classes
3. New types can be added without changing client code (OCP)
4. Often combined with Strategy or Template Method patterns
5. Modern Java: use `switch` expressions or `Map<String, Supplier<T>>` for cleaner factories
