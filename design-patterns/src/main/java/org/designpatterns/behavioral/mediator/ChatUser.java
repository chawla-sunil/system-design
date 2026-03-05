package org.designpatterns.behavioral.mediator;

public class ChatUser extends User {
    public ChatUser(String name, ChatMediator mediator) {
        super(name, mediator);
    }

    @Override
    public void send(String message) {
        System.out.println("  [" + name + "] sends: " + message);
        mediator.sendMessage(message, this);
    }

    @Override
    public void receive(String message, String fromUser) {
        System.out.println("  [" + name + "] received from " + fromUser + ": " + message);
    }
}
