package org.systemdesign.pg.model;

import org.systemdesign.pg.enums.*;

import java.math.BigDecimal;

/**
 * PaymentRequest built using the Builder Pattern.
 * Encapsulates all data needed to initiate a payment.
 */
public class PaymentRequest {

    private final String orderId;
    private final String userId;
    private final String merchantId;
    private final BigDecimal amount;
    private final Currency currency;
    private final PaymentMethod paymentMethod;
    private final PaymentProvider paymentProvider;
    private final String idempotencyKey;

    private PaymentRequest(Builder builder) {
        this.orderId = builder.orderId;
        this.userId = builder.userId;
        this.merchantId = builder.merchantId;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.paymentMethod = builder.paymentMethod;
        this.paymentProvider = builder.paymentProvider;
        this.idempotencyKey = builder.idempotencyKey;
    }

    // --- Getters ---
    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getMerchantId() { return merchantId; }
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentProvider getPaymentProvider() { return paymentProvider; }
    public String getIdempotencyKey() { return idempotencyKey; }

    // --- Builder ---
    public static class Builder {
        private String orderId;
        private String userId;
        private String merchantId;
        private BigDecimal amount;
        private Currency currency;
        private PaymentMethod paymentMethod;
        private PaymentProvider paymentProvider;
        private String idempotencyKey;

        public Builder orderId(String orderId) { this.orderId = orderId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder merchantId(String merchantId) { this.merchantId = merchantId; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder currency(Currency currency) { this.currency = currency; return this; }
        public Builder paymentMethod(PaymentMethod method) { this.paymentMethod = method; return this; }
        public Builder paymentProvider(PaymentProvider provider) { this.paymentProvider = provider; return this; }
        public Builder idempotencyKey(String key) { this.idempotencyKey = key; return this; }

        public PaymentRequest build() {
            // Validation
            if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
            if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
            if (merchantId == null || merchantId.isBlank()) throw new IllegalArgumentException("merchantId is required");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be positive");
            if (currency == null) throw new IllegalArgumentException("currency is required");
            if (paymentMethod == null) throw new IllegalArgumentException("paymentMethod is required");
            if (paymentProvider == null) throw new IllegalArgumentException("paymentProvider is required");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
            return new PaymentRequest(this);
        }
    }

    @Override
    public String toString() {
        return "PaymentRequest{orderId='" + orderId + "', amount=" + amount +
                ", currency=" + currency + ", method=" + paymentMethod +
                ", provider=" + paymentProvider + '}';
    }
}

