package org.systemdesign.parkinglot.exception;

/**
 * Thrown when a ticket is not found, already used, or invalid.
 */
public class InvalidTicketException extends RuntimeException {
    public InvalidTicketException(String message) {
        super(message);
    }
}

