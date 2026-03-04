package org.systemdesign.parkinglot.exception;

/**
 * Thrown when no parking spot is available for the requested vehicle type.
 */
public class ParkingLotFullException extends RuntimeException {
    public ParkingLotFullException(String message) {
        super(message);
    }
}

