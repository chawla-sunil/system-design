package org.systemdesign.carrentalsystem.strategy;

public class UpiPayment implements PaymentStrategy {
    private final String upiId;

    public UpiPayment(String upiId) {
        this.upiId = upiId;
    }

    @Override
    public boolean pay(double amount) {
        System.out.println("  📱 Processing UPI payment of ₹" + String.format("%.2f", amount) +
                " via UPI ID: " + upiId);
        return true;
    }

    @Override
    public boolean refund(double amount) {
        System.out.println("  📱 Refunding ₹" + String.format("%.2f", amount) +
                " to UPI ID: " + upiId);
        return true;
    }
}

