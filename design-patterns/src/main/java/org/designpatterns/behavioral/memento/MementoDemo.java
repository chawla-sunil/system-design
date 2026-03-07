package org.designpatterns.behavioral.memento;

public class MementoDemo {
    public static void run() {
        System.out.println("=== MEMENTO PATTERN DEMO ===\n");

        TextEditorOriginator editor = new TextEditorOriginator();
        EditorHistory history = new EditorHistory();

        // Type some text and save snapshots
        System.out.println("--- Typing with auto-save ---");

        editor.type("Hello");
        history.save(editor.save());
        System.out.println("  After typing 'Hello': " + editor);

        editor.type(" World");
        history.save(editor.save());
        System.out.println("  After typing ' World': " + editor);

        editor.type("! Welcome to Design Patterns");
        history.save(editor.save());
        System.out.println("  After typing '! Welcome to Design Patterns': " + editor);

        // Undo
        System.out.println("\n--- Undo Operations ---");
        EditorMemento undone1 = history.undo();
        if (undone1 != null) {
            editor.restore(undone1);
            System.out.println("  After undo: " + editor);
        }

        EditorMemento undone2 = history.undo();
        if (undone2 != null) {
            editor.restore(undone2);
            System.out.println("  After undo: " + editor);
        }

        // Redo
        System.out.println("\n--- Redo Operation ---");
        EditorMemento redone = history.redo();
        if (redone != null) {
            editor.restore(redone);
            System.out.println("  After redo: " + editor);
        }

        // Type new text (clears redo stack)
        System.out.println("\n--- New typing (clears redo) ---");
        editor.type(" Java");
        history.save(editor.save());
        System.out.println("  After typing ' Java': " + editor);
        System.out.printf("  Undo stack: %d, Redo stack: %d%n",
                history.getUndoCount(), history.getRedoCount());

        System.out.println();
    }
}

