# Memento Pattern

## What is it? (One-liner)
Memento captures and externalizes an object's **internal state** so it can be **restored later**, without violating encapsulation.

## When to Use (Interview Answer)
> "I'd use Memento when I need undo/redo or rollback functionality. For example, a text editor that saves snapshots of its state — you can undo typing by restoring a previous snapshot. The key is that the saved state is opaque to everyone except the originator."

## The Problem It Solves
```java
// WITHOUT Memento (breaks encapsulation — everyone sees internal state)
class Editor {
    public String content;     // Public! Anyone can access/modify
    public int cursorPosition; // No encapsulation
}

// WITH Memento (encapsulated state snapshots)
EditorMemento snapshot = editor.save();  // Opaque snapshot
editor.type("new text");
editor.restore(snapshot);                // Undo! Back to previous state
```

## How to Implement
```java
// Memento - immutable snapshot of state
public class EditorMemento {
    private final String content;
    private final int cursorPosition;
    // Package-private getters — only originator can read
    String getContent() { return content; }
}

// Originator - creates and restores from mementos
public class TextEditor {
    private String content;
    private int cursorPosition;

    public EditorMemento save() {
        return new EditorMemento(content, cursorPosition);
    }
    public void restore(EditorMemento memento) {
        this.content = memento.getContent();
        this.cursorPosition = memento.getCursorPosition();
    }
}

// Caretaker - manages memento history (never peeks inside)
public class EditorHistory {
    private Deque<EditorMemento> undoStack = new ArrayDeque<>();
    public void save(EditorMemento m) { undoStack.push(m); }
    public EditorMemento undo() { return undoStack.pop(); }
}
```

## UML Structure
```
┌──────────────────────┐     ┌───────────────────┐     ┌──────────────────┐
│  EditorHistory       │     │  TextEditor        │     │  EditorMemento   │
│  (Caretaker)         │     │  (Originator)      │     │  (Memento)       │
├──────────────────────┤     ├───────────────────┤     ├──────────────────┤
│ - undoStack: Deque   │     │ - content          │────>│ - content        │
│ - redoStack: Deque   │     │ - cursorPosition   │     │ - cursorPosition │
├──────────────────────┤     ├───────────────────┤     ├──────────────────┤
│ + save(memento)      │     │ + save(): Memento  │     │ ~ getContent()   │
│ + undo(): Memento    │     │ + restore(Memento) │     │ ~ getCursor()    │
│ + redo(): Memento    │     │ + type(text)       │     └──────────────────┘
└──────────────────────┘     └───────────────────┘
```

## Three Key Roles
| Role | Class | Responsibility |
|------|-------|---------------|
| **Originator** | TextEditor | Creates mementos, restores from them |
| **Memento** | EditorMemento | Stores internal state (immutable) |
| **Caretaker** | EditorHistory | Manages undo/redo stacks, never peeks inside memento |

## Memento vs Command (both support Undo)
| Memento | Command |
|---------|---------|
| Stores full state snapshot | Stores the action + inverse action |
| Simpler for complex state | More memory-efficient for simple actions |
| Always correct (full rollback) | Must implement `undo()` correctly per command |
| Memory-heavy if state is large | Better for command logging and replay |

## Real-World Examples
- **java.io.Serializable** — serialize/deserialize object state
- **javax.swing.undo.UndoManager** — Swing undo framework
- **Database transactions** — savepoints and rollbacks
- **Git stash** — saves and restores working directory state
- **Game save/load** — snapshot of entire game state
- **IDE undo** — IntelliJ/VS Code local history

## Interview Deep-Dive Questions

**Q: How does Memento preserve encapsulation?**
> "The Memento stores internal state, but only the Originator can read it. The Caretaker holds mementos but treats them as opaque tokens. In Java, we achieve this with package-private access — memento getters are accessible to the originator (same package) but not to external classes."

**Q: What about memory concerns?**
> "Storing full snapshots can be expensive. Solutions: (1) Store only deltas/diffs instead of full state, (2) Limit undo history size, (3) Use compression, (4) Combine with Command pattern — use commands for recent history and mementos for checkpoints."

**Q: Can you combine Memento with other patterns?**
> "Yes! Command + Memento: Command stores the memento before executing, uses it for undo. Prototype can be used to clone state into mementos. Iterator can traverse memento history."

