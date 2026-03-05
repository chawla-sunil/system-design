# Chain of Responsibility Pattern

## What is it? (One-liner)
Chain of Responsibility passes a request along a **chain of handlers**. Each handler decides either to process the request or pass it to the next handler.

## When to Use (Interview Answer)
> "I'd use Chain of Responsibility when a request can be handled by multiple handlers, and which handler should process it isn't known in advance. Examples: support ticket escalation, middleware pipelines, logging frameworks, and servlet filters."

## How to Implement
```java
public abstract class SupportHandler {
    private SupportHandler next;

    public SupportHandler setNext(SupportHandler next) {
        this.next = next;
        return next;
    }

    public void handle(SupportTicket ticket) {
        if (canHandle(ticket)) {
            processTicket(ticket);
        } else if (next != null) {
            next.handle(ticket);  // Pass along the chain
        }
    }

    protected abstract boolean canHandle(SupportTicket ticket);
    protected abstract void processTicket(SupportTicket ticket);
}

// Build chain: Basic -> Technical -> Manager
basic.setNext(tech).setNext(manager);
basic.handle(ticket);  // Enters at start of chain
```

## UML Structure
```
┌──────────────────────┐
│   SupportHandler     │
│ - next: SupportHandler│──┐
│ + setNext()          │  │
│ + handle()           │  │ next
│ # canHandle()        │  │
│ # processTicket()    │◄─┘
└──────────┬───────────┘
    ┌──────┼──────┐
    ▼      ▼      ▼
  Basic  Tech  Manager
```

## Real-World Examples
- **Servlet Filters**: `doFilter(request, response, chain)`
- **Spring Security**: Filter chain
- **Java logging**: `Logger` hierarchy (parent loggers)
- **Exception handling**: try-catch chain
- **Middleware**: Express.js, Spring interceptors
- **DOM Event Bubbling**: Events travel up the DOM tree
- **Approval workflows**: Purchase approval levels

## Interview Deep-Dive Questions

**Q: What if no handler processes the request?**
> "You can add a default handler at the end, throw an exception, or return a default response. The pattern should handle this gracefully."

**Q: Chain of Responsibility vs Decorator?**
| Chain of Responsibility | Decorator |
|------------------------|-----------|
| One handler processes | All decorators contribute |
| Request may not be handled | All layers always execute |
| Handlers are independent | Decorators wrap each other |

**Q: How does Spring Security use this?**
> "Spring Security's `FilterChain` is a Chain of Responsibility. Request passes through authentication filter, authorization filter, CSRF filter, etc. Each filter can either continue the chain or reject the request."

## Key Points to Mention in Interview
1. Request travels along chain until handled
2. Decouples sender from receivers
3. Chain order matters
4. Servlet Filter chain is the classic Java example
5. Each handler has single responsibility
6. Can be combined with Composite for tree-structured chains
