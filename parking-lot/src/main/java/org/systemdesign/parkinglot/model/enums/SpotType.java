package org.systemdesign.parkinglot.model.enums;

/**
 * SpotType defines the physical size of a parking spot.
 * Fit rules:
 *   BIKE  -> can fit in SMALL, MEDIUM, LARGE
 *   CAR   -> can fit in MEDIUM, LARGE
 *   TRUCK -> can fit in LARGE only
 */
public enum SpotType {
    SMALL,
    MEDIUM,
    LARGE,
    EV,          // Electric Vehicle charging spot (MEDIUM size with charger)
    HANDICAPPED; // Accessible spot (MEDIUM size, reserved)

    /**
     * Returns true if this spot type can physically accommodate the given vehicle type.
     */
    public boolean canFit(VehicleType vehicleType) {
        return switch (vehicleType) {
            case BIKE  -> this == SMALL || this == MEDIUM || this == LARGE;
            case CAR   -> this == MEDIUM || this == LARGE || this == EV || this == HANDICAPPED;
            case TRUCK -> this == LARGE;
        };
    }
}

