package org.systemdesign.carrentalsystem.strategy;

public class CashPayment implements PaymentStrategy {

    @Override
    public boolean pay(double amount) {
        System.out.println("  💵 Processing Cash payment of ₹" + String.format("%.2f", amount));
        return true;
    }

    @Override
    public boolean refund(double amount) {
        System.out.println("  💵 Refunding ₹" + String.format("%.2f", amount) + " in cash");
        return true;
    }
}

