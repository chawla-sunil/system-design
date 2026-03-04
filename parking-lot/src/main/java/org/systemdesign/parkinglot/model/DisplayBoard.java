package org.systemdesign.parkinglot.model;

import org.systemdesign.parkinglot.observer.ParkingLotObserver;

/**
 * Displays real-time availability counts per floor.
 * Implements ParkingLotObserver — automatically updated whenever a spot changes status.
 *
 * Interview note: Observer pattern decouples ParkingSpot from the display logic.
 * Tomorrow you could add an SMS/email notifier by adding another observer — zero changes to spot code.
 */
public class DisplayBoard implements ParkingLotObserver {

    private final int floorNumber;
    private long availableSpots;

    public DisplayBoard(int floorNumber) {
        this.floorNumber = floorNumber;
    }

    /** Called by ParkingSpot whenever its status changes. */
    @Override
    public void onSpotStatusChanged(ParkingSpot spot) {
        // In a real system, re-query the floor for accurate count.
        // Here we just print; the floor calls refresh() separately.
        System.out.println("  [DisplayBoard Floor " + floorNumber + "] Spot " +
                spot.getSpotId() + " is now " + spot.getStatus());
    }

    /** Called by ParkingFloor after each assignment/removal to update the count. */
    public void refresh(long availableCount) {
        this.availableSpots = availableCount;
    }

    public void display() {
        System.out.println("  [DisplayBoard Floor " + floorNumber + "] Available spots: " + availableSpots);
    }

    public long getAvailableSpots() { return availableSpots; }
    public int getFloorNumber()     { return floorNumber; }
}

