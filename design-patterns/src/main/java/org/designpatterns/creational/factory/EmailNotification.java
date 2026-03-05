package org.designpatterns.creational.factory;

public class EmailNotification implements Notification {

    @Override
    public void notifyUser(String message) {
        System.out.println("[EMAIL] Sending email: " + message);
    }

    @Override
    public String getChannel() {
        return "Email";
    }
}
