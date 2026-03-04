package org.systemdesign.parkinglot.model;

import java.util.UUID;
import org.systemdesign.parkinglot.model.enums.PaymentStatus;

/**
 * Represents a payment transaction for a parking session.
 *
 * Interview note: Separating Payment from Ticket follows SRP.
 * A Ticket is an "entry record"; a Payment is a "financial transaction".
 * This also allows multiple payment attempts (FAILED → retry → PAID).
 */
public class Payment {

    private final String paymentId;
    private final Ticket ticket;
    private final double amount;
    private PaymentStatus status;

    public Payment(Ticket ticket, double amount) {
        this.paymentId = UUID.randomUUID().toString();
        this.ticket    = ticket;
        this.amount    = amount;
        this.status    = PaymentStatus.PENDING;
    }

    public void markPaid()   { this.status = PaymentStatus.PAID; }
    public void markFailed() { this.status = PaymentStatus.FAILED; }

    public String getPaymentId()    { return paymentId; }
    public Ticket getTicket()       { return ticket; }
    public double getAmount()       { return amount; }
    public PaymentStatus getStatus(){ return status; }

    @Override
    public String toString() {
        return String.format("Payment[%s | Ticket:%s | Amount:$%.2f | Status:%s]",
                paymentId.substring(0, 8), ticket.getTicketId().substring(0, 8), amount, status);
    }
}

