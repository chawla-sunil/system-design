package org.systemdesign.pg.observer;

import org.systemdesign.pg.model.Payment;

import java.math.BigDecimal;

/**
 * Notification listener — sends notifications on payment events.
 *
 * In real world: sends email, SMS, push notifications, or webhook callbacks to merchants.
 */
public class NotificationEventListener implements PaymentEventListener {

    @Override
    public void onPaymentInitiated(Payment payment) {
        System.out.println("[📧 Notification] Payment initiated for order " +
                payment.getOrderId() + " — awaiting processing");
    }

    @Override
    public void onPaymentSuccess(Payment payment) {
        System.out.println("[📧 Notification] Payment of " + payment.getAmount() +
                " " + payment.getCurrency() + " successful! Order: " + payment.getOrderId());
    }

    @Override
    public void onPaymentFailed(Payment payment) {
        System.out.println("[📧 Notification] Payment failed for order " +
                payment.getOrderId() + ". Please retry.");
    }

    @Override
    public void onRefundSuccess(Payment payment, BigDecimal refundAmount) {
        System.out.println("[📧 Notification] Refund of " + refundAmount +
                " " + payment.getCurrency() + " processed for order " + payment.getOrderId());
    }

    @Override
    public void onRefundFailed(Payment payment) {
        System.out.println("[📧 Notification] Refund failed for order " +
                payment.getOrderId() + ". Support has been notified.");
    }
}

