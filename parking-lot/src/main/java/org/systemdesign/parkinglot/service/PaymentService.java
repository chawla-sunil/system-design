package org.systemdesign.parkinglot.service;

import org.systemdesign.parkinglot.billing.BillingStrategy;
import org.systemdesign.parkinglot.exception.PaymentFailedException;
import org.systemdesign.parkinglot.model.Payment;
import org.systemdesign.parkinglot.model.Ticket;

/**
 * Handles fare calculation and payment processing.
 *
 * Interview note: PaymentService depends on BillingStrategy (injected),
 * not on a concrete strategy — Dependency Inversion Principle.
 * You can swap HourlyBilling ↔ FlatRateBilling without touching this class.
 */
public class PaymentService {

    private final BillingStrategy billingStrategy;

    public PaymentService(BillingStrategy billingStrategy) {
        this.billingStrategy = billingStrategy;
    }

    /**
     * Calculates the fare for a closed ticket and creates a Payment object.
     * The ticket's exitTime must be set before calling this.
     */
    public Payment createPayment(Ticket ticket) {
        double fare = billingStrategy.calculateFare(ticket);
        return new Payment(ticket, fare);
    }

    /**
     * Processes the payment.
     * In a real system this would call a payment gateway.
     * Here we simulate: amount > 0 → PAID, else → FAILED.
     *
     * Interview note: If payment fails, the spot stays OCCUPIED —
     * the vehicle cannot exit until payment succeeds.
     */
    public void processPayment(Payment payment) {
        if (payment.getAmount() <= 0) {
            payment.markFailed();
            throw new PaymentFailedException(
                    "Payment failed for ticket: " + payment.getTicket().getTicketId());
        }
        // Simulate gateway call
        payment.markPaid();
        System.out.printf("  [PaymentService] %s%n", payment);
    }
}

