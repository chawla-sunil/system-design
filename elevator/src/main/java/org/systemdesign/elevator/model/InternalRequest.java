package org.systemdesign.elevator.model;

import org.systemdesign.elevator.model.enums.Direction;
import org.systemdesign.elevator.model.enums.RequestType;

/**
 * An internal request — someone inside the elevator presses a destination floor button.
 * We know the elevator ID and the target floor.
 */
public class InternalRequest extends Request {

    private final int elevatorId;

    public InternalRequest(int elevatorId, int currentFloor, int destinationFloor) {
        super(currentFloor, destinationFloor,
                destinationFloor > currentFloor ? Direction.UP : Direction.DOWN,
                RequestType.INTERNAL);
        this.elevatorId = elevatorId;
    }

    public int getElevatorId() {
        return elevatorId;
    }

    @Override
    public String toString() {
        return "InternalRequest{elevator=" + elevatorId +
                ", dest=" + getDestinationFloor() + ", dir=" + getDirection() + "}";
    }
}

