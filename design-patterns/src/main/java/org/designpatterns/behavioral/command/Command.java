package org.designpatterns.behavioral.command;

public interface Command {
    void execute();
    void undo();
    String getDescription();
}
