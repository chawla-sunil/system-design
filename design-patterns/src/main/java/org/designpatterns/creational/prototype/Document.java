package org.designpatterns.creational.prototype;

/**
 * Prototype Pattern - Document Cloning Example
 *
 * Creates new objects by copying an existing object (prototype),
 * avoiding expensive creation from scratch.
 */
public abstract class Document implements Cloneable {

    private String title;
    private String content;
    private String author;

    public Document(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
        // Simulate expensive initialization
        System.out.println("  [Expensive init] Creating document: " + title);
    }

    // Protected copy constructor for cloning
    protected Document(Document source) {
        this.title = source.title;
        this.content = source.content;
        this.author = source.author;
    }

    public abstract Document cloneDocument();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{title='" + title + "', author='" + author +
                "', content='" + content.substring(0, Math.min(content.length(), 40)) + "...'}";
    }
}
