package org.systemdesign.elevator.model;

import org.systemdesign.elevator.model.enums.DoorState;

/**
 * Represents the physical door of an elevator.
 * Handles state transitions: CLOSED → OPENING → OPEN, OPEN → CLOSING → CLOSED.
 */
public class Door {

    private DoorState state;

    public Door() {
        this.state = DoorState.CLOSED;
    }

    public synchronized void open() {
        if (state == DoorState.OPEN || state == DoorState.OPENING) return;
        state = DoorState.OPENING;
        simulateDelay(500);
        state = DoorState.OPEN;
    }

    public synchronized void close() {
        if (state == DoorState.CLOSED || state == DoorState.CLOSING) return;
        state = DoorState.CLOSING;
        simulateDelay(500);
        state = DoorState.CLOSED;
    }

    public DoorState getState() {
        return state;
    }

    public boolean isOpen() {
        return state == DoorState.OPEN;
    }

    private void simulateDelay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return "Door{state=" + state + "}";
    }
}

