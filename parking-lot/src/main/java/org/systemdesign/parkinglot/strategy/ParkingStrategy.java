package org.systemdesign.parkinglot.strategy;

import org.systemdesign.parkinglot.model.ParkingLot;
import org.systemdesign.parkinglot.model.ParkingSpot;
import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Strategy interface for selecting a parking spot.
 *
 * Interview note: Strategy pattern lets us swap algorithms at runtime.
 * e.g. NearestSpot for weekdays, RandomSpot for stress testing,
 * PremiumSpot for VIP customers — all without touching ParkingService.
 */
public interface ParkingStrategy {
    /**
     * Selects the best available spot for the given vehicle type.
     * Returns null if no spot is available.
     */
    ParkingSpot findSpot(ParkingLot parkingLot, VehicleType vehicleType);
}

