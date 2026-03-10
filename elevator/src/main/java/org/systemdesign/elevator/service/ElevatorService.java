package org.systemdesign.elevator.service;

import org.systemdesign.elevator.model.Elevator;
import org.systemdesign.elevator.model.enums.ElevatorState;

/**
 * Per-elevator service that runs the LOOK algorithm.
 * <p>
 * The LOOK algorithm (used in disk scheduling, applied here to elevators):
 * 1. Move in the current direction, serving stops along the way
 * 2. When no more stops in the current direction, reverse
 * 3. When no stops at all, go IDLE
 * <p>
 * Each ElevatorService runs on its own thread via Runnable.
 */
public class ElevatorService implements Runnable {

    private final Elevator elevator;
    private volatile boolean running = true;
    private static final long FLOOR_TRAVEL_TIME_MS = 1000; // 1 second per floor
    private static final long DOOR_OPEN_WAIT_MS = 2000;    // 2 seconds door stays open

    public ElevatorService(Elevator elevator) {
        this.elevator = elevator;
    }


    // ──────────────────────────────────────────────
    //  LOOK Algorithm Loop
    // ──────────────────────────────────────────────

    @Override
    public void run() {
        System.out.println("🛗 ElevatorService started for Elevator " + elevator.getElevatorId());

        while (running) {
            try {
                if (!elevator.hasStops()) {
                    // No stops — go idle and wait
                    if (elevator.getState() != ElevatorState.IDLE &&
                        elevator.getState() != ElevatorState.MAINTENANCE) {
                        elevator.setState(ElevatorState.IDLE);
                    }
                    Thread.sleep(200); // polling interval
                    continue;
                }

                int nextStop = elevator.getNextStop();
                if (nextStop == -1) {
                    Thread.sleep(200);
                    continue;
                }

                // Move floor by floor toward the next stop
                moveToFloor(nextStop);

                // Arrived — remove stop, open door, wait, close door
                elevator.removeStop(nextStop);
                System.out.printf("  🔔 Elevator %d arrived at floor %d%n",
                        elevator.getElevatorId(), elevator.getCurrentFloor());

                elevator.openDoor();
                Thread.sleep(DOOR_OPEN_WAIT_MS); // passengers enter/exit
                elevator.closeDoor();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }

        System.out.println("🛗 ElevatorService stopped for Elevator " + elevator.getElevatorId());
    }

    /**
     * Moves the elevator one floor at a time toward the target floor.
     * Notifies observers on each floor change.
     */
    private void moveToFloor(int targetFloor) throws InterruptedException {
        while (elevator.getCurrentFloor() != targetFloor && running) {
            Thread.sleep(FLOOR_TRAVEL_TIME_MS);
            elevator.moveOneFloor();
        }
    }

    /**
     * Adds a stop to this elevator.
     * Can be called from any thread (controller, button press handler).
     */
    public void addStop(int floor) {
        elevator.addStop(floor);
    }

    public void stop() {
        running = false;
    }

    public Elevator getElevator() {
        return elevator;
    }
}
