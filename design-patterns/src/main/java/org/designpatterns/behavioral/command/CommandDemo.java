package org.designpatterns.behavioral.command;

public class CommandDemo {
    public static void run() {
        System.out.println("=== COMMAND PATTERN DEMO ===\n");

        TextEditor editor = new TextEditor();
        CommandHistory history = new CommandHistory();

        // Execute commands
        System.out.println("--- Executing commands ---");
        history.executeCommand(new InsertCommand(editor, 0, "Hello"));
        System.out.println("  Content: " + editor);

        history.executeCommand(new InsertCommand(editor, 5, " World"));
        System.out.println("  Content: " + editor);

        history.executeCommand(new InsertCommand(editor, 5, " Beautiful"));
        System.out.println("  Content: " + editor);

        // Undo
        System.out.println("\n--- Undo operations ---");
        history.undo();
        System.out.println("  Content: " + editor);

        history.undo();
        System.out.println("  Content: " + editor);

        // Redo
        System.out.println("\n--- Redo operations ---");
        history.redo();
        System.out.println("  Content: " + editor);

        // Delete command
        System.out.println("\n--- Delete command ---");
        history.executeCommand(new DeleteCommand(editor, 0, 5));
        System.out.println("  Content: " + editor);

        // Undo delete
        System.out.println("\n--- Undo delete ---");
        history.undo();
        System.out.println("  Content: " + editor);

        System.out.println();
    }
}
