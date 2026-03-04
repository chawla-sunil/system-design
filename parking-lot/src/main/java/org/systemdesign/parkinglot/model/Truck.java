package org.systemdesign.parkinglot.model;

import org.systemdesign.parkinglot.model.enums.VehicleType;

public class Truck extends Vehicle {
    public Truck(String licensePlate) {
        super(licensePlate, VehicleType.TRUCK);
    }
}

