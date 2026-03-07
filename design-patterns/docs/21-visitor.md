# Visitor Pattern

## What is it? (One-liner)
Visitor lets you **add new operations to existing object structures** without modifying the classes of the elements on which it operates.

## When to Use (Interview Answer)
> "I'd use Visitor when I have a stable set of element types but need to frequently add new operations on them. For example, a shopping cart with Books, Electronics, and Grocery items — I can add price calculation, tax calculation, shipping estimation as separate visitors without touching the item classes."

## The Problem It Solves
```java
// WITHOUT Visitor (messy — every new operation modifies all element classes)
class Book {
    double calculatePrice() { ... }
    double calculateTax() { ... }
    double calculateShipping() { ... }  // Another change in every class!
}

// WITH Visitor (clean — new operations are separate classes)
class TaxCalculatorVisitor implements ShoppingCartVisitor {
    void visit(Book b) { /* tax logic for books */ }
    void visit(Electronics e) { /* tax logic for electronics */ }
}
```

## How to Implement
```java
// Element interface
public interface ShoppingElement {
    void accept(ShoppingCartVisitor visitor);
}

// Concrete element
public class Book implements ShoppingElement {
    public void accept(ShoppingCartVisitor visitor) {
        visitor.visit(this);  // Double dispatch!
    }
}

// Visitor interface
public interface ShoppingCartVisitor {
    void visit(Book book);
    void visit(Electronics electronics);
}

// Concrete visitor (new operation = new class)
public class TaxCalculatorVisitor implements ShoppingCartVisitor {
    public void visit(Book book) { /* book tax logic */ }
    public void visit(Electronics electronics) { /* electronics tax logic */ }
}
```

## UML Structure
```
┌────────────────────────┐      ┌──────────────────────────┐
│  ShoppingCartVisitor   │      │    ShoppingElement        │
├────────────────────────┤      ├──────────────────────────┤
│ + visit(Book)          │◄─────│ + accept(Visitor)         │
│ + visit(Electronics)   │      └──────────┬───────────────┘
│ + visit(Grocery)       │           ┌─────┴─────┐
└────────┬───────────────┘           ▼           ▼
    ┌────┴────┐                   Book      Electronics
    ▼         ▼
PriceCalc  TaxCalc
```

## Double Dispatch
The key mechanism — `accept()` + `visit()` gives us runtime dispatch on **both** the element type and the visitor type:
1. `element.accept(visitor)` → dispatches on element type
2. `visitor.visit(this)` → dispatches on visitor type

## Visitor vs Strategy
| Visitor | Strategy |
|---------|----------|
| Multiple operations on multiple types | One algorithm, swappable |
| Double dispatch (element + visitor) | Single dispatch |
| Works across a class hierarchy | Works within one context |
| Open for new operations, closed for new types | Open for new algorithms |

## When NOT to Use
- When element types change frequently (adding a new element type means updating ALL visitors)
- When the element hierarchy is unstable
- Best when: **stable types, changing operations**

## Real-World Examples
- **Java**: `FileVisitor` for walking file trees (`Files.walkFileTree()`)
- **AST Processing**: Compiler abstract syntax tree traversal
- **Document export**: HTML, PDF, Markdown export from a document model
- **Spring**: `BeanDefinitionVisitor`
- **javax.lang.model**: `ElementVisitor` for annotation processing

## Interview Deep-Dive Questions

**Q: What is double dispatch and why does Visitor need it?**
> "Java only supports single dispatch — the method called depends on the runtime type of the object you call it on. Visitor uses accept()/visit() to achieve double dispatch: the correct visit() method is selected based on BOTH the element type AND the visitor type."

**Q: What's the main trade-off?**
> "Visitor makes it easy to add new operations (new visitors) but hard to add new element types (must update all visitors). It's the opposite of plain polymorphism, where adding types is easy but adding operations requires modifying all types."

