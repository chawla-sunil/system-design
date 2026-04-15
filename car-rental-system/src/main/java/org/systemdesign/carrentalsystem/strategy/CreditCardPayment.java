package org.systemdesign.carrentalsystem.strategy;

public class CreditCardPayment implements PaymentStrategy {
    private final String cardNumber;
    private final String cardHolderName;

    public CreditCardPayment(String cardNumber, String cardHolderName) {
        this.cardNumber = cardNumber;
        this.cardHolderName = cardHolderName;
    }

    @Override
    public boolean pay(double amount) {
        // Simulate credit card payment gateway call
        System.out.println("  💳 Processing Credit Card payment of ₹" + String.format("%.2f", amount) +
                " for card ending in ****" + cardNumber.substring(cardNumber.length() - 4));
        // In production: call payment gateway API
        return true;
    }

    @Override
    public boolean refund(double amount) {
        System.out.println("  💳 Refunding ₹" + String.format("%.2f", amount) +
                " to credit card ending in ****" + cardNumber.substring(cardNumber.length() - 4));
        return true;
    }
}

