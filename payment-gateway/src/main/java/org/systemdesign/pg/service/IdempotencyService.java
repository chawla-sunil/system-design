package org.systemdesign.pg.service;

import org.systemdesign.pg.enums.PaymentStatus;
import org.systemdesign.pg.model.Payment;
import org.systemdesign.pg.model.PaymentResponse;
import org.systemdesign.pg.repository.PaymentRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency Service — prevents duplicate payment processing.
 *
 * WHY is this critical?
 *  - Network timeouts can cause clients to retry
 *  - Double-charging a customer is unacceptable
 *  - The idempotency key (passed by client) ensures same request = same result
 *
 * HOW it works:
 *  - Store idempotency keys of successful/in-progress payments
 *  - If the same key comes again, return the existing payment response
 *  - Keys are scoped to (orderId + idempotencyKey) to be unique
 */
public class IdempotencyService {

    // Set of processed idempotency keys
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();
    private final PaymentRepository paymentRepository;

    public IdempotencyService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Check if this request has already been processed.
     * Returns the existing PaymentResponse if duplicate, empty otherwise.
     */
    public Optional<PaymentResponse> checkDuplicate(String orderId, String idempotencyKey) {
        String compositeKey = orderId + ":" + idempotencyKey;

        if (processedKeys.contains(compositeKey)) {
            // Find the existing payment for this order
            List<Payment> payments = paymentRepository.findByOrderId(orderId);
            Optional<Payment> existingPayment = payments.stream()
                    .filter(p -> p.getIdempotencyKey().equals(idempotencyKey))
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS ||
                            p.getStatus() == PaymentStatus.PROCESSING)
                    .findFirst();

            if (existingPayment.isPresent()) {
                Payment payment = existingPayment.get();
                return Optional.of(new PaymentResponse(
                        payment.getPaymentId(),
                        payment.getStatus(),
                        "Duplicate request — returning existing payment"
                ));
            }
        }
        return Optional.empty();
    }

    /**
     * Mark an idempotency key as processed.
     */
    public void markProcessed(String orderId, String idempotencyKey) {
        String compositeKey = orderId + ":" + idempotencyKey;
        processedKeys.add(compositeKey);
    }
}

