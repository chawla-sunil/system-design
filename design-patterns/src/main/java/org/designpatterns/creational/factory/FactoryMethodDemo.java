package org.designpatterns.creational.factory;

public class FactoryMethodDemo {

    public static void run() {
        System.out.println("=== FACTORY METHOD PATTERN DEMO ===\n");

        // Client code doesn't know about concrete classes
        Notification email = NotificationFactory.createNotification("EMAIL");
        Notification sms = NotificationFactory.createNotification("SMS");
        Notification push = NotificationFactory.createNotification("PUSH");

        email.notifyUser("Your order has been confirmed!");
        sms.notifyUser("OTP: 123456");
        push.notifyUser("New message from John");

        System.out.println("\nChannels used: " + email.getChannel() + ", " + sms.getChannel() + ", " + push.getChannel());
        System.out.println();
    }
}
