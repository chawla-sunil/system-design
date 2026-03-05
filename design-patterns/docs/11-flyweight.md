# Flyweight Pattern

## What is it? (One-liner)
Flyweight uses **sharing to support large numbers of fine-grained objects** efficiently by separating intrinsic (shared) state from extrinsic (unique) state.

## When to Use (Interview Answer)
> "I'd use Flyweight when an application needs a huge number of similar objects that would consume too much memory. By sharing the common parts (intrinsic state) and keeping the unique parts (extrinsic state) external, we dramatically reduce memory usage."

## Key Concept: Intrinsic vs Extrinsic State
```
Intrinsic State (shared):  Font family, size, color  → stored in Flyweight
Extrinsic State (unique):  Character value, position  → stored in Context

1000 characters might need only 5 style objects!
```

## How to Implement
```java
// Flyweight (shared)
public class CharacterStyle {
    private final String font;  // Intrinsic - shared
    private final int size;
    private final String color;
    public void render(char c, int row, int col) { ... } // Extrinsic passed in
}

// Factory ensures sharing
public class CharacterStyleFactory {
    private Map<String, CharacterStyle> cache = new HashMap<>();

    public CharacterStyle getStyle(String font, int size, String color) {
        String key = font + "-" + size + "-" + color;
        return cache.computeIfAbsent(key, k -> new CharacterStyle(font, size, color));
    }
}
```

## UML Structure
```
┌──────────────────────┐     ┌──────────────────────┐
│  CharacterStyle      │◄────│ CharacterStyleFactory │
│  (Flyweight)         │     │ - cache: Map          │
├──────────────────────┤     │ + getStyle(): Style   │
│ - font    (intrinsic)│     └──────────────────────┘
│ - size    (intrinsic)│
│ - color   (intrinsic)│            ┌──────────────────┐
├──────────────────────┤     ┌─────>│  TextCharacter    │
│ + render(char, row,  │     │      │  (Context)        │
│          col)        │     │      ├──────────────────┤
└──────────────────────┘     │      │ - char (extrinsic)│
         ▲                   │      │ - row  (extrinsic)│
         └───────────────────┘      │ - style: Style    │
                shares              └──────────────────┘
```

## Real-World Examples
- `String.intern()` — Java's String pool IS a Flyweight
- `Integer.valueOf()` — caches -128 to 127 (Integer cache)
- `Boolean.valueOf()` — only two instances
- Game engines: shared textures, sprites
- Text editors: shared font/style objects
- `EnumSet` / `EnumMap`

## Interview Deep-Dive Questions

**Q: Flyweight vs Singleton?**
| Flyweight | Singleton |
|-----------|-----------|
| Multiple shared instances | One instance |
| Identified by state (key) | Single global instance |
| Factory manages sharing | Class manages itself |

**Q: Thread safety?**
> "Flyweight objects should be immutable (only intrinsic state, which is read-only). Immutable objects are inherently thread-safe."

## Key Points to Mention in Interview
1. Separate intrinsic (shared) from extrinsic (unique) state
2. Factory ensures flyweights are shared, not duplicated
3. Flyweight objects should be **immutable**
4. Integer cache (-128 to 127) is the easiest Java example
5. Dramatically reduces memory for large numbers of similar objects
