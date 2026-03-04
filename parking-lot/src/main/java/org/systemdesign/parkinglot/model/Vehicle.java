package org.systemdesign.parkinglot.model;

import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Abstract base class for all vehicles.
 * Interview note: Using abstract class (not interface) because a Vehicle
 * always HAS a license plate and type — shared state, not just a contract.
 */
public abstract class Vehicle {

    private final String licensePlate;
    private final VehicleType vehicleType;

    protected Vehicle(String licensePlate, VehicleType vehicleType) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("License plate cannot be null or blank");
        }
        this.licensePlate = licensePlate.toUpperCase().trim();
        this.vehicleType = vehicleType;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    @Override
    public String toString() {
        return vehicleType + "[" + licensePlate + "]";
    }
}

