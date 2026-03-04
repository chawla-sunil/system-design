package org.systemdesign.parkinglot.strategy;

import org.systemdesign.parkinglot.model.ParkingFloor;
import org.systemdesign.parkinglot.model.ParkingLot;
import org.systemdesign.parkinglot.model.ParkingSpot;
import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Nearest Spot Strategy: scans floors in order (0 → N) and returns
 * the first available spot that fits the vehicle type.
 *
 * Interview note: "Nearest to entrance" typically means lowest floor first,
 * then lowest spot ID. This is the default production strategy.
 * Time complexity: O(F × S) where F = floors, S = spots per floor.
 */
public class NearestSpotStrategy implements ParkingStrategy {

    @Override
    public ParkingSpot findSpot(ParkingLot parkingLot, VehicleType vehicleType) {
        for (ParkingFloor floor : parkingLot.getFloors()) {
            ParkingSpot spot = floor.getAvailableSpot(vehicleType);
            if (spot != null) {
                return spot;
            }
        }
        return null; // No spot found — caller should throw ParkingLotFullException
    }
}

