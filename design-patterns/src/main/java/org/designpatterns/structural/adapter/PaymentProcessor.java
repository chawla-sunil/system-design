package org.designpatterns.structural.adapter;

public interface PaymentProcessor {
    void processPayment(double amount, String currency);
    boolean refund(String transactionId, double amount);
    String getProviderName();
}
