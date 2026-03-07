package org.designpatterns.behavioral.memento;

/**
 * Memento - Stores a snapshot of the editor's internal state.
 * Immutable — once created, the state cannot be changed.
 */
public class EditorMemento {
    private final String content;
    private final int cursorPosition;
    private final String timestamp;

    public EditorMemento(String content, int cursorPosition) {
        this.content = content;
        this.cursorPosition = cursorPosition;
        this.timestamp = java.time.LocalTime.now().toString();
    }

    // Package-private — only accessible within the same package (not to outsiders)
    String getContent() { return content; }
    int getCursorPosition() { return cursorPosition; }
    String getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] content=\"%s\", cursor=%d", timestamp, content, cursorPosition);
    }
}

