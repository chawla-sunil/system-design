# Interpreter Pattern

## What is it? (One-liner)
Interpreter defines a **grammar for a language** and provides an **interpreter** that uses the grammar to interpret sentences in that language.

## When to Use (Interview Answer)
> "I'd use Interpreter when I need to evaluate expressions or parse a simple language. For example, a math expression evaluator: '3 + 5 * 2' gets parsed into a tree where each node knows how to interpret itself. It's ideal for DSLs (Domain-Specific Languages) with simple grammars."

## The Problem It Solves
```java
// WITHOUT Interpreter (hardcoded, inflexible)
int evaluate(String expr) {
    // Giant if/else or switch parsing logic
    // Hard to extend with new operators
}

// WITH Interpreter (composable expression tree)
Expression expr = new AddExpression(
    new NumberExpression(3),
    new MultiplyExpression(new NumberExpression(5), new NumberExpression(2))
);
int result = expr.interpret();  // Each node interprets itself
```

## How to Implement
```java
// Abstract Expression
public interface Expression {
    int interpret();
}

// Terminal Expression (leaf)
public class NumberExpression implements Expression {
    private final int number;
    public int interpret() { return number; }
}

// Non-Terminal Expression (composite)
public class AddExpression implements Expression {
    private final Expression left, right;
    public int interpret() {
        return left.interpret() + right.interpret();
    }
}

// Parser/Client builds the expression tree
Expression expr = parser.parse("3 + 5 - 2");
int result = expr.interpret();  // 6
```

## UML Structure
```
┌───────────────────┐
│   Expression      │ ◄── Abstract Expression
├───────────────────┤
│ + interpret(): int│
└────────┬──────────┘
    ┌────┴──────────────────┐
    ▼                       ▼
┌───────────────┐   ┌──────────────────┐
│ NumberExpr    │   │ AddExpression     │
│ (Terminal)    │   │ (Non-Terminal)    │
├───────────────┤   ├──────────────────┤
│ - number: int │   │ - left: Expr     │
│ + interpret() │   │ - right: Expr    │
└───────────────┘   │ + interpret()    │
                    └──────────────────┘
                    SubtractExpr, MultiplyExpr...
```

## Expression Tree Example
```
Expression: "3 + 5 - 2"

        [-]
       /   \
     [+]    [2]
    /   \
  [3]   [5]

interpret() walks the tree bottom-up:
  [3] → 3, [5] → 5, [+] → 8, [2] → 2, [-] → 6
```

## Terminal vs Non-Terminal
| Terminal Expression | Non-Terminal Expression |
|-------------------|----------------------|
| Leaf nodes (numbers, variables) | Composite nodes (operators) |
| Directly returns a value | Combines child results |
| `NumberExpression` | `AddExpression`, `SubtractExpression` |
| No children | Has left/right children |

## Interpreter vs Visitor
| Interpreter | Visitor |
|------------|---------|
| Each node interprets itself | External visitor operates on nodes |
| interpret() defined in each expression | visit() defined in visitor |
| Better for simple grammars | Better for complex operations on existing structures |
| Expression tree builds the language | Object structure already exists |

## When NOT to Use
- Complex grammars — use a proper parser generator (ANTLR, JavaCC)
- Performance-critical — expression trees have overhead
- Better alternatives: Strategy (for simple algorithm selection), State machines

## Real-World Examples
- **java.util.regex.Pattern** — regex is an interpreted language
- **SQL parsers** — SQL is interpreted into query execution plans
- **Spring SpEL** — Spring Expression Language
- **JSP EL** — `${expression}` in JSP pages
- **Rule engines** — Drools, business rule evaluation
- **Calculator apps** — math expression parsing

## Interview Deep-Dive Questions

**Q: Why is Interpreter rarely used directly?**
> "For complex grammars, the number of classes explodes (one per grammar rule). Tools like ANTLR or JavaCC generate parsers automatically. Interpreter is great for simple, well-defined DSLs — like config expressions, filter criteria, or simple math."

**Q: How does it relate to Composite?**
> "Interpreter IS essentially Composite applied to a grammar. Terminal expressions are leaves, non-terminal expressions are composites. The `interpret()` method recursively traverses the tree, just like Composite's operation method."

**Q: Can you optimize an Interpreter?**
> "Yes — use Flyweight for shared terminal expressions (e.g., common numbers), cache interpret() results, or compile the expression tree into bytecode for repeated evaluation."

