package org.systemdesign.movieticketbooking.service;

import org.systemdesign.movieticketbooking.model.Payment;
import org.systemdesign.movieticketbooking.model.enums.PaymentStatus;

/**
 * Simulates payment processing.
 * In production, this would integrate with a payment gateway (Razorpay, Stripe, etc.)
 */
public class PaymentService {

    /**
     * Process payment. Simulates success/failure.
     * In production: calls payment gateway API.
     */
    public Payment processPayment(String userId, double amount) {
        Payment payment = new Payment(amount);

        // Simulate: payments always succeed in this LLD demo
        // In production: call external payment gateway here
        payment.markCompleted();

        System.out.println("  💳 Payment processed: ₹" + amount + " for user: " + userId +
                " | Status: " + payment.getStatus());
        return payment;
    }

    /**
     * Refund a completed payment.
     */
    public void refundPayment(Payment payment) {
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            payment.markRefunded();
            System.out.println("  💰 Refund processed: ₹" + payment.getAmount() +
                    " | Status: " + payment.getStatus());
        }
    }
}

