package org.designpatterns.creational.factory;

/**
 * Factory class that encapsulates the object creation logic.
 * Client code never needs to know the concrete classes.
 */
public class NotificationFactory {

    public static Notification createNotification(String channel) {
        return switch (channel.toUpperCase()) {
            case "EMAIL" -> new EmailNotification();
            case "SMS" -> new SMSNotification();
            case "PUSH" -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown notification channel: " + channel);
        };
    }
}
