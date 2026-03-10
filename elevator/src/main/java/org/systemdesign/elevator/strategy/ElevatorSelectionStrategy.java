package org.systemdesign.elevator.strategy;

import org.systemdesign.elevator.model.Elevator;
import org.systemdesign.elevator.model.ExternalRequest;

import java.util.List;

/**
 * Strategy interface for selecting which elevator should serve an external request.
 * Different implementations provide different scheduling algorithms.
 */
public interface ElevatorSelectionStrategy {

    /**
     * Selects the best elevator to serve the given request.
     *
     * @param elevators all elevators in the building
     * @param request   the external request to serve
     * @return the selected elevator, or null if none available
     */
    Elevator selectElevator(List<Elevator> elevators, ExternalRequest request);
}

