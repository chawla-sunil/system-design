# Iterator Pattern

## What is it? (One-liner)
Iterator provides a way to access elements of a collection **sequentially without exposing** its underlying representation.

## When to Use (Interview Answer)
> "I'd use Iterator when I want to traverse a collection without exposing its internal structure, or when I need multiple traversal strategies (e.g., filter by department, by salary range) over the same collection."

## How to Implement
```java
public interface EmployeeIterator {
    boolean hasNext();
    Employee next();
}

public class EmployeeCollection {
    private List<Employee> employees = new ArrayList<>();

    public EmployeeIterator iterator() {
        return new AllEmployeeIterator();
    }

    public EmployeeIterator departmentIterator(String dept) {
        return new DepartmentIterator(dept);
    }

    // Different traversal strategies as inner classes
    private class DepartmentIterator implements EmployeeIterator {
        // Filters by department while iterating
    }
}
```

## UML Structure
```
┌──────────────────┐       ┌──────────────────┐
│  <<interface>>   │       │  <<interface>>   │
│  Iterable        │──────>│  Iterator        │
│  + iterator()    │       │  + hasNext()     │
└────────┬─────────┘       │  + next()        │
         │                 └────────┬─────────┘
         ▼                          ▼
  EmployeeCollection      AllEmployeeIterator
                          DepartmentIterator
```

## Internal vs External Iterator
| External (GoF) | Internal (Java 8+) |
|----------------|-------------------|
| Client controls iteration | Collection controls iteration |
| `while(iter.hasNext())` | `collection.forEach()` |
| More flexible | More concise |
| `Iterator` interface | `Iterable.forEach()`, Streams |

## Real-World Examples
- `java.util.Iterator` / `java.util.Iterable`
- Enhanced for-loop (`for (Item i : collection)`)
- `java.util.Scanner`
- `java.util.stream.Stream`
- `ResultSet` in JDBC
- `Enumeration` (legacy)
- `Spliterator` (parallel iteration)

## Interview Deep-Dive Questions

**Q: Iterator vs for-each loop?**
> "for-each uses `Iterator` under the hood. Any class implementing `Iterable` can be used with for-each. But explicit Iterator gives you `remove()` capability."

**Q: Fail-fast vs Fail-safe iterators?**
| Fail-fast | Fail-safe |
|-----------|-----------|
| `ArrayList.iterator()` | `ConcurrentHashMap.iterator()` |
| Throws `ConcurrentModificationException` | Works on a copy |
| Detects structural modification | No exception |

**Q: Why is Iterator a pattern and not just an interface?**
> "The pattern is about the concept of separating traversal from the collection. Different iterators over the same collection without changing the collection class. The interface is just the Java implementation."

## Key Points to Mention in Interview
1. Separates traversal logic from collection
2. Multiple iterators over same collection
3. `java.util.Iterator` is Java's built-in implementation
4. Know fail-fast vs fail-safe
5. Java 8+ Streams are the modern evolution
6. For-each loop = syntactic sugar for Iterator
