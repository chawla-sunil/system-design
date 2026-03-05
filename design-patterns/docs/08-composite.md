# Composite Pattern

## What is it? (One-liner)
Composite lets you compose objects into **tree structures** and treat individual objects and compositions **uniformly**.

## When to Use (Interview Answer)
> "I'd use Composite when I have a **part-whole hierarchy** -- like a file system where directories contain files or other directories, or an org chart where managers have subordinates. The key benefit: client code treats leaves and composites the same way."

## How to Implement
```java
public interface FileSystemComponent {
    String getName();
    long getSize();
    void display(String indent);
}

// Leaf
public class File implements FileSystemComponent {
    public long getSize() { return size; }
}

// Composite
public class Directory implements FileSystemComponent {
    private List<FileSystemComponent> children = new ArrayList<>();

    public void add(FileSystemComponent component) { children.add(component); }

    public long getSize() {
        return children.stream().mapToLong(FileSystemComponent::getSize).sum();
    }
}

// Client treats both uniformly
FileSystemComponent file = new File("doc.txt", 5);
FileSystemComponent dir = new Directory("src");
file.getSize();  // returns 5
dir.getSize();   // returns sum of all children recursively
```

## UML Structure
```
     ┌────────────────────────┐
     │  <<interface>>         │
     │ FileSystemComponent    │
     │ + getName(): String    │
     │ + getSize(): long      │
     └───────────┬────────────┘
         ┌───────┴───────┐
         ▼               ▼
    ┌─────────┐   ┌───────────┐
    │  File   │   │ Directory │
    │ (Leaf)  │   │(Composite)│──┐
    └─────────┘   └───────────┘  │ children
                        ▲        │
                        └────────┘
```

## Real-World Examples
- File systems (files and directories)
- UI component trees (panels containing buttons containing labels)
- `java.awt.Container` extends `Component`
- XML/HTML DOM trees
- Organization hierarchies
- Menu items (menus containing menu items or sub-menus)

## Interview Deep-Dive Questions

**Q: What are the trade-offs?**
> "The main trade-off is that it makes the design overly general. It's hard to restrict what types of children a composite can have (e.g., preventing a file from being added to another file). You sacrifice type safety for uniformity."

**Q: How does Composite follow OCP?**
> "New component types (leaf or composite) can be added without changing existing code. The client works through the common interface."

## Key Points to Mention in Interview
1. Tree structure: Leaf + Composite share a common interface
2. Client treats individual objects and groups uniformly
3. Recursive composition (composites contain components)
4. Mention `getSize()` example: leaf returns own size, composite sums children
5. Common in file systems, UI frameworks, org charts
