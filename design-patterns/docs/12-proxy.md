# Proxy Pattern

## What is it? (One-liner)
Proxy provides a **surrogate or placeholder** for another object to control access to it.

## When to Use (Interview Answer)
> "I'd use Proxy to **control access** to an object — either for lazy loading (virtual proxy), access control (protection proxy), logging (logging proxy), or caching. The proxy has the same interface as the real object, so the client doesn't know it's talking to a proxy."

## Types of Proxy
| Type | Purpose | Example |
|------|---------|---------|
| Virtual Proxy | Lazy initialization | Load image only when displayed |
| Protection Proxy | Access control | Check permissions before allowing access |
| Remote Proxy | Network access | RMI stub |
| Caching Proxy | Cache results | Cache API responses |
| Logging Proxy | Audit/logging | Log all method calls |

## How to Implement
```java
public interface Image {
    void display();
}

// Real object (expensive to create)
public class RealImage implements Image {
    public RealImage(String file) {
        loadFromDisk(file);  // Slow!
    }
    public void display() { ... }
}

// Virtual Proxy (lazy loading)
public class LazyImageProxy implements Image {
    private RealImage realImage;  // Created on demand
    private final String fileName;

    public void display() {
        if (realImage == null) {
            realImage = new RealImage(fileName);  // Load only when needed
        }
        realImage.display();
    }
}
```

## UML Structure
```
     ┌──────────────────┐
     │  <<interface>>   │
     │     Image        │
     └────────┬─────────┘
        ┌─────┴──────┐
        ▼            ▼
┌───────────┐  ┌──────────────┐
│ RealImage │  │ ImageProxy   │
│           │  │ -real: Image │
│ +display()│  │ +display()   │──> delegates to RealImage
└───────────┘  └──────────────┘
```

## Real-World Examples
- **Spring AOP Proxies** — `@Transactional`, `@Cacheable` use dynamic proxies
- **JPA Lazy Loading** — `@ManyToOne(fetch=LAZY)` returns proxy
- `java.lang.reflect.Proxy` — dynamic proxy API
- **RMI** — Remote Method Invocation stubs
- **Hibernate** — lazy-loaded entity proxies
- **CDI** — Contexts and Dependency Injection proxies

## Interview Deep-Dive Questions

**Q: Proxy vs Decorator?**
| Proxy | Decorator |
|-------|-----------|
| Controls access | Adds behavior |
| May create real object | Wraps existing object |
| Same interface, different purpose | Same interface, extends functionality |
| Client unaware of proxy | Client may build decorator chain |

**Q: Static Proxy vs Dynamic Proxy?**
> "Static proxy: you write the proxy class manually. Dynamic proxy: generated at runtime using `java.lang.reflect.Proxy` (JDK) or CGLIB (bytecode). Spring uses dynamic proxies for AOP."

**Q: What's JPA lazy loading doing under the hood?**
> "Hibernate returns a proxy object. When you access a lazy field, the proxy intercepts the getter call and executes the SQL query at that point. That's a Virtual Proxy."

## Key Points to Mention in Interview
1. Same interface as real object — transparent to client
2. Controls access without changing the real object
3. Types: virtual, protection, remote, caching, logging
4. Spring AOP and JPA lazy loading are the best Java examples
5. Know the difference between static and dynamic proxies
