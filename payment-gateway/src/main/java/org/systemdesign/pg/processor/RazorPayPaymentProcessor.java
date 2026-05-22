package org.systemdesign.pg.processor;

import org.systemdesign.pg.model.Payment;

import java.math.BigDecimal;

/**
 * RazorPay payment processor — simulates calling RazorPay's API.
 * Popular in India for UPI, cards, net banking.
 */
public class RazorPayPaymentProcessor implements PaymentProcessor {

    @Override
    public boolean processPayment(Payment payment) {
        System.out.println("[RazorPay] Processing payment of " + payment.getAmount() +
                " " + payment.getCurrency() + " for order " + payment.getOrderId());

        boolean success = Math.random() < 0.92;

        if (success) {
            System.out.println("[RazorPay] ✅ Payment successful for " + payment.getPaymentId());
        } else {
            System.out.println("[RazorPay] ❌ Payment failed for " + payment.getPaymentId());
        }
        return success;
    }

    @Override
    public boolean processRefund(Payment payment, BigDecimal refundAmount) {
        System.out.println("[RazorPay] Processing refund of " + refundAmount +
                " " + payment.getCurrency() + " for payment " + payment.getPaymentId());

        boolean success = Math.random() < 0.93;

        if (success) {
            System.out.println("[RazorPay] ✅ Refund successful");
        } else {
            System.out.println("[RazorPay] ❌ Refund failed");
        }
        return success;
    }
}

