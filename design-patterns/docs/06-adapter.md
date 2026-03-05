# Adapter Pattern

## What is it? (One-liner)
Adapter converts the **interface of a class into another interface** that clients expect. It lets classes work together that couldn't otherwise because of incompatible interfaces.

## When to Use (Interview Answer)
> "I'd use Adapter when I need to integrate a **third-party library or legacy code** whose interface doesn't match what my system expects. Instead of modifying either side, I create an adapter that translates between them."

## Real-World Analogy
A power plug adapter: Your laptop has a US plug, but the wall socket is European. The adapter sits in between, converting one interface to another.

## How to Implement
```java
// Our system's interface (Target)
public interface PaymentProcessor {
    void processPayment(double amount, String currency);
}

// Third-party API (Adaptee) - incompatible interface
public class StripeApi {
    public String createCharge(int amountInCents, String cur, String desc) { ... }
}

// Adapter - bridges the gap
public class StripeAdapter implements PaymentProcessor {
    private final StripeApi stripeApi;

    public StripeAdapter(StripeApi stripeApi) {
        this.stripeApi = stripeApi;
    }

    @Override
    public void processPayment(double amount, String currency) {
        int cents = (int) (amount * 100);  // Convert interface
        stripeApi.createCharge(cents, currency, "Payment");
    }
}
```

## Two Types of Adapter
| Object Adapter (Composition) | Class Adapter (Inheritance) |
|------------------------------|---------------------------|
| Uses composition (has-a) | Uses inheritance (is-a) |
| More flexible | Limited (Java: single inheritance) |
| Preferred approach | Rarely used in Java |

## UML Structure
```
┌──────────────────┐     ┌──────────────┐
│  <<interface>>   │     │   StripeApi  │ (Adaptee)
│ PaymentProcessor │     │ +createCharge│
└────────┬─────────┘     └──────┬───────┘
         │implements             │ has-a
    ┌────┴──────────────────────┴─┐
    │      StripeAdapter          │ (Adapter)
    ├─────────────────────────────┤
    │ - stripeApi: StripeApi      │
    │ + processPayment()          │
    └─────────────────────────────┘
```

## Real-World Examples
- `java.util.Arrays.asList()` -- adapts array to List interface
- `java.io.InputStreamReader` -- adapts InputStream to Reader
- `Collections.enumeration()` -- adapts Collection to Enumeration
- Spring MVC `HandlerAdapter`
- JDBC drivers adapt DB-specific protocols to JDBC interface

## Interview Deep-Dive Questions

**Q: Adapter vs Facade?**
| Adapter | Facade |
|---------|--------|
| Makes incompatible interface compatible | Simplifies a complex subsystem |
| Wraps ONE class | Wraps MULTIPLE classes |
| Client knows it's adapting | Client may not know about subsystem |

**Q: Adapter vs Decorator?**
| Adapter | Decorator |
|---------|-----------|
| Changes the interface | Keeps the same interface |
| Purpose: compatibility | Purpose: add behavior |

## Key Points to Mention in Interview
1. Wraps an existing class with a new interface
2. Object adapter (composition) preferred over class adapter (inheritance)
3. Follows Single Responsibility Principle (conversion in one place)
4. Very common in integrating third-party libraries
5. Mention concrete Java examples (InputStreamReader, Arrays.asList)
