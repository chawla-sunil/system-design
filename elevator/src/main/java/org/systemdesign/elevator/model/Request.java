package org.systemdesign.elevator.model;

import org.systemdesign.elevator.model.enums.Direction;
import org.systemdesign.elevator.model.enums.RequestType;

import java.time.LocalDateTime;

/**
 * Represents a request to use an elevator.
 * Can be an external request (floor button) or internal request (inside elevator).
 */
public class Request {

    private final int sourceFloor;
    private final int destinationFloor;
    private final Direction direction;
    private final RequestType type;
    private final LocalDateTime timestamp;

    public Request(int sourceFloor, int destinationFloor, Direction direction, RequestType type) {
        this.sourceFloor = sourceFloor;
        this.destinationFloor = destinationFloor;
        this.direction = direction;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    public int getSourceFloor() {
        return sourceFloor;
    }

    public int getDestinationFloor() {
        return destinationFloor;
    }

    public Direction getDirection() {
        return direction;
    }

    public RequestType getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Request{src=" + sourceFloor + ", dest=" + destinationFloor +
                ", dir=" + direction + ", type=" + type + "}";
    }
}

