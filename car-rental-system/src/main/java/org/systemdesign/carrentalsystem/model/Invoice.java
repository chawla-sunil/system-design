package org.systemdesign.carrentalsystem.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Invoice {
    private final String id;
    private final Reservation reservation;
    private final Bill bill;
    private final LocalDateTime generatedAt;

    public Invoice(Reservation reservation, Bill bill) {
        this.id = "INV-" + UUID.randomUUID().toString().substring(0, 8);
        this.reservation = reservation;
        this.bill = bill;
        this.generatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public Bill getBill() { return bill; }
    public double getTotalAmount() { return bill.getTotalAmount(); }
    public LocalDateTime getGeneratedAt() { return generatedAt; }

    @Override
    public String toString() {
        return "Invoice{id='" + id + "', " + bill +
                ", generatedAt=" + generatedAt + "}";
    }
}

