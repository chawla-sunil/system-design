# Prototype Pattern

## What is it? (One-liner)
Prototype creates new objects by **cloning an existing instance** (the prototype), avoiding expensive creation from scratch.

## When to Use (Interview Answer)
> "I'd use Prototype when object creation is **costly** (database calls, network requests, complex computation) and I need many similar objects. Instead of creating from scratch each time, I clone an existing prototype and modify what's different."

## How to Implement
```java
// Abstract prototype
public abstract class Document implements Cloneable {
    private String title;
    private String content;

    protected Document(Document source) {
        this.title = source.title;
        this.content = source.content;
    }

    public abstract Document cloneDocument();
}

// Concrete prototype
public class Report extends Document {
    private String reportType;

    private Report(Report source) {
        super(source);
        this.reportType = source.reportType;
    }

    @Override
    public Document cloneDocument() {
        return new Report(this);  // Copy constructor (preferred over clone())
    }
}

// Prototype Registry
public class DocumentRegistry {
    private Map<String, Document> prototypes = new HashMap<>();

    public void register(String key, Document prototype) {
        prototypes.put(key, prototype);
    }

    public Document create(String key) {
        return prototypes.get(key).cloneDocument();
    }
}
```

## UML Structure
```
┌──────────────────────┐
│   <<interface>>      │
│    Cloneable         │
└──────────┬───────────┘
┌──────────┴───────────┐      ┌─────────────────┐
│   Document           │◄─────│ DocumentRegistry │
├──────────────────────┤      ├─────────────────┤
│ - title              │      │ - prototypes: Map│
│ - content            │      ├─────────────────┤
├──────────────────────┤      │ + register()    │
│ + cloneDocument()    │      │ + create()      │
└──────────┬───────────┘      └─────────────────┘
      ┌────┴────┐
      ▼         ▼
   Report   Spreadsheet
```

## Deep Copy vs Shallow Copy
```
Shallow Copy: New object, but references point to SAME nested objects
Deep Copy:    New object, AND all nested objects are also copied

// Shallow (dangerous if mutating nested objects)
Person clone = original;  // same reference

// Deep copy (safe)
Person clone = new Person(original.getName(), new Address(original.getAddress()));
```

**Interview tip:** Always mention whether your clone is shallow or deep, and why.

## Real-World Examples
- `Object.clone()` in Java
- `Cloneable` interface
- Spreadsheet: copy a cell/row with formatting
- Game development: spawning enemies from templates
- Configuration templates

## Interview Deep-Dive Questions

**Q: Why not just use `new`?**
> "When the initialization is expensive (loading from DB, parsing large files, complex calculations), cloning avoids repeating that cost."

**Q: `clone()` vs Copy Constructor?**
| `Object.clone()` | Copy Constructor |
|-------------------|-----------------|
| Shallow copy by default | You control depth |
| Requires `Cloneable` interface | No interface needed |
| Returns `Object` (needs cast) | Type-safe |
| Broken by design (Effective Java) | **Recommended approach** |

**Q: How to handle deep copy of complex object graphs?**
> "Three approaches: (1) Copy constructors for each nested object, (2) Serialization/Deserialization, (3) Libraries like Apache Commons `SerializationUtils.clone()`."

## Key Points to Mention in Interview
1. Avoids costly object creation by cloning
2. Use **copy constructors** over `Object.clone()` (Effective Java Item 13)
3. Always clarify shallow vs deep copy
4. Prototype Registry caches pre-built objects for cloning
5. Useful when the exact type isn't known at compile time (polymorphic cloning)
