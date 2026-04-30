package org.systemdesign.movieticketbooking.exception;

public class SeatTemporarilyLockedException extends RuntimeException {
    public SeatTemporarilyLockedException(String message) {
        super(message);
    }
}

