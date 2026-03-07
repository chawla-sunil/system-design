package org.designpatterns.behavioral.memento;

/**
 * Originator - The text editor whose state we want to save and restore.
 * Only the originator knows how to create and restore from a memento.
 */
public class TextEditorOriginator {
    private String content;
    private int cursorPosition;

    public TextEditorOriginator() {
        this.content = "";
        this.cursorPosition = 0;
    }

    public void type(String text) {
        content = content.substring(0, cursorPosition) + text + content.substring(cursorPosition);
        cursorPosition += text.length();
    }

    public void moveCursor(int position) {
        this.cursorPosition = Math.max(0, Math.min(position, content.length()));
    }

    public void deleteLastChar() {
        if (cursorPosition > 0) {
            content = content.substring(0, cursorPosition - 1) + content.substring(cursorPosition);
            cursorPosition--;
        }
    }

    /**
     * Save current state to a memento.
     */
    public EditorMemento save() {
        return new EditorMemento(content, cursorPosition);
    }

    /**
     * Restore state from a memento.
     */
    public void restore(EditorMemento memento) {
        this.content = memento.getContent();
        this.cursorPosition = memento.getCursorPosition();
    }

    public String getContent() { return content; }
    public int getCursorPosition() { return cursorPosition; }

    @Override
    public String toString() {
        // Show cursor position with a | character
        return String.format("\"%s|%s\" (cursor: %d)",
                content.substring(0, cursorPosition),
                content.substring(cursorPosition),
                cursorPosition);
    }
}

