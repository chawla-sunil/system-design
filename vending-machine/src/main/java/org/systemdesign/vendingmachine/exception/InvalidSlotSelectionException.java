package org.systemdesign.vendingmachine.exception;

public class InvalidSlotSelectionException extends RuntimeException {
    public InvalidSlotSelectionException(String message) {
        super(message);
    }
}

