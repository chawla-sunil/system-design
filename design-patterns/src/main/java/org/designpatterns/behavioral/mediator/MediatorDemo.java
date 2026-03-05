package org.designpatterns.behavioral.mediator;

public class MediatorDemo {
    public static void run() {
        System.out.println("=== MEDIATOR PATTERN DEMO ===\n");

        // Create the mediator (chat room)
        ChatRoom devRoom = new ChatRoom("dev-team");

        // Create users - they communicate through the mediator, not directly
        ChatUser alice = new ChatUser("Alice", devRoom);
        ChatUser bob = new ChatUser("Bob", devRoom);
        ChatUser charlie = new ChatUser("Charlie", devRoom);

        devRoom.addUser(alice);
        devRoom.addUser(bob);
        devRoom.addUser(charlie);

        System.out.println();

        // Users send messages through the mediator
        alice.send("Hey team, the build is broken!");
        System.out.println();
        bob.send("I'll take a look at it.");
        System.out.println();
        charlie.send("I think I found the issue in the config.");

        System.out.println();
    }
}
