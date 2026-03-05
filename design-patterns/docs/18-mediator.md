# Mediator Pattern

## What is it? (One-liner)
Mediator defines an object that **encapsulates how a set of objects interact**, promoting loose coupling by keeping objects from referring to each other directly.

## When to Use (Interview Answer)
> "I'd use Mediator when multiple objects communicate in complex ways, creating a tangled web of dependencies. The mediator acts as a central hub — like a chat room where users send messages to the room, not to each other directly."

## Without vs With Mediator
```
Without: Every user talks to every other user → N*(N-1)/2 connections
With:    Every user talks to mediator only   → N connections
```

## How to Implement
```java
public interface ChatMediator {
    void sendMessage(String message, User sender);
    void addUser(User user);
}

public class ChatRoom implements ChatMediator {
    private List<User> users = new ArrayList<>();

    public void sendMessage(String message, User sender) {
        for (User user : users) {
            if (user != sender) {
                user.receive(message, sender.getName());
            }
        }
    }
}

// Users only know the mediator, not each other
public class ChatUser extends User {
    public void send(String message) {
        mediator.sendMessage(message, this);
    }
}
```

## UML Structure
```
┌──────────────────┐    ┌──────────────────┐
│  <<interface>>   │    │     User         │
│  ChatMediator    │◄───│  - mediator      │
│  + sendMessage() │    │  + send()        │
│  + addUser()     │    │  + receive()     │
└────────┬─────────┘    └────────┬─────────┘
         │                       │
    ┌────┴────┐             ┌────┴────┐
    ▼         ▼             ▼         ▼
 ChatRoom  (other)      ChatUser  (other)
```

## Real-World Examples
- **Java**: `java.util.Timer` mediates between `TimerTask` objects
- **Swing**: `JMediator` pattern in complex UIs
- **Air Traffic Control**: Planes don't talk to each other; they talk to ATC
- **Spring MVC**: `DispatcherServlet` mediates between controllers/views
- **Message Brokers**: Kafka, RabbitMQ act as mediators
- **Chat applications**: Chat room mediates between users

## Interview Deep-Dive Questions

**Q: Mediator vs Observer?**
| Mediator | Observer |
|----------|----------|
| Bidirectional communication | Unidirectional (subject -> observer) |
| Mediator knows all colleagues | Subject doesn't know concrete observers |
| Centralizes complex communication | Distributes notification |

**Q: Downside of Mediator?**
> "The mediator can become a 'God Object' that's too complex. It centralizes logic, which can be a single point of failure. Keep the mediator focused."

## Key Points to Mention in Interview
1. Reduces many-to-many into one-to-many relationships
2. Colleagues only know the mediator, not each other
3. Centralizes communication logic
4. Risk: mediator becoming a God Object
5. Air traffic control is the best real-world analogy
