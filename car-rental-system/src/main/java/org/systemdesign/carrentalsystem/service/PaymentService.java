package org.systemdesign.carrentalsystem.service;

import org.systemdesign.carrentalsystem.enums.PaymentMode;
import org.systemdesign.carrentalsystem.enums.PaymentStatus;
import org.systemdesign.carrentalsystem.exception.InvalidPaymentException;
import org.systemdesign.carrentalsystem.model.Invoice;
import org.systemdesign.carrentalsystem.model.Payment;
import org.systemdesign.carrentalsystem.strategy.PaymentStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * PaymentService uses the Strategy pattern.
 * The caller passes in a PaymentStrategy (CreditCard, UPI, Cash etc.)
 * and this service delegates the actual payment processing to the strategy.
 */
public class PaymentService {

    private final List<Payment> payments;

    public PaymentService() {
        this.payments = new ArrayList<>();
    }

    /**
     * Process payment for an invoice using the given payment strategy.
     */
    public Payment processPayment(Invoice invoice, PaymentMode paymentMode,
                                   PaymentStrategy paymentStrategy) {
        double amount = invoice.getTotalAmount();

        if (amount <= 0) {
            // No payment needed (e.g., free cancellation)
            Payment payment = new Payment(invoice, 0.0, paymentMode);
            payment.setPaymentStatus(PaymentStatus.COMPLETED);
            payments.add(payment);
            return payment;
        }

        Payment payment = new Payment(invoice, amount, paymentMode);

        boolean success = paymentStrategy.pay(amount);

        if (success) {
            payment.setPaymentStatus(PaymentStatus.COMPLETED);
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            throw new InvalidPaymentException("Payment failed for invoice: " + invoice.getId());
        }

        payments.add(payment);
        return payment;
    }

    /**
     * Process a refund using the given payment strategy.
     */
    public Payment processRefund(Invoice invoice, PaymentMode paymentMode,
                                  PaymentStrategy paymentStrategy) {
        double amount = invoice.getTotalAmount();

        Payment refundPayment = new Payment(invoice, amount, paymentMode);

        boolean success = paymentStrategy.refund(amount);

        if (success) {
            refundPayment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            refundPayment.setPaymentStatus(PaymentStatus.FAILED);
            throw new InvalidPaymentException("Refund failed for invoice: " + invoice.getId());
        }

        payments.add(refundPayment);
        return refundPayment;
    }

    public List<Payment> getAllPayments() {
        return new ArrayList<>(payments);
    }
}

