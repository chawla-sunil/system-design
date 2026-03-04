package org.systemdesign.parkinglot.exception;

/**
 * Thrown when a payment transaction fails.
 */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) {
        super(message);
    }
}

