package org.systemdesign.parkinglot.factory;

import org.systemdesign.parkinglot.model.Bike;
import org.systemdesign.parkinglot.model.Car;
import org.systemdesign.parkinglot.model.Truck;
import org.systemdesign.parkinglot.model.Vehicle;
import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Factory for creating Vehicle instances.
 *
 * Interview note: Factory pattern encapsulates object creation.
 * If tomorrow we add an ElectricCar subclass, we only change this factory —
 * all callers remain untouched.
 */
public class VehicleFactory {

    private VehicleFactory() { /* utility class, no instances */ }

    public static Vehicle create(VehicleType type, String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("License plate must not be null or blank");
        }
        return switch (type) {
            case BIKE  -> new Bike(licensePlate);
            case CAR   -> new Car(licensePlate);
            case TRUCK -> new Truck(licensePlate);
        };
    }
}

