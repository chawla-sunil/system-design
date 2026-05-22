package org.systemdesign.pg.observer;

import org.systemdesign.pg.model.Payment;

/**
 * OBSERVER PATTERN — Listener interface for payment events.
 *
 * Any component that wants to react to payment events implements this.
 * Examples: Ledger logging, email notifications, analytics, webhook dispatching.
 */
public interface PaymentEventListener {

    void onPaymentInitiated(Payment payment);
    void onPaymentSuccess(Payment payment);
    void onPaymentFailed(Payment payment);
    void onRefundSuccess(Payment payment, java.math.BigDecimal refundAmount);
    void onRefundFailed(Payment payment);
}

