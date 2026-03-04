package org.systemdesign.parkinglot.billing;

import org.systemdesign.parkinglot.model.Ticket;

/**
 * Strategy interface for calculating parking fees.
 *
 * Interview note: Billing is a prime candidate for the Strategy pattern because
 * different lots (airport vs mall vs hospital) charge differently.
 * You can inject the right strategy without changing the PaymentService.
 */
public interface BillingStrategy {
    /**
     * Calculates the parking fee for the given ticket.
     * Ticket must have exitTime set before calling this.
     */
    double calculateFare(Ticket ticket);
}

