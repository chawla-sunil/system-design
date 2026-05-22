package org.systemdesign.pg.model;

import org.systemdesign.pg.enums.PaymentStatus;

/**
 * Response returned after processing a payment.
 */
public class PaymentResponse {

    private final String paymentId;
    private final PaymentStatus status;
    private final String message;

    public PaymentResponse(String paymentId, PaymentStatus status, String message) {
        this.paymentId = paymentId;
        this.status = status;
        this.message = message;
    }

    public String getPaymentId() { return paymentId; }
    public PaymentStatus getStatus() { return status; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "PaymentResponse{paymentId='" + paymentId + "', status=" + status +
                ", message='" + message + "'}";
    }
}

