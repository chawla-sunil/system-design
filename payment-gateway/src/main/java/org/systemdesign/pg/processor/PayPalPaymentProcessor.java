package org.systemdesign.pg.processor;

import org.systemdesign.pg.model.Payment;

import java.math.BigDecimal;

/**
 * PayPal payment processor — simulates calling PayPal's API.
 */
public class PayPalPaymentProcessor implements PaymentProcessor {

    @Override
    public boolean processPayment(Payment payment) {
        System.out.println("[PayPal] Processing payment of " + payment.getAmount() +
                " " + payment.getCurrency() + " for order " + payment.getOrderId());

        boolean success = Math.random() < 0.85;

        if (success) {
            System.out.println("[PayPal] ✅ Payment successful for " + payment.getPaymentId());
        } else {
            System.out.println("[PayPal] ❌ Payment failed for " + payment.getPaymentId());
        }
        return success;
    }

    @Override
    public boolean processRefund(Payment payment, BigDecimal refundAmount) {
        System.out.println("[PayPal] Processing refund of " + refundAmount +
                " " + payment.getCurrency() + " for payment " + payment.getPaymentId());

        boolean success = Math.random() < 0.90;

        if (success) {
            System.out.println("[PayPal] ✅ Refund successful");
        } else {
            System.out.println("[PayPal] ❌ Refund failed");
        }
        return success;
    }
}

