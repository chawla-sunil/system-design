package org.designpatterns.structural.adapter;

public class StripeAdapter implements PaymentProcessor {
    private final StripeApi stripeApi;

    public StripeAdapter(StripeApi stripeApi) {
        this.stripeApi = stripeApi;
    }

    @Override
    public void processPayment(double amount, String currency) {
        int amountInCents = (int) (amount * 100);
        stripeApi.createCharge(amountInCents, currency, "Payment via adapter");
    }

    @Override
    public boolean refund(String transactionId, double amount) {
        return stripeApi.reverseCharge(transactionId);
    }

    @Override
    public String getProviderName() {
        return "Stripe";
    }
}
