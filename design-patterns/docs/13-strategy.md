# Strategy Pattern

## What is it? (One-liner)
Strategy defines a **family of algorithms**, encapsulates each one, and makes them **interchangeable** at runtime.

## When to Use (Interview Answer)
> "I'd use Strategy when I have multiple algorithms for a task and want to **switch between them at runtime** without changing the client code. For example, different sorting algorithms, payment methods, compression strategies, or validation rules."

## How to Implement
```java
// Strategy interface
public interface SortStrategy<T extends Comparable<T>> {
    List<T> sort(List<T> data);
}

// Concrete strategies
public class BubbleSortStrategy<T extends Comparable<T>> implements SortStrategy<T> {
    public List<T> sort(List<T> data) { /* bubble sort */ }
}
public class QuickSortStrategy<T extends Comparable<T>> implements SortStrategy<T> {
    public List<T> sort(List<T> data) { /* quick sort */ }
}

// Context
public class DataProcessor<T extends Comparable<T>> {
    private SortStrategy<T> strategy;

    public void setStrategy(SortStrategy<T> strategy) {
        this.strategy = strategy;  // Switch at runtime!
    }

    public List<T> process(List<T> data) {
        return strategy.sort(data);
    }
}

// Usage - switch strategy at runtime
DataProcessor<Integer> processor = new DataProcessor<>(new BubbleSortStrategy<>());
processor.process(data);
processor.setStrategy(new QuickSortStrategy<>());  // Switch!
processor.process(data);
```

## UML Structure
```
┌──────────────────┐      ┌──────────────────┐
│  DataProcessor   │─────>│  <<interface>>   │
│  (Context)       │      │  SortStrategy    │
│  - strategy      │      │  + sort(data)    │
│  + setStrategy() │      └────────┬─────────┘
│  + process()     │          ┌────┴────┐
└──────────────────┘          ▼         ▼
                        BubbleSort  QuickSort
```

## Modern Java: Strategy with Lambdas
```java
// Since Strategy is often a single-method interface (functional interface):
DataProcessor processor = new DataProcessor(data -> Collections.sort(data));

// Or use method references:
processor.setStrategy(Collections::sort);
```

## Real-World Examples
- `java.util.Comparator` — THE classic Strategy in Java
- `java.util.Collections.sort(list, comparator)`
- `javax.servlet.Filter` chain strategies
- Spring's `Resource` resolution strategies
- Payment processing (CreditCard, PayPal, Crypto strategies)

## Interview Deep-Dive Questions

**Q: Strategy vs State?**
| Strategy | State |
|----------|-------|
| Client chooses the strategy | State transitions internally |
| Algorithms are independent | States know about each other |
| Swapped explicitly by client | Changed automatically by context |

**Q: Strategy vs Template Method?**
| Strategy | Template Method |
|----------|-----------------|
| Composition (has-a) | Inheritance (is-a) |
| Entire algorithm replaced | Only specific steps vary |
| Runtime flexibility | Compile-time structure |

**Q: How does Strategy follow SOLID?**
- **O** (Open/Closed): New strategies without modifying context
- **D** (Dependency Inversion): Context depends on abstraction
- **L** (Liskov): All strategies are substitutable

## Key Points to Mention in Interview
1. Encapsulates algorithms as objects
2. Swap algorithms at runtime via composition
3. Eliminates conditional statements (if/else, switch for algorithm selection)
4. `Comparator` is the easiest Java example
5. With Java 8+ lambdas, Strategy = functional interface
6. Follows Open/Closed Principle
