package org.systemdesign.movieticketbooking.model;

import org.systemdesign.movieticketbooking.model.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class Payment {
    private final String id;
    private final double amount;
    private PaymentStatus status;
    private final LocalDateTime paymentTime;

    public Payment(double amount) {
        this.id = UUID.randomUUID().toString();
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.paymentTime = LocalDateTime.now();
    }

    public void markCompleted() { this.status = PaymentStatus.COMPLETED; }
    public void markFailed() { this.status = PaymentStatus.FAILED; }
    public void markRefunded() { this.status = PaymentStatus.REFUNDED; }

    public String getId() { return id; }
    public double getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getPaymentTime() { return paymentTime; }

    @Override
    public String toString() {
        return "Payment{amount=₹" + amount + ", status=" + status + "}";
    }
}

