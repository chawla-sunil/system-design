package org.systemdesign.carrentalsystem.exception;

public class VehicleNotAvailableException extends RuntimeException {
    public VehicleNotAvailableException(String message) {
        super(message);
    }
}

