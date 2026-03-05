package org.designpatterns.structural.facade;

public class NotificationService {
    public void sendOrderConfirmation(String email, String orderId) {
        System.out.println("  [Notification] Order confirmation sent to " + email + " for " + orderId);
    }

    public void sendShippingUpdate(String email, String trackingId) {
        System.out.println("  [Notification] Shipping update sent to " + email + ": " + trackingId);
    }
}
