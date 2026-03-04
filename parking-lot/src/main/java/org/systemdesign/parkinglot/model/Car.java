package org.systemdesign.parkinglot.model;

import org.systemdesign.parkinglot.model.enums.VehicleType;

public class Car extends Vehicle {
    public Car(String licensePlate) {
        super(licensePlate, VehicleType.CAR);
    }
}

