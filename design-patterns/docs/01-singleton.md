# Singleton Pattern

## What is it? (One-liner)
Singleton ensures a class has **only one instance** and provides a **global point of access** to it.

## When to Use (Interview Answer)
> "I'd use Singleton when exactly one instance of a class is needed to coordinate actions across the system — like a **database connection pool**, **configuration manager**, **logger**, or **thread pool**."

## Real-World Examples
- `java.lang.Runtime.getRuntime()`
- `java.awt.Desktop.getDesktop()`
- Spring Beans (default scope is singleton)
- Logger instances (SLF4J/Log4j)

## How to Implement

### Approach 1: Double-Checked Locking (Lazy, Thread-Safe)
```java
public class DatabaseConnection {
    private static volatile DatabaseConnection instance;

    private DatabaseConnection() { }  // private constructor

    public static DatabaseConnection getInstance() {
        if (instance == null) {                         // 1st check (no lock)
            synchronized (DatabaseConnection.class) {
                if (instance == null) {                 // 2nd check (with lock)
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }
}
```
**Why volatile?** Without `volatile`, Thread B might see a partially constructed object due to instruction reordering.

**Why double-check?** The outer check avoids the cost of synchronization on every call. The inner check ensures only one thread creates the instance.

### Approach 2: Enum Singleton (Recommended by Joshua Bloch)
```java
public enum AppConfig {
    INSTANCE;

    private String appName = "MyApp";

    public String getAppName() { return appName; }
}
// Usage: AppConfig.INSTANCE.getAppName();
```
**Why enum?** It's thread-safe, serialization-safe, and reflection-proof — all for free.

### Approach 3: Static Inner Class (Bill Pugh)
```java
public class Singleton {
    private Singleton() { }

    private static class Holder {
        private static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```
**Advantage:** Lazy loaded (inner class not loaded until `getInstance()` is called). No `synchronized` keyword needed.

## UML Structure
```
┌──────────────────────────┐
│       Singleton          │
├──────────────────────────┤
│ - instance: Singleton    │
├──────────────────────────┤
│ - Singleton()            │
│ + getInstance(): Singleton│
│ + businessMethod()       │
└──────────────────────────┘
```

## Interview Deep-Dive Questions

**Q: How do you break a Singleton?**
1. **Reflection** — Can call private constructor via `setAccessible(true)`
2. **Serialization** — Deserializing creates a new instance (fix: implement `readResolve()`)
3. **Cloning** — Override `clone()` to throw exception
4. **Multiple ClassLoaders** — Different classloaders create separate instances

**Q: Is Singleton thread-safe?**
- Eager initialization and enum: Yes, by default
- Lazy initialization: Only with proper synchronization (double-checked locking or `synchronized`)

**Q: Singleton vs Static Class?**
| Singleton | Static Class |
|-----------|-------------|
| Can implement interfaces | Cannot |
| Can be lazily loaded | Loaded at class loading |
| Can be passed as parameter | Cannot |
| Supports inheritance | Does not |

**Q: Is Singleton an anti-pattern?**
> "Singleton can become an anti-pattern when overused because it introduces **global state**, makes **unit testing harder** (tight coupling), and violates the **Single Responsibility Principle**. In modern apps, **dependency injection** (Spring's `@Singleton` scope) is preferred as it gives you singleton behavior without the downsides."

## Key Points to Mention in Interview
1. Private constructor prevents `new Singleton()`
2. `volatile` keyword is essential for double-checked locking
3. Enum singleton is the most robust approach
4. Mention trade-offs: testability, global state, DI alternatives
5. Know how to break it and prevent each breaking method
