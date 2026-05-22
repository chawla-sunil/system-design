package org.systemdesign.pg.processor;

import org.systemdesign.pg.model.Payment;

/**
 * STRATEGY PATTERN — Interface for payment processing.
 *
 * Each payment provider (Stripe, PayPal, RazorPay) implements this interface.
 * This allows us to swap providers at runtime without changing the core logic.
 *
 * In a real system, each implementation would call the respective provider's API.
 */
public interface PaymentProcessor {

    /**
     * Process a payment. Returns true if successful, false otherwise.
     */
    boolean processPayment(Payment payment);

    /**
     * Process a refund for a payment. Returns true if successful.
     */
    boolean processRefund(Payment payment, java.math.BigDecimal refundAmount);
}

