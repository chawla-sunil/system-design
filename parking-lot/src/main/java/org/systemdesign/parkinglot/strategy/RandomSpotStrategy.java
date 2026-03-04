package org.systemdesign.parkinglot.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.systemdesign.parkinglot.model.ParkingFloor;
import org.systemdesign.parkinglot.model.ParkingLot;
import org.systemdesign.parkinglot.model.ParkingSpot;
import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Random Spot Strategy: picks a random available spot from ALL floors.
 * Useful for load testing or distributing wear across a multi-floor lot.
 *
 * Interview note: Showing an alternative Strategy implementation proves you
 * understand the pattern — not just its definition.
 */
public class RandomSpotStrategy implements ParkingStrategy {

    private final Random random = new Random();

    @Override
    public ParkingSpot findSpot(ParkingLot parkingLot, VehicleType vehicleType) {
        List<ParkingSpot> allAvailable = new ArrayList<>();
        for (ParkingFloor floor : parkingLot.getFloors()) {
            allAvailable.addAll(floor.getAllAvailableSpots(vehicleType));
        }
        if (allAvailable.isEmpty()) {
            return null;
        }
        return allAvailable.get(random.nextInt(allAvailable.size()));
    }
}

