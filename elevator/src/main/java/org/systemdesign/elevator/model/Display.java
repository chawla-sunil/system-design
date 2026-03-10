package org.systemdesign.elevator.model;

import org.systemdesign.elevator.model.enums.Direction;
import org.systemdesign.elevator.observer.ElevatorObserver;

/**
 * Observer that displays the current floor and direction of an elevator.
 * Registered as an ElevatorObserver on the Elevator it belongs to.
 *
 * Interview note: Display implements ElevatorObserver so the Elevator doesn't
 * need to know about Display specifically — it just notifies all observers.
 * Tomorrow you can add an LED panel or a mobile push notification
 * by implementing ElevatorObserver — zero changes to Elevator.
 */
public class Display implements ElevatorObserver {

    private int currentFloor;
    private Direction direction;
    private final int elevatorId;

    public Display(int elevatorId) {
        this.elevatorId = elevatorId;
        this.currentFloor = 0;
        this.direction = Direction.IDLE;
    }

    public void show() {
        String arrow = switch (direction) {
            case UP -> "▲";
            case DOWN -> "▼";
            case IDLE -> "●";
        };
        System.out.printf("  [Display E%d] Floor: %d %s %s%n", elevatorId, currentFloor, arrow, direction);
    }

    // ──────────────────────────────────────────────
    //  ElevatorObserver callbacks
    // ──────────────────────────────────────────────

    @Override
    public void onFloorChanged(Elevator elevator) {
        this.currentFloor = elevator.getCurrentFloor();
        this.direction = elevator.getCurrentDirection();
        show();
    }

    @Override
    public void onStateChanged(Elevator elevator) {
        this.direction = elevator.getCurrentDirection();
    }

    @Override
    public void onDoorOpened(Elevator elevator) {
        // Display doesn't react to door events — but interface requires it
    }

    @Override
    public void onDoorClosed(Elevator elevator) {
        // Display doesn't react to door events
    }

    // ──────────────────────────────────────────────
    //  Getters
    // ──────────────────────────────────────────────

    public int getCurrentFloor() { return currentFloor; }
    public Direction getDirection() { return direction; }

    @Override
    public String toString() {
        return "Display{elevator=" + elevatorId + ", floor=" + currentFloor + ", dir=" + direction + "}";
    }
}
