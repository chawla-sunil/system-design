package org.systemdesign.elevator.observer;

import org.systemdesign.elevator.model.Elevator;

/**
 * Observer interface for elevator state changes.
 * Implementations react to floor changes and state transitions.
 */
public interface ElevatorObserver {

    void onFloorChanged(Elevator elevator);

    void onStateChanged(Elevator elevator);

    void onDoorOpened(Elevator elevator);

    void onDoorClosed(Elevator elevator);
}

