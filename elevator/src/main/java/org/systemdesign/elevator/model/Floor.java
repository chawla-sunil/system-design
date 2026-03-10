package org.systemdesign.elevator.model;

import org.systemdesign.elevator.model.enums.Direction;

/**
 * Represents a floor in the building.
 * Each floor has an UP button and a DOWN button for external requests.
 */
public class Floor {

    private final int floorNumber;
    private boolean upButtonPressed;
    private boolean downButtonPressed;

    public Floor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.upButtonPressed = false;
        this.downButtonPressed = false;
    }

    public synchronized void pressButton(Direction direction) {
        if (direction == Direction.UP) {
            upButtonPressed = true;
        } else if (direction == Direction.DOWN) {
            downButtonPressed = true;
        }
    }

    public synchronized void resetButton(Direction direction) {
        if (direction == Direction.UP) {
            upButtonPressed = false;
        } else if (direction == Direction.DOWN) {
            downButtonPressed = false;
        }
    }

    public synchronized void resetAllButtons() {
        upButtonPressed = false;
        downButtonPressed = false;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public boolean isUpButtonPressed() {
        return upButtonPressed;
    }

    public boolean isDownButtonPressed() {
        return downButtonPressed;
    }

    @Override
    public String toString() {
        return "Floor{" + floorNumber + ", up=" + upButtonPressed + ", down=" + downButtonPressed + "}";
    }
}

