package org.systemdesign.carrentalsystem.strategy;

/**
 * Strategy Pattern: PaymentStrategy interface.
 * Different payment modes implement this interface.
 * This makes it easy to add new payment methods without modifying existing code (Open/Closed Principle).
 */
public interface PaymentStrategy {

    /**
     * Process a payment of the given amount.
     * @param amount the amount to charge
     * @return true if payment was successful, false otherwise
     */
    boolean pay(double amount);

    /**
     * Refund a payment of the given amount.
     * @param amount the amount to refund
     * @return true if refund was successful, false otherwise
     */
    boolean refund(double amount);
}

