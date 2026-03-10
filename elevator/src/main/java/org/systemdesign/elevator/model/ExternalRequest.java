package org.systemdesign.elevator.model;

import org.systemdesign.elevator.model.enums.Direction;
import org.systemdesign.elevator.model.enums.RequestType;

/**
 * An external request — someone presses the UP/DOWN button on a floor.
 * At this point we only know the source floor and desired direction, NOT the destination.
 * The destination will be provided via an InternalRequest once the person enters the elevator.
 */
public class ExternalRequest extends Request {

    public ExternalRequest(int sourceFloor, Direction direction) {
        // destination is unknown for external requests — use sourceFloor as placeholder
        super(sourceFloor, sourceFloor, direction, RequestType.EXTERNAL);
    }

    @Override
    public String toString() {
        return "ExternalRequest{floor=" + getSourceFloor() + ", dir=" + getDirection() + "}";
    }
}

