package org.systemdesign.parkinglot.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.systemdesign.parkinglot.model.enums.ParkingSpotStatus;
import org.systemdesign.parkinglot.model.enums.SpotType;
import org.systemdesign.parkinglot.model.enums.VehicleType;
import org.systemdesign.parkinglot.observer.ParkingLotObserver;

/**
 * Represents a single physical parking spot.
 *
 * Interview note: ReentrantLock per spot (instead of 'synchronized' on 'this')
 * gives finer-grained concurrency — two threads can park on different spots simultaneously.
 */
public class ParkingSpot {

    private final String spotId;
    private final SpotType spotType;
    private final int floorNumber;

    private ParkingSpotStatus status;
    private Vehicle currentVehicle;

    // Fine-grained lock — one per spot for high-throughput concurrent access
    private final ReentrantLock lock = new ReentrantLock();

    // Observers notified on status changes (e.g. DisplayBoard)
    private final List<ParkingLotObserver> observers = new ArrayList<>();

    public ParkingSpot(String spotId, SpotType spotType, int floorNumber) {
        this.spotId = spotId;
        this.spotType = spotType;
        this.floorNumber = floorNumber;
        this.status = ParkingSpotStatus.AVAILABLE;
    }

    // ──────────────────────────────────────────────
    //  Core operations (thread-safe)
    // ──────────────────────────────────────────────

    /**
     * Assigns a vehicle to this spot.
     * Returns true if assignment succeeded, false if the spot was already taken.
     */
    public boolean assignVehicle(Vehicle vehicle) {
        lock.lock();
        try {
            if (status != ParkingSpotStatus.AVAILABLE) {
                return false;
            }
            if (!spotType.canFit(vehicle.getVehicleType())) {
                throw new IllegalArgumentException(
                        vehicle.getVehicleType() + " cannot fit in a " + spotType + " spot");
            }
            this.currentVehicle = vehicle;
            this.status = ParkingSpotStatus.OCCUPIED;
            notifyObservers();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes the currently parked vehicle from this spot.
     */
    public void removeVehicle() {
        lock.lock();
        try {
            if (status != ParkingSpotStatus.OCCUPIED) {
                throw new IllegalStateException("Spot " + spotId + " is not currently occupied");
            }
            this.currentVehicle = null;
            this.status = ParkingSpotStatus.AVAILABLE;
            notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    // ──────────────────────────────────────────────
    //  Observer support
    // ──────────────────────────────────────────────

    public void addObserver(ParkingLotObserver observer) {
        observers.add(observer);
    }

    private void notifyObservers() {
        observers.forEach(o -> o.onSpotStatusChanged(this));
    }

    // ──────────────────────────────────────────────
    //  Getters
    // ──────────────────────────────────────────────

    public String getSpotId()           { return spotId; }
    public SpotType getSpotType()       { return spotType; }
    public int getFloorNumber()         { return floorNumber; }
    public ParkingSpotStatus getStatus(){ return status; }
    public Vehicle getCurrentVehicle()  { return currentVehicle; }

    public boolean isAvailable() {
        return status == ParkingSpotStatus.AVAILABLE;
    }

    public boolean canFit(VehicleType vehicleType) {
        return isAvailable() && spotType.canFit(vehicleType);
    }

    @Override
    public String toString() {
        return String.format("Spot[%s | Floor:%d | Type:%s | Status:%s]",
                spotId, floorNumber, spotType, status);
    }
}

