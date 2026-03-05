package org.designpatterns.creational.factory;

public class SMSNotification implements Notification {

    @Override
    public void notifyUser(String message) {
        System.out.println("[SMS] Sending SMS: " + message);
    }

    @Override
    public String getChannel() {
        return "SMS";
    }
}
