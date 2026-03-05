package org.designpatterns.behavioral.command;

import java.util.ArrayDeque;
import java.util.Deque;

public class CommandHistory {
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    public void executeCommand(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear(); // Clear redo stack on new command
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            System.out.println("  [History] Nothing to undo");
            return;
        }
        Command command = undoStack.pop();
        System.out.println("  [History] Undoing: " + command.getDescription());
        command.undo();
        redoStack.push(command);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            System.out.println("  [History] Nothing to redo");
            return;
        }
        Command command = redoStack.pop();
        System.out.println("  [History] Redoing: " + command.getDescription());
        command.execute();
        undoStack.push(command);
    }
}
