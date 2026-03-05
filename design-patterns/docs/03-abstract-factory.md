# Abstract Factory Pattern

## What is it? (One-liner)
Abstract Factory provides an interface for creating **families of related objects** without specifying their concrete classes.

## When to Use (Interview Answer)
> "I'd use Abstract Factory when the system needs to create **families of related objects** that must be used together — like UI components for different OS themes (Windows vs Mac), or database access layers for different databases (MySQL vs PostgreSQL)."

## Factory Method vs Abstract Factory
| Factory Method | Abstract Factory |
|---------------|-----------------|
| Creates **one product** | Creates **families of products** |
| Uses inheritance | Uses composition |
| One factory method | Multiple factory methods |
| Single product hierarchy | Multiple product hierarchies |

## How to Implement
```java
// Abstract Products
public interface Button { void render(); }
public interface Checkbox { void render(); }

// Abstract Factory
public interface UIFactory {
    Button createButton();
    Checkbox createCheckbox();
}

// Concrete Factory: Windows Family
public class WindowsUIFactory implements UIFactory {
    public Button createButton()     { return new WindowsButton(); }
    public Checkbox createCheckbox() { return new WindowsCheckbox(); }
}

// Concrete Factory: Mac Family
public class MacUIFactory implements UIFactory {
    public Button createButton()     { return new MacButton(); }
    public Checkbox createCheckbox() { return new MacCheckbox(); }
}

// Client code - works with ANY factory
void renderUI(UIFactory factory) {
    Button btn = factory.createButton();     // Don't know if Windows or Mac
    Checkbox cb = factory.createCheckbox();   // But they're guaranteed compatible
    btn.render();
    cb.render();
}
```

## UML Structure
```
┌──────────────┐       ┌──────────────┐
│ <<interface>> │       │ <<interface>> │
│    Button     │       │   Checkbox   │
└──────┬───────┘       └──────┬───────┘
  ┌────┴────┐            ┌────┴────┐
  ▼         ▼            ▼         ▼
WinButton MacButton  WinCheckbox MacCheckbox
  ▲         ▲            ▲         ▲
  │         │            │         │
┌─┴─────────┴──┐   ┌────┴─────────┴──┐
│ WindowsUIFactory │   │  MacUIFactory     │
└──────┬───────┘   └──────┬───────────┘
       │                   │
       └─────┬─────────────┘
        ┌────┴────────┐
        │ <<interface>>│
        │  UIFactory   │
        └─────────────┘
```

## Real-World Examples
- Java AWT: `Toolkit` creates platform-specific UI components
- JDBC: `Connection` creates `Statement`, `PreparedStatement` (family)
- Spring: `AbstractFactoryBean`
- Document editors: create families of elements for different formats

## Interview Deep-Dive Questions

**Q: What's the main drawback?**
> "Adding a new product type (e.g., `TextField`) requires changing the abstract factory interface AND all concrete factories. This violates OCP for product types, though it follows OCP for product families."

**Q: How to choose the right factory at runtime?**
```java
UIFactory factory = switch (os) {
    case "Windows" -> new WindowsUIFactory();
    case "Mac"     -> new MacUIFactory();
    default        -> throw new UnsupportedOperationException();
};
```

## Key Points to Mention in Interview
1. Creates families of **related** objects that must be compatible
2. Concrete classes are isolated from client code
3. Swapping entire product families is easy (just change the factory)
4. Adding new product to the family is expensive (all factories must change)
5. Often the factory itself is a Singleton
