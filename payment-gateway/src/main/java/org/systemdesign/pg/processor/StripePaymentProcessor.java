package org.systemdesign.pg.processor;

import org.systemdesign.pg.model.Payment;

import java.math.BigDecimal;

/**
 * Stripe payment processor — simulates calling Stripe's API.
 *
 * In real world: uses Stripe SDK, handles 3D Secure, webhooks, etc.
 * Here we simulate with a 90% success rate.
 */
public class StripePaymentProcessor implements PaymentProcessor {

    @Override
    public boolean processPayment(Payment payment) {
        System.out.println("[Stripe] Processing payment of " + payment.getAmount() +
                " " + payment.getCurrency() + " for order " + payment.getOrderId());

        // Simulate API call — 90% success rate
        boolean success = Math.random() < 0.9;

        if (success) {
            System.out.println("[Stripe] ✅ Payment successful for " + payment.getPaymentId());
        } else {
            System.out.println("[Stripe] ❌ Payment failed for " + payment.getPaymentId());
        }
        return success;
    }

    @Override
    public boolean processRefund(Payment payment, BigDecimal refundAmount) {
        System.out.println("[Stripe] Processing refund of " + refundAmount +
                " " + payment.getCurrency() + " for payment " + payment.getPaymentId());

        // Simulate — 95% success for refunds
        boolean success = Math.random() < 0.95;

        if (success) {
            System.out.println("[Stripe] ✅ Refund successful");
        } else {
            System.out.println("[Stripe] ❌ Refund failed");
        }
        return success;
    }
}

