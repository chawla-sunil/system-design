package org.designpatterns.behavioral.mediator;

import java.util.ArrayList;
import java.util.List;

public class ChatRoom implements ChatMediator {
    private final String roomName;
    private final List<User> users = new ArrayList<>();

    public ChatRoom(String roomName) {
        this.roomName = roomName;
    }

    @Override
    public void addUser(User user) {
        users.add(user);
        System.out.println("  [" + roomName + "] " + user.getName() + " joined the room");
    }

    @Override
    public void sendMessage(String message, User sender) {
        for (User user : users) {
            // Don't send message back to sender
            if (user != sender) {
                user.receive(message, sender.getName());
            }
        }
    }
}
