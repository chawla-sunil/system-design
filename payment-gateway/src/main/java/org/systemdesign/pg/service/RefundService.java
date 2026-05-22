package org.systemdesign.pg.service;

import org.systemdesign.pg.enums.PaymentStatus;
import org.systemdesign.pg.model.Payment;
import org.systemdesign.pg.model.Refund;
import org.systemdesign.pg.observer.PaymentEventManager;
import org.systemdesign.pg.processor.PaymentProcessor;
import org.systemdesign.pg.processor.PaymentProcessorFactory;
import org.systemdesign.pg.repository.PaymentRepository;
import org.systemdesign.pg.repository.RefundRepository;

import java.math.BigDecimal;

/**
 * Refund Service — handles full and partial refund logic.
 *
 * Key validations:
 *  1. Payment must exist
 *  2. Payment must be in SUCCESS or PARTIALLY_REFUNDED state
 *  3. Refund amount must not exceed remaining refundable amount
 *  4. Uses the same payment provider that processed the original payment
 */
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentEventManager eventManager;

    public RefundService(PaymentRepository paymentRepository,
                         RefundRepository refundRepository,
                         PaymentEventManager eventManager) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.eventManager = eventManager;
    }

    public Refund processRefund(String paymentId, BigDecimal refundAmount, String reason) {
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("🔄 Processing Refund for Payment: " + paymentId);
        System.out.println("══════════════════════════════════════════");

        // 1. Find the payment
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        // 2. Validate payment status
        if (payment.getStatus() != PaymentStatus.SUCCESS &&
                payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException(
                    "Cannot refund payment in status: " + payment.getStatus() +
                            ". Only SUCCESS or PARTIALLY_REFUNDED payments can be refunded.");
        }

        // 3. Validate refund amount
        BigDecimal refundableAmount = payment.getRefundableAmount();
        if (refundAmount.compareTo(refundableAmount) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount " + refundAmount + " exceeds refundable amount " + refundableAmount);
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }

        // 4. Create refund record
        Refund refund = new Refund(paymentId, refundAmount, reason);

        // 5. Process refund through the same provider
        PaymentProcessor processor = PaymentProcessorFactory.getProcessor(payment.getPaymentProvider());
        boolean success = processor.processRefund(payment, refundAmount);

        if (success) {
            refund.markSuccess();
            payment.markRefunded(refundAmount);
            eventManager.notifyRefundSuccess(payment, refundAmount);
        } else {
            refund.markFailed();
            eventManager.notifyRefundFailed(payment);
        }

        // 6. Save refund record
        refundRepository.save(refund);

        System.out.println("Refund result: " + refund);
        return refund;
    }
}

