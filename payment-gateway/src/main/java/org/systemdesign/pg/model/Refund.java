package org.systemdesign.pg.model;

import org.systemdesign.pg.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refund entity — tracks a refund against a payment.
 */
public class Refund {

    private final String refundId;
    private final String paymentId;
    private final BigDecimal amount;
    private final String reason;
    private RefundStatus status;
    private final LocalDateTime createdAt;

    public Refund(String paymentId, BigDecimal amount, String reason) {
        this.refundId = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
        this.status = RefundStatus.INITIATED;
        this.createdAt = LocalDateTime.now();
    }

    public void markSuccess() { this.status = RefundStatus.SUCCESS; }
    public void markFailed() { this.status = RefundStatus.FAILED; }

    public String getRefundId() { return refundId; }
    public String getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "Refund{refundId='" + refundId + "', paymentId='" + paymentId +
                "', amount=" + amount + ", reason='" + reason +
                "', status=" + status + '}';
    }
}

