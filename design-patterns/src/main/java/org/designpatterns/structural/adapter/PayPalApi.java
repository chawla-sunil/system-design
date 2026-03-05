package org.designpatterns.structural.adapter;

public class PayPalApi {
    public boolean sendPayment(String recipient, double amount) {
        System.out.println("  [PayPal] Payment sent: $" + amount + " to " + recipient);
        return true;
    }

    public boolean issueRefund(String paymentId) {
        System.out.println("  [PayPal] Refund issued for: " + paymentId);
        return true;
    }
}
