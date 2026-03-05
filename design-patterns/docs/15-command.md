# Command Pattern

## What is it? (One-liner)
Command encapsulates a **request as an object**, letting you parameterize clients with different requests, queue or log requests, and support **undo/redo** operations.

## When to Use (Interview Answer)
> "I'd use Command when I need to decouple the sender of a request from the receiver, support undo/redo, queue operations, or log requests for replay. The classic example is a text editor with undo/redo, or a task queue system."

## How to Implement
```java
public interface Command {
    void execute();
    void undo();
}

public class InsertCommand implements Command {
    private final TextEditor editor;
    private final String text;
    private final int position;

    public void execute() { editor.insertText(position, text); }
    public void undo()    { editor.deleteText(position, text.length()); }
}

// Invoker with history
public class CommandHistory {
    private Deque<Command> undoStack = new ArrayDeque<>();

    public void executeCommand(Command cmd) {
        cmd.execute();
        undoStack.push(cmd);
    }

    public void undo() {
        undoStack.pop().undo();
    }
}
```

## UML Structure
```
┌────────────────┐    ┌──────────────────┐    ┌──────────────┐
│    Invoker     │───>│  <<interface>>   │───>│   Receiver   │
│ CommandHistory │    │    Command       │    │  TextEditor  │
│ + execute()    │    │  + execute()     │    │ + insertText │
│ + undo()       │    │  + undo()        │    │ + deleteText │
└────────────────┘    └────────┬─────────┘    └──────────────┘
                       ┌───────┴───────┐
                       ▼               ▼
                 InsertCommand    DeleteCommand
```

## Real-World Examples
- **Java**: `Runnable` is a Command (execute = `run()`)
- `java.util.concurrent.Callable`
- Swing `Action` interface
- **Undo systems**: Text editors, Photoshop, IDEs
- **Task queues**: Thread pools, job schedulers
- **Transaction logs**: Database WAL, Event Sourcing
- **Macro recording**: Record and replay sequences

## Interview Deep-Dive Questions

**Q: Command vs Strategy?**
| Command | Strategy |
|---------|----------|
| Encapsulates a request/action | Encapsulates an algorithm |
| Has undo capability | No undo concept |
| Can be queued/logged | Swapped at runtime for context |
| Often has receiver reference | Self-contained algorithm |

**Q: How does Command relate to Event Sourcing?**
> "In Event Sourcing, every state change is stored as a Command/Event. To rebuild state, you replay all commands. This gives you a complete audit log and undo capability — same principle as Command pattern."

**Q: Why Runnable is a Command?**
> "Runnable encapsulates an action (`run()`) that can be passed to thread pools, scheduled, queued — exactly what Command pattern enables."

## Key Points to Mention in Interview
1. Encapsulates request as object
2. Enables undo/redo via execute/undo methods
3. Commands can be queued, logged, scheduled
4. Decouples invoker from receiver
5. Runnable/Callable are Java's built-in Command implementations
6. Foundation of Event Sourcing architecture
