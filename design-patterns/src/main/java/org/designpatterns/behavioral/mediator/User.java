package org.designpatterns.behavioral.mediator;

public abstract class User {
    protected final String name;
    protected ChatMediator mediator;

    public User(String name, ChatMediator mediator) {
        this.name = name;
        this.mediator = mediator;
    }

    public String getName() { return name; }
    public abstract void send(String message);
    public abstract void receive(String message, String fromUser);
}
