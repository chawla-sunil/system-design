# Decorator Pattern

## What is it? (One-liner)
Decorator attaches **additional responsibilities to an object dynamically**. It provides a flexible alternative to subclassing for extending functionality.

## When to Use (Interview Answer)
> "I'd use Decorator when I need to add behavior to individual objects at runtime without affecting other objects of the same class. Classic example: Java I/O streams where you wrap a FileInputStream with BufferedInputStream with DataInputStream."

## The Problem It Solves
Without Decorator (subclass explosion):
```
Coffee, CoffeeWithMilk, CoffeeWithSugar, CoffeeWithMilkAndSugar,
CoffeeWithMilkAndSugarAndWhip... (combinatorial explosion!)
```

With Decorator (composition):
```java
Coffee c = new WhippedCream(new Sugar(new Milk(new SimpleCoffee())));
// Stack any combination dynamically!
```

## How to Implement
```java
public interface Coffee {
    String getDescription();
    double getCost();
}

public class SimpleCoffee implements Coffee {
    public double getCost() { return 2.00; }
}

public abstract class CoffeeDecorator implements Coffee {
    protected final Coffee wrapped;
    public CoffeeDecorator(Coffee coffee) { this.wrapped = coffee; }
}

public class MilkDecorator extends CoffeeDecorator {
    public MilkDecorator(Coffee c) { super(c); }
    public String getDescription() { return wrapped.getDescription() + " + Milk"; }
    public double getCost() { return wrapped.getCost() + 0.50; }
}
```

## UML Structure
```
     ┌──────────────────┐
     │  <<interface>>   │
     │     Coffee       │
     └────────┬─────────┘
      ┌───────┴────────┐
      ▼                ▼
┌──────────┐   ┌──────────────────┐
│ Simple   │   │ CoffeeDecorator  │──┐ wraps
│ Coffee   │   │ - wrapped: Coffee│  │ Coffee
└──────────┘   └────────┬─────────┘  │
                   ┌────┴────┐       │
                   ▼         ▼       │
              MilkDecorator  SugarDecorator
```

## Real-World Examples
- **Java I/O**: `new BufferedReader(new InputStreamReader(new FileInputStream("f")))`
- `Collections.unmodifiableList()` — decorator that throws on modification
- `Collections.synchronizedList()` — adds thread safety
- Spring: `BeanPostProcessor` decorates beans
- Servlet filters decorate request/response

## Interview Deep-Dive Questions

**Q: Decorator vs Inheritance?**
| Decorator | Inheritance |
|-----------|-------------|
| Adds behavior at runtime | Adds behavior at compile time |
| Can stack multiple decorators | Single extension point |
| Follows OCP | New feature = new subclass |
| Can remove decorators | Permanent |

**Q: Decorator vs Proxy?**
| Decorator | Proxy |
|-----------|-------|
| Adds functionality | Controls access |
| Client creates the chain | Proxy manages lifecycle |
| Used to extend behavior | Used for lazy init, access control |

**Q: Downside of Decorator?**
> "Many small classes and wrapping layers. Debugging can be tricky. The object identity changes with each wrapper (`decorated != original`)."

## Key Points to Mention in Interview
1. Wraps an object to add behavior dynamically
2. Both decorator and component implement the same interface
3. Decorators can be stacked (layered)
4. Follows Open/Closed Principle
5. Java I/O is THE classic example to mention
6. Flexible alternative to subclassing
