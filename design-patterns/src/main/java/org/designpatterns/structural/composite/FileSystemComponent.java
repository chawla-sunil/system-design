package org.designpatterns.structural.composite;

public interface FileSystemComponent {
    String getName();
    long getSize();
    void display(String indent);
}
