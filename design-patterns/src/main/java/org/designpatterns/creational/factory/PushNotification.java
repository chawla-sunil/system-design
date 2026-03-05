package org.designpatterns.creational.factory;

public class PushNotification implements Notification {

    @Override
    public void notifyUser(String message) {
        System.out.println("[PUSH] Sending push notification: " + message);
    }

    @Override
    public String getChannel() {
        return "Push";
    }
}
