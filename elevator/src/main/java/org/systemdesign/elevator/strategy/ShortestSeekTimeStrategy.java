package org.systemdesign.elevator.strategy;

import org.systemdesign.elevator.model.Elevator;
import org.systemdesign.elevator.model.ExternalRequest;

import java.util.List;

/**
 * Shortest Seek Time First (SSTF) strategy.
 * <p>
 * Simply picks the nearest available elevator to the request floor.
 * Among elevators at the same distance, prefers IDLE over moving, then lower ID.
 * <p>
 * Pros: Minimizes wait time for the individual request.
 * Cons: Can cause starvation for distant floors (same problem as SSTF in disk scheduling).
 */
public class ShortestSeekTimeStrategy implements ElevatorSelectionStrategy {

    @Override
    public Elevator selectElevator(List<Elevator> elevators, ExternalRequest request) {
        int requestFloor = request.getSourceFloor();

        Elevator best = null;
        int bestScore = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (!elevator.isAvailable()) continue;

            int distance = Math.abs(elevator.getCurrentFloor() - requestFloor);

            // Score: distance * 10 + (isIdle ? 0 : 5) + elevatorId (for tie-breaking)
            // Lower score = better
            int score = distance * 10 + (elevator.isIdle() ? 0 : 5) + elevator.getElevatorId();

            if (score < bestScore) {
                bestScore = score;
                best = elevator;
            }
        }

        return best; // null if all in maintenance
    }
}

