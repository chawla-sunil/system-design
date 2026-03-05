package org.designpatterns.structural.adapter;

public class PayPalAdapter implements PaymentProcessor {
    private final PayPalApi payPalApi;
    private final String merchantEmail;

    public PayPalAdapter(PayPalApi payPalApi, String merchantEmail) {
        this.payPalApi = payPalApi;
        this.merchantEmail = merchantEmail;
    }

    @Override
    public void processPayment(double amount, String currency) {
        payPalApi.sendPayment(merchantEmail, amount);
    }

    @Override
    public boolean refund(String transactionId, double amount) {
        return payPalApi.issueRefund(transactionId);
    }

    @Override
    public String getProviderName() {
        return "PayPal";
    }
}
