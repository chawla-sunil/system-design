package org.systemdesign.pg.model;

import org.systemdesign.pg.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core Payment entity representing a single payment transaction.
 * Uses BigDecimal for monetary precision (never use double/float for money).
 */
public class Payment {

    private final String paymentId;
    private final String orderId;
    private final String userId;
    private final String merchantId;
    private final BigDecimal amount;
    private BigDecimal refundedAmount;
    private final Currency currency;
    private final PaymentMethod paymentMethod;
    private final PaymentProvider paymentProvider;
    private PaymentStatus status;
    private final String idempotencyKey;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Payment(String orderId, String userId, String merchantId,
                   BigDecimal amount, Currency currency,
                   PaymentMethod paymentMethod, PaymentProvider paymentProvider,
                   String idempotencyKey) {
        this.paymentId = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.userId = userId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.refundedAmount = BigDecimal.ZERO;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.paymentProvider = paymentProvider;
        this.status = PaymentStatus.INITIATED;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // --- State Transitions (State Pattern logic) ---
    public void markProcessing() {
        if (this.status != PaymentStatus.INITIATED) {
            throw new IllegalStateException("Cannot move to PROCESSING from " + this.status);
        }
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markSuccess() {
        if (this.status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot move to SUCCESS from " + this.status);
        }
        this.status = PaymentStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed() {
        if (this.status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot move to FAILED from " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markRefunded(BigDecimal refundAmount) {
        if (this.status != PaymentStatus.SUCCESS && this.status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Cannot refund from status " + this.status);
        }
        this.refundedAmount = this.refundedAmount.add(refundAmount);
        if (this.refundedAmount.compareTo(this.amount) >= 0) {
            this.status = PaymentStatus.REFUNDED;
        } else {
            this.status = PaymentStatus.PARTIALLY_REFUNDED;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getRefundableAmount() {
        return this.amount.subtract(this.refundedAmount);
    }

    // --- Getters ---
    public String getPaymentId() { return paymentId; }
    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getMerchantId() { return merchantId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public Currency getCurrency() { return currency; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentProvider getPaymentProvider() { return paymentProvider; }
    public PaymentStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Payment{" +
                "paymentId='" + paymentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", amount=" + amount +
                ", currency=" + currency +
                ", method=" + paymentMethod +
                ", provider=" + paymentProvider +
                ", status=" + status +
                ", refundedAmount=" + refundedAmount +
                '}';
    }
}

