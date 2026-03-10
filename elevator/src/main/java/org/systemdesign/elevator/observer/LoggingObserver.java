package org.systemdesign.elevator.observer;

import org.systemdesign.elevator.model.Elevator;

/**
 * Logs elevator events to the console.
 *
 * Interview note: Separating logging from ElevatorService follows SRP.
 * The service runs the LOOK algorithm; the logger observes and logs.
 * Tomorrow you can swap this for a file logger, metrics collector,
 * or Kafka publisher — zero changes to ElevatorService.
 */
public class LoggingObserver implements ElevatorObserver {

    @Override
    public void onFloorChanged(Elevator elevator) {
        // Display already handles floor-change rendering via its own observer callback
    }

    @Override
    public void onStateChanged(Elevator elevator) {
        System.out.printf("  ⚡ Elevator %d state → %s%n",
                elevator.getElevatorId(), elevator.getState());
    }

    @Override
    public void onDoorOpened(Elevator elevator) {
        System.out.printf("  🚪 Elevator %d doors OPENED at floor %d%n",
                elevator.getElevatorId(), elevator.getCurrentFloor());
    }

    @Override
    public void onDoorClosed(Elevator elevator) {
        System.out.printf("  🚪 Elevator %d doors CLOSED at floor %d%n",
                elevator.getElevatorId(), elevator.getCurrentFloor());
    }
}

