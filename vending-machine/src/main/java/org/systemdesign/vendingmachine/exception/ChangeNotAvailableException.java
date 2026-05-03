package org.systemdesign.vendingmachine.exception;

public class ChangeNotAvailableException extends RuntimeException {
    public ChangeNotAvailableException(String message) {
        super(message);
    }
}

