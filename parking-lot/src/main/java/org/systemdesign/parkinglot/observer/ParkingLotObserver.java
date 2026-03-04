package org.systemdesign.parkinglot.observer;

import org.systemdesign.parkinglot.model.ParkingSpot;

/**
 * Observer interface for parking spot status changes.
 *
 * Interview note: This is the Observer pattern.
 * Any class that needs to react to spot changes (DisplayBoard, SMS alerter, logger)
 * simply implements this interface and registers on a ParkingSpot.
 */
public interface ParkingLotObserver {
    void onSpotStatusChanged(ParkingSpot spot);
}

