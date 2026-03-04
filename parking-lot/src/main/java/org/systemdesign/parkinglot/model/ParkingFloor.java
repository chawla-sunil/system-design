package org.systemdesign.parkinglot.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Represents a single floor of the parking lot.
 *
 * Interview note: Using ConcurrentHashMap so multiple threads can safely
 * read spot availability without locking the entire floor.
 */
public class ParkingFloor {

    private final int floorNumber;
    private final Map<String, ParkingSpot> spots = new ConcurrentHashMap<>();
    private final DisplayBoard displayBoard;

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.displayBoard = new DisplayBoard(floorNumber);
    }

    /** Add a spot to this floor and register the display board as an observer. */
    public void addSpot(ParkingSpot spot) {
        spot.addObserver(displayBoard);
        spots.put(spot.getSpotId(), spot);
        displayBoard.refresh(getAvailableSpotCount());
    }

    /** Returns the first available spot that can fit the given vehicle type, or null if none. */
    public ParkingSpot getAvailableSpot(VehicleType vehicleType) {
        return spots.values().stream()
                .filter(s -> s.canFit(vehicleType))
                .findFirst()
                .orElse(null);
    }

    /** Returns ALL available spots for a vehicle type (useful for strategy implementations). */
    public List<ParkingSpot> getAllAvailableSpots(VehicleType vehicleType) {
        return spots.values().stream()
                .filter(s -> s.canFit(vehicleType))
                .collect(Collectors.toList());
    }

    public long getAvailableSpotCount() {
        return spots.values().stream().filter(ParkingSpot::isAvailable).count();
    }

    public long getAvailableSpotCount(VehicleType vehicleType) {
        return spots.values().stream().filter(s -> s.canFit(vehicleType)).count();
    }

    public int getFloorNumber()                      { return floorNumber; }
    public Map<String, ParkingSpot> getSpots()       { return Collections.unmodifiableMap(spots); }
    public DisplayBoard getDisplayBoard()             { return displayBoard; }

    @Override
    public String toString() {
        return String.format("Floor %d | Available: %d / %d spots",
                floorNumber, getAvailableSpotCount(), spots.size());
    }
}

