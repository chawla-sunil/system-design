package org.systemdesign.carrentalsystem.service;

import org.systemdesign.carrentalsystem.model.Bill;
import org.systemdesign.carrentalsystem.model.Invoice;
import org.systemdesign.carrentalsystem.model.Reservation;

import java.time.Duration;
import java.time.LocalDateTime;

public class InvoiceService {

    private static final double TAX_RATE = 0.18; // 18% GST

    /**
     * Generate an invoice for a completed reservation.
     * Calculates cost based on actual usage hours × price per hour.
     * If the car is returned late (after endTime), charges based on actual return time.
     */
    public Invoice generateInvoice(Reservation reservation) {
        LocalDateTime start = reservation.getStartTime();
        LocalDateTime returnTime = reservation.getActualReturnTime() != null
                ? reservation.getActualReturnTime()
                : reservation.getEndTime();

        // Calculate hours (round up to next hour)
        long totalMinutes = Duration.between(start, returnTime).toMinutes();
        long hours = (totalMinutes + 59) / 60; // ceil division

        double pricePerHour = reservation.getVehicle().getPricePerHour();
        double baseCost = hours * pricePerHour;
        double discount = 0.0;

        // Apply discount for long rentals (more than 24 hours)
        if (hours > 24) {
            discount = baseCost * 0.10; // 10% discount for 24+ hour rentals
        }

        Bill bill = new Bill(baseCost, TAX_RATE, discount);
        Invoice invoice = new Invoice(reservation, bill);
        reservation.setInvoice(invoice);

        return invoice;
    }

    /**
     * Generate a cancellation invoice (zero charge or cancellation fee).
     */
    public Invoice generateCancellationInvoice(Reservation reservation) {
        double cancellationFee = 0.0;

        // If cancellation is within 2 hours of start time, charge a fee
        long hoursUntilStart = Duration.between(LocalDateTime.now(), reservation.getStartTime()).toHours();
        if (hoursUntilStart < 2 && hoursUntilStart >= 0) {
            cancellationFee = reservation.getVehicle().getPricePerHour() * 2; // 2-hour cancellation fee
        }

        Bill bill = new Bill(cancellationFee, TAX_RATE, 0.0);
        Invoice invoice = new Invoice(reservation, bill);
        reservation.setInvoice(invoice);

        return invoice;
    }
}

