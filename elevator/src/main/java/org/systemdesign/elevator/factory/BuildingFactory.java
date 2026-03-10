package org.systemdesign.elevator.factory;

import org.systemdesign.elevator.model.Building;
import org.systemdesign.elevator.model.Elevator;
import org.systemdesign.elevator.model.Floor;
import org.systemdesign.elevator.service.ElevatorController;
import org.systemdesign.elevator.strategy.ElevatorSelectionStrategy;
import org.systemdesign.elevator.strategy.LookSelectionStrategy;

/**
 * Factory for creating a fully wired Building with Floors, Elevators, and Controller.
 * Encapsulates the complex object creation — client just calls one method.
 */
public class BuildingFactory {

    private static final int DEFAULT_MAX_WEIGHT = 800; // kg (roughly 10 people)

    private BuildingFactory() { /* utility class, no instances */ }

    /**
     * Creates a standard building with the LOOK selection strategy.
     */
    public static ElevatorController createStandardBuilding(String name, int numFloors, int numElevators) {
        return createBuilding(name, numFloors, numElevators, new LookSelectionStrategy());
    }

    /**
     * Creates a building with a custom elevator selection strategy.
     */
    public static ElevatorController createBuilding(String name, int numFloors, int numElevators,
                                                     ElevatorSelectionStrategy strategy) {
        // Reset Building singleton (for demo / testing — in production, use DI)
        Building.reset();

        // Create building
        Building building = Building.getInstance(name, numFloors);

        // Create floors (0 to numFloors-1)
        for (int i = 0; i < numFloors; i++) {
            building.addFloor(new Floor(i));
        }

        // Create elevators (IDs 1 to numElevators)
        for (int i = 1; i <= numElevators; i++) {
            Elevator elevator = new Elevator(i, 0, numFloors - 1, DEFAULT_MAX_WEIGHT);
            building.addElevator(elevator);
        }

        // Wire up controller (plain constructor — not singleton)
        ElevatorController controller = new ElevatorController(building, strategy);

        System.out.printf("🏢 Building '%s' created: %d floors, %d elevators%n", name, numFloors, numElevators);
        return controller;
    }
}
