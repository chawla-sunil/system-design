package org.systemdesign.carrentalsystem.model;

import org.systemdesign.carrentalsystem.enums.PaymentMode;
import org.systemdesign.carrentalsystem.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class Payment {
    private final String id;
    private final Invoice invoice;
    private final double amount;
    private final PaymentMode paymentMode;
    private PaymentStatus paymentStatus;
    private final LocalDateTime transactionTime;

    public Payment(Invoice invoice, double amount, PaymentMode paymentMode) {
        this.id = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        this.invoice = invoice;
        this.amount = amount;
        this.paymentMode = paymentMode;
        this.paymentStatus = PaymentStatus.PENDING;
        this.transactionTime = LocalDateTime.now();
    }

    public String getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public double getAmount() { return amount; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public LocalDateTime getTransactionTime() { return transactionTime; }

    @Override
    public String toString() {
        return "Payment{id='" + id + "', amount=₹" + amount +
                ", mode=" + paymentMode + ", status=" + paymentStatus + "}";
    }
}

