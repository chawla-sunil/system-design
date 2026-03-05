# Template Method Pattern

## What is it? (One-liner)
Template Method defines the **skeleton of an algorithm** in a base class, letting subclasses **override specific steps** without changing the algorithm's structure.

## When to Use (Interview Answer)
> "I'd use Template Method when I have an algorithm with a fixed overall structure, but individual steps vary. For example, data processing pipelines: read -> parse -> validate -> save. The pipeline structure is the same, but reading CSV differs from reading JSON."

## How to Implement
```java
public abstract class DataProcessor {
    // Template method - final to prevent override
    public final void process() {
        List<String> raw = readData();       // varies
        List<String> parsed = parseData(raw); // varies
        List<String> valid = validate(parsed); // hook (optional override)
        saveData(valid);                      // common
    }

    protected abstract List<String> readData();     // Must implement
    protected abstract List<String> parseData(List<String> data); // Must implement

    // Hook method - default implementation, can be overridden
    protected List<String> validate(List<String> data) {
        return data;  // Default: no extra validation
    }

    // Common step - same for all
    protected void saveData(List<String> data) {
        System.out.println("Saving " + data.size() + " records");
    }
}

public class CsvProcessor extends DataProcessor {
    protected List<String> readData() { /* read CSV */ }
    protected List<String> parseData(List<String> data) { /* parse CSV */ }
}
```

## UML Structure
```
┌──────────────────────────┐
│   DataProcessor          │
├──────────────────────────┤
│ + process()  [final]     │  ◄── Template Method
│ # readData() [abstract]  │  ◄── Primitive operations
│ # parseData() [abstract] │
│ # validate()             │  ◄── Hook (optional override)
│ # saveData()             │  ◄── Common implementation
└────────────┬─────────────┘
        ┌────┴────┐
        ▼         ▼
  CsvProcessor  JsonProcessor
```

## Template Method vs Strategy
| Template Method | Strategy |
|-----------------|----------|
| Inheritance (is-a) | Composition (has-a) |
| Override steps of an algorithm | Replace entire algorithm |
| `abstract class` with `final` template | Interface with multiple implementations |
| Inverted control (Hollywood Principle) | Client controls which strategy |

## Hollywood Principle
> "Don't call us, we'll call you."
The base class calls subclass methods (not the other way around). The framework calls your code.

## Real-World Examples
- **Java I/O**: `InputStream.read(byte[])` calls `read()` (abstract single-byte)
- `java.util.AbstractList`: `get()` and `size()` are template primitives
- **JUnit**: `setUp()` -> `testMethod()` -> `tearDown()`
- **Servlet lifecycle**: `init()` -> `service()` -> `destroy()`
- **Spring**: `JdbcTemplate`, `RestTemplate`, `JmsTemplate`
- **HttpServlet**: `doGet()`, `doPost()` override hooks

## Interview Deep-Dive Questions

**Q: What makes it 'Template'?**
> "The base class method IS the template — it defines the algorithm skeleton. Subclasses fill in the blanks (abstract methods) and optionally customize hooks."

**Q: What's a hook method?**
> "A hook is a method with a default (often empty) implementation that subclasses CAN override but don't have to. It's an optional extension point."

**Q: Why make the template method `final`?**
> "To prevent subclasses from changing the algorithm structure. Subclasses can only override individual steps, not the overall flow."

## Key Points to Mention in Interview
1. Base class defines algorithm skeleton; subclasses implement steps
2. Template method should be `final` (algorithm structure is fixed)
3. Uses the Hollywood Principle (inversion of control)
4. Hook methods provide optional customization points
5. Spring's `*Template` classes (JdbcTemplate, RestTemplate) are named after this pattern
6. Inheritance-based (contrast with Strategy which uses composition)
