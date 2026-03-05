package org.designpatterns.structural.adapter;

public class StripeApi {
    public String createCharge(int amountInCents, String cur, String description) {
        String txnId = "stripe_txn_" + System.currentTimeMillis();
        System.out.println("  [Stripe] Charge created: " + amountInCents + " cents " + cur + " -> " + txnId);
        return txnId;
    }

    public boolean reverseCharge(String chargeId) {
        System.out.println("  [Stripe] Charge reversed: " + chargeId);
        return true;
    }
}
