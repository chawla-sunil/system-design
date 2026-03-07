package org.designpatterns.behavioral.memento;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Caretaker - Manages memento history (undo/redo stacks).
 * The caretaker never looks inside the memento — it just stores and retrieves them.
 */
public class EditorHistory {
    private final Deque<EditorMemento> undoStack = new ArrayDeque<>();
    private final Deque<EditorMemento> redoStack = new ArrayDeque<>();

    public void save(EditorMemento memento) {
        undoStack.push(memento);
        redoStack.clear();  // New action invalidates redo history
    }

    public EditorMemento undo() {
        if (undoStack.isEmpty()) {
            System.out.println("  Nothing to undo!");
            return null;
        }
        EditorMemento memento = undoStack.pop();
        redoStack.push(memento);
        return undoStack.isEmpty() ? new EditorMemento("", 0) : undoStack.peek();
    }

    public EditorMemento redo() {
        if (redoStack.isEmpty()) {
            System.out.println("  Nothing to redo!");
            return null;
        }
        EditorMemento memento = redoStack.pop();
        undoStack.push(memento);
        return memento;
    }

    public int getUndoCount() { return undoStack.size(); }
    public int getRedoCount() { return redoStack.size(); }
}

