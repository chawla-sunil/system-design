package org.systemdesign.pg.service;

import org.systemdesign.pg.model.*;
import org.systemdesign.pg.observer.*;
import org.systemdesign.pg.processor.*;
import org.systemdesign.pg.repository.*;

import java.util.Optional;

/**
 * SINGLETON — PaymentGatewayService is the main entry point.
 *
 * This is where all the pieces come together:
 *  1. Validates the request
 *  2. Checks idempotency (no duplicate payments)
 *  3. Creates Payment object (INITIATED state)
 *  4. Resolves the right processor via Factory (Strategy pattern)
 *  5. Processes payment → transitions to SUCCESS or FAILED
 *  6. Notifies observers (ledger, notifications) via Observer pattern
 *  7. Returns PaymentResponse
 *
 * Thread Safety: In production, use synchronized blocks or database-level locks
 * on the orderId to prevent race conditions.
 */
public class PaymentGatewayService {

    private static PaymentGatewayService instance;

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final IdempotencyService idempotencyService;
    private final RefundService refundService;
    private final PaymentEventManager eventManager;
    private final LedgerEventListener ledgerListener;

    private PaymentGatewayService() {
        this.paymentRepository = new PaymentRepository();
        this.refundRepository = new RefundRepository();
        this.idempotencyService = new IdempotencyService(paymentRepository);

        // Set up Observer pattern
        this.eventManager = new PaymentEventManager();
        this.ledgerListener = new LedgerEventListener();
        this.eventManager.subscribe(ledgerListener);
        this.eventManager.subscribe(new NotificationEventListener());

        this.refundService = new RefundService(paymentRepository, refundRepository, eventManager);
    }

    public static synchronized PaymentGatewayService getInstance() {
        if (instance == null) {
            instance = new PaymentGatewayService();
        }
        return instance;
    }

    /**
     * Main payment processing flow.
     */
    public PaymentResponse processPayment(PaymentRequest request) {
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("💳 Processing Payment Request");
        System.out.println("══════════════════════════════════════════");
        System.out.println("Request: " + request);

        // Step 1: Idempotency check — prevent duplicate charges
        Optional<PaymentResponse> duplicate = idempotencyService.checkDuplicate(
                request.getOrderId(), request.getIdempotencyKey());
        if (duplicate.isPresent()) {
            System.out.println("⚠️  Duplicate payment detected! Returning existing response.");
            return duplicate.get();
        }

        // Step 2: Create Payment entity (status = INITIATED)
        Payment payment = new Payment(
                request.getOrderId(),
                request.getUserId(),
                request.getMerchantId(),
                request.getAmount(),
                request.getCurrency(),
                request.getPaymentMethod(),
                request.getPaymentProvider(),
                request.getIdempotencyKey()
        );
        paymentRepository.save(payment);
        eventManager.notifyPaymentInitiated(payment);

        // Step 3: Get the right processor (Strategy via Factory)
        PaymentProcessor processor = PaymentProcessorFactory.getProcessor(request.getPaymentProvider());

        // Step 4: Transition to PROCESSING
        payment.markProcessing();

        // Step 5: Process payment via the chosen provider
        boolean success = processor.processPayment(payment);

        // Step 6: Transition to SUCCESS or FAILED
        PaymentResponse response;
        if (success) {
            payment.markSuccess();
            idempotencyService.markProcessed(request.getOrderId(), request.getIdempotencyKey());
            eventManager.notifyPaymentSuccess(payment);
            response = new PaymentResponse(payment.getPaymentId(), payment.getStatus(),
                    "Payment processed successfully");
        } else {
            payment.markFailed();
            eventManager.notifyPaymentFailed(payment);
            response = new PaymentResponse(payment.getPaymentId(), payment.getStatus(),
                    "Payment processing failed. Please retry.");
        }

        System.out.println("Result: " + response);
        return response;
    }

    /**
     * Process a refund for an existing payment.
     */
    public Refund processRefund(String paymentId, java.math.BigDecimal refundAmount, String reason) {
        return refundService.processRefund(paymentId, refundAmount, reason);
    }

    /**
     * Get payment details by ID.
     */
    public Payment getPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }

    /**
     * Get all payments for an order (including retries).
     */
    public java.util.List<Payment> getPaymentsByOrder(String orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /**
     * Get the full audit trail.
     */
    public java.util.List<LedgerEntry> getFullLedger() {
        return ledgerListener.getLedger();
    }

    /**
     * Get audit trail for a specific payment.
     */
    public java.util.List<LedgerEntry> getLedgerForPayment(String paymentId) {
        return ledgerListener.getLedgerForPayment(paymentId);
    }
}

