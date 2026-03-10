package org.systemdesign.elevator.exception;

public class AllElevatorsUnavailableException extends RuntimeException {
    public AllElevatorsUnavailableException(String message) {
        super(message);
    }
}

