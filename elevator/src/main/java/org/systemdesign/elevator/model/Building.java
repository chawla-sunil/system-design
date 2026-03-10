package org.systemdesign.elevator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the building — Singleton.
 * Holds all floors and elevators.
 * <p>
 * Uses double-checked locking with volatile for thread-safe lazy initialization.
 */
public class Building {

    private static volatile Building instance;

    private final String name;
    private final int totalFloors;
    private final List<Floor> floors;
    private final List<Elevator> elevators;

    private Building(String name, int totalFloors) {
        this.name = name;
        this.totalFloors = totalFloors;
        this.floors = new ArrayList<>();
        this.elevators = new ArrayList<>();
    }

    public static Building getInstance(String name, int totalFloors) {
        if (instance == null) {
            synchronized (Building.class) {
                if (instance == null) {
                    instance = new Building(name, totalFloors);
                }
            }
        }
        return instance;
    }

    /** For testing — allows resetting the singleton */
    public static void reset() {
        synchronized (Building.class) {
            instance = null;
        }
    }

    public void addFloor(Floor floor) {
        floors.add(floor);
    }

    public void addElevator(Elevator elevator) {
        elevators.add(elevator);
    }

    public Floor getFloor(int floorNumber) {
        if (floorNumber < 0 || floorNumber >= floors.size()) {
            throw new IllegalArgumentException("Invalid floor: " + floorNumber);
        }
        return floors.get(floorNumber);
    }

    public Elevator getElevator(int elevatorId) {
        return elevators.stream()
                .filter(e -> e.getElevatorId() == elevatorId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Elevator not found: " + elevatorId));
    }

    public String getName() {
        return name;
    }

    public int getTotalFloors() {
        return totalFloors;
    }

    public List<Floor> getFloors() {
        return Collections.unmodifiableList(floors);
    }

    public List<Elevator> getElevators() {
        return Collections.unmodifiableList(elevators);
    }

    @Override
    public String toString() {
        return "Building{name='" + name + "', floors=" + totalFloors + ", elevators=" + elevators.size() + "}";
    }
}

