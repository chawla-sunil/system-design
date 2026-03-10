package org.systemdesign.elevator.strategy;

import org.systemdesign.elevator.model.Elevator;
import org.systemdesign.elevator.model.ExternalRequest;
import org.systemdesign.elevator.model.enums.Direction;
import org.systemdesign.elevator.model.enums.ElevatorState;

import java.util.List;

/**
 * LOOK-based selection strategy.
 * <p>
 * Priority order:
 * 1. Elevator already moving TOWARD the request floor in the SAME direction → best
 * 2. IDLE elevator nearest to the request floor → second best
 * 3. Elevator moving in opposite direction (will eventually reverse) → last resort
 * <p>
 * This mimics the LOOK disk scheduling algorithm applied to elevators.
 */
public class LookSelectionStrategy implements ElevatorSelectionStrategy {

    @Override
    public Elevator selectElevator(List<Elevator> elevators, ExternalRequest request) {
        int requestFloor = request.getSourceFloor();
        Direction requestDir = request.getDirection();

        Elevator bestSameDir = null;
        int bestSameDirDist = Integer.MAX_VALUE;

        Elevator bestIdle = null;
        int bestIdleDist = Integer.MAX_VALUE;

        Elevator bestOther = null;
        int bestOtherDist = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (!elevator.isAvailable()) continue; // skip maintenance

            int distance = Math.abs(elevator.getCurrentFloor() - requestFloor);

            if (elevator.isIdle()) {
                // Bucket 2: idle elevators
                if (distance < bestIdleDist) {
                    bestIdleDist = distance;
                    bestIdle = elevator;
                }
            } else if (isSameDirectionAndApproaching(elevator, requestFloor, requestDir)) {
                // Bucket 1: same direction AND hasn't passed the floor yet
                if (distance < bestSameDirDist) {
                    bestSameDirDist = distance;
                    bestSameDir = elevator;
                }
            } else {
                // Bucket 3: everything else (opposite direction, or same dir but already passed)
                if (distance < bestOtherDist) {
                    bestOtherDist = distance;
                    bestOther = elevator;
                }
            }
        }

        // Return best available in priority order
        if (bestSameDir != null) return bestSameDir;
        if (bestIdle != null) return bestIdle;
        return bestOther; // may be null if all in maintenance
    }

    private boolean isSameDirectionAndApproaching(Elevator elevator, int floor, Direction direction) {
        ElevatorState state = elevator.getState();
        int currentFloor = elevator.getCurrentFloor();

        if (direction == Direction.UP && state == ElevatorState.MOVING_UP) {
            return currentFloor <= floor; // hasn't passed the floor yet
        }
        if (direction == Direction.DOWN && state == ElevatorState.MOVING_DOWN) {
            return currentFloor >= floor; // hasn't passed the floor yet
        }
        return false;
    }
}

