package org.designpatterns.behavioral.command;

public class TextEditor {
    private StringBuilder content = new StringBuilder();

    public void insertText(int position, String text) {
        content.insert(position, text);
        System.out.println("  [Editor] Inserted '" + text + "' at position " + position);
    }

    public void deleteText(int position, int length) {
        String deleted = content.substring(position, position + length);
        content.delete(position, position + length);
        System.out.println("  [Editor] Deleted '" + deleted + "' from position " + position);
    }

    public String getContent() {
        return content.toString();
    }

    @Override
    public String toString() {
        return "\"" + content.toString() + "\"";
    }
}
