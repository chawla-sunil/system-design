package org.systemdesign.parkinglot.model;

import org.systemdesign.parkinglot.model.enums.VehicleType;

public class Bike extends Vehicle {
    public Bike(String licensePlate) {
        super(licensePlate, VehicleType.BIKE);
    }
}

