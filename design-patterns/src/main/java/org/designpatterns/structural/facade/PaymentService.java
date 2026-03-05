package org.designpatterns.structural.facade;

public class PaymentService {
    public boolean validateCard(String cardNumber) {
        System.out.println("  [Payment] Validating card: ****" + cardNumber.substring(cardNumber.length() - 4));
        return true;
    }

    public String chargeCard(String cardNumber, double amount) {
        String txnId = "PAY-" + System.currentTimeMillis();
        System.out.println("  [Payment] Charged $" + String.format("%.2f", amount) + " -> " + txnId);
        return txnId;
    }
}
