package org.systemdesign.elevator.model;

import org.systemdesign.elevator.model.enums.Direction;
import org.systemdesign.elevator.model.enums.ElevatorState;
import org.systemdesign.elevator.observer.ElevatorObserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a single elevator car.
 * <p>
 * Maintains two sorted sets of stops:
 * - upStops: floors to visit while going UP (natural ascending order)
 * - downStops: floors to visit while going DOWN (reverse descending order)
 * <p>
 * Uses ReentrantLock for thread-safe stop management since multiple threads
 * (controller, button presses) can add stops concurrently.
 */
public class Elevator {

    private final int elevatorId;
    private int currentFloor;
    private ElevatorState state;
    private final Door door;
    private final Display display;
    private int currentWeight;
    private final int maxWeight;
    private final int minFloor;
    private final int maxFloor;

    // Sorted stop queues — the LOOK algorithm processes these
    private final TreeSet<Integer> upStops;     // ascending — poll lowest first
    private final TreeSet<Integer> downStops;   // descending — poll highest first

    private final ReentrantLock lock = new ReentrantLock();
    private final List<ElevatorObserver> observers = new ArrayList<>();

    public Elevator(int elevatorId, int minFloor, int maxFloor, int maxWeight) {
        this.elevatorId = elevatorId;
        this.currentFloor = minFloor; // start at ground floor
        this.state = ElevatorState.IDLE;
        this.door = new Door();
        this.display = new Display(elevatorId);
        this.observers.add(display); // Display is registered as an observer
        this.currentWeight = 0;
        this.maxWeight = maxWeight;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.upStops = new TreeSet<>();
        this.downStops = new TreeSet<>(Collections.reverseOrder());
    }

    // ───────────── Stop Management (Thread-Safe) ─────────────

    /**
     * Adds a stop to the appropriate queue based on direction relative to current floor.
     * If the elevator is IDLE, it starts moving toward the stop.
     */
    public void addStop(int floor) {
        lock.lock();
        try {
            if (floor == currentFloor) return; // already here, skip

            if (floor > currentFloor) {
                upStops.add(floor);
            } else {
                downStops.add(floor);
            }

            // If idle, start moving toward the new stop
            if (state == ElevatorState.IDLE) {
                if (floor > currentFloor) {
                    setState(ElevatorState.MOVING_UP);
                } else {
                    setState(ElevatorState.MOVING_DOWN);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the next stop in the current direction.
     * If no stops remain in current direction, reverses.
     * If no stops at all, returns -1 (idle).
     */
    public int getNextStop() {
        lock.lock();
        try {
            if (state == ElevatorState.MOVING_UP) {
                Integer next = upStops.higher(currentFloor - 1); // next floor >= currentFloor
                if (next != null) return next;
                // No more up stops — check if we have down stops
                if (!downStops.isEmpty()) {
                    setState(ElevatorState.MOVING_DOWN);
                    return downStops.first(); // highest floor below
                }
            } else if (state == ElevatorState.MOVING_DOWN) {
                Integer next = downStops.higher(currentFloor + 1); // next floor <= currentFloor (reversed order)
                if (next != null) return next;
                // No more down stops — check if we have up stops
                if (!upStops.isEmpty()) {
                    setState(ElevatorState.MOVING_UP);
                    return upStops.first(); // lowest floor above
                }
            }
            // Truly idle
            if (!upStops.isEmpty()) {
                setState(ElevatorState.MOVING_UP);
                return upStops.first();
            }
            if (!downStops.isEmpty()) {
                setState(ElevatorState.MOVING_DOWN);
                return downStops.first();
            }
            return -1; // no stops
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a floor from the stop queues (called after arriving at that floor).
     */
    public void removeStop(int floor) {
        lock.lock();
        try {
            upStops.remove(floor);
            downStops.remove(floor);
        } finally {
            lock.unlock();
        }
    }

    public boolean hasStops() {
        lock.lock();
        try {
            return !upStops.isEmpty() || !downStops.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    // ───────────── Movement ─────────────

    /**
     * Moves one floor in the current direction. Notifies observers.
     */
    public void moveOneFloor() {
        if (state == ElevatorState.MOVING_UP && currentFloor < maxFloor) {
            currentFloor++;
        } else if (state == ElevatorState.MOVING_DOWN && currentFloor > minFloor) {
            currentFloor--;
        }
        notifyFloorChanged();
    }

    public void openDoor() {
        door.open();
        notifyDoorOpened();
    }

    public void closeDoor() {
        door.close();
        notifyDoorClosed();
    }

    // ───────────── Weight ─────────────

    public boolean addWeight(int weight) {
        if (currentWeight + weight > maxWeight) return false;
        currentWeight += weight;
        return true;
    }

    public void removeWeight(int weight) {
        currentWeight = Math.max(0, currentWeight - weight);
    }

    public boolean isOverweight() {
        return currentWeight > maxWeight;
    }

    // ───────────── State ─────────────

    public void setState(ElevatorState newState) {
        if (this.state != newState) {
            this.state = newState;
            notifyStateChanged();
        }
    }

    public void setToMaintenance() {
        lock.lock();
        try {
            upStops.clear();
            downStops.clear();
            setState(ElevatorState.MAINTENANCE);
        } finally {
            lock.unlock();
        }
    }

    public void clearMaintenance() {
        setState(ElevatorState.IDLE);
    }

    public boolean isAvailable() {
        return state != ElevatorState.MAINTENANCE;
    }

    public boolean isIdle() {
        return state == ElevatorState.IDLE;
    }

    public boolean isMovingToward(int floor) {
        if (state == ElevatorState.MOVING_UP) return floor >= currentFloor;
        if (state == ElevatorState.MOVING_DOWN) return floor <= currentFloor;
        return true; // IDLE can go anywhere
    }

    public Direction getCurrentDirection() {
        return switch (state) {
            case MOVING_UP -> Direction.UP;
            case MOVING_DOWN -> Direction.DOWN;
            default -> Direction.IDLE;
        };
    }

    // ───────────── Observer ─────────────

    public void addObserver(ElevatorObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(ElevatorObserver observer) {
        observers.remove(observer);
    }

    private void notifyFloorChanged() {
        observers.forEach(o -> o.onFloorChanged(this));
    }

    private void notifyStateChanged() {
        observers.forEach(o -> o.onStateChanged(this));
    }

    private void notifyDoorOpened() {
        observers.forEach(o -> o.onDoorOpened(this));
    }

    private void notifyDoorClosed() {
        observers.forEach(o -> o.onDoorClosed(this));
    }

    // ───────────── Getters ─────────────

    public int getElevatorId() {
        return elevatorId;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public ElevatorState getState() {
        return state;
    }

    public Door getDoor() {
        return door;
    }

    public Display getDisplay() {
        return display;
    }

    public int getCurrentWeight() {
        return currentWeight;
    }

    public int getMaxWeight() {
        return maxWeight;
    }

    public int getMinFloor() {
        return minFloor;
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    public int getStopCount() {
        lock.lock();
        try {
            return upStops.size() + downStops.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "Elevator{id=" + elevatorId + ", floor=" + currentFloor +
                ", state=" + state + ", stops=" + getStopCount() + "}";
    }
}

