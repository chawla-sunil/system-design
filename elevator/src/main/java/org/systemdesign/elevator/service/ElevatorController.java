package org.systemdesign.elevator.service;

import org.systemdesign.elevator.exception.AllElevatorsUnavailableException;
import org.systemdesign.elevator.exception.ElevatorMaintenanceException;
import org.systemdesign.elevator.exception.InvalidFloorException;
import org.systemdesign.elevator.model.Building;
import org.systemdesign.elevator.model.Elevator;
import org.systemdesign.elevator.model.ExternalRequest;
import org.systemdesign.elevator.model.InternalRequest;
import org.systemdesign.elevator.observer.LoggingObserver;
import org.systemdesign.elevator.strategy.ElevatorSelectionStrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main orchestrator: dispatches requests to elevators and manages their lifecycle.
 *
 * Interview note: ElevatorController is NOT a Singleton. It's an orchestrator
 * that receives its dependencies (Building, Strategy) via constructor injection.
 * This makes it testable — you can create a controller with a mock building
 * and a mock strategy. Compare this with Building which IS a Singleton
 * (there's exactly one building — a domain constraint, not a wiring choice).
 *
 * Responsibilities:
 *   - ElevatorSelectionStrategy → which elevator to dispatch (pluggable)
 *   - ElevatorService (per elevator) → runs the LOOK algorithm
 *   - Validation → floor range, maintenance checks
 *
 * This separation means each class has one reason to change (SRP).
 */
public class ElevatorController {

    private final Building building;
    private final ElevatorSelectionStrategy strategy;
    private final Map<Integer, ElevatorService> elevatorServices;
    private ExecutorService threadPool;

    public ElevatorController(Building building, ElevatorSelectionStrategy strategy) {
        this.building = building;
        this.strategy = strategy;
        this.elevatorServices = new HashMap<>();
    }

    // ──────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────

    /**
     * Initializes and starts one ElevatorService thread per elevator.
     * Registers a LoggingObserver on each elevator for console output.
     */
    public void start() {
        int numElevators = building.getElevators().size();
        threadPool = Executors.newFixedThreadPool(numElevators);

        LoggingObserver loggingObserver = new LoggingObserver();

        for (Elevator elevator : building.getElevators()) {
            elevator.addObserver(loggingObserver);
            ElevatorService service = new ElevatorService(elevator);
            elevatorServices.put(elevator.getElevatorId(), service);
            threadPool.submit(service);
        }

        System.out.println("✅ ElevatorController started with " + numElevators + " elevators");
    }

    /**
     * Gracefully shuts down all elevator service threads.
     */
    public void shutdown() {
        elevatorServices.values().forEach(ElevatorService::stop);
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("🛑 ElevatorController shut down");
    }

    // ──────────────────────────────────────────────
    //  Request Handling
    // ──────────────────────────────────────────────

    /**
     * Handles an external request (someone pressed UP/DOWN on a floor).
     * Uses the strategy to pick the best elevator, then adds the floor as a stop.
     *
     * Thread-safety: strategy.selectElevator() reads elevator state;
     * elevator.addStop() uses a per-elevator ReentrantLock.
     * Multiple threads calling this concurrently is safe.
     */
    public void handleExternalRequest(ExternalRequest request) {
        validateFloor(request.getSourceFloor());

        // Mark the floor button as pressed
        building.getFloor(request.getSourceFloor()).pressButton(request.getDirection());

        Elevator selected = strategy.selectElevator(building.getElevators(), request);
        if (selected == null) {
            throw new AllElevatorsUnavailableException(
                    "No elevator available to serve request at floor " + request.getSourceFloor());
        }

        System.out.printf("📥 External request: floor %d %s → assigned to Elevator %d%n",
                request.getSourceFloor(), request.getDirection(), selected.getElevatorId());

        // Reset the floor button now that an elevator is dispatched
        building.getFloor(request.getSourceFloor()).resetButton(request.getDirection());

        ElevatorService service = elevatorServices.get(selected.getElevatorId());
        service.addStop(request.getSourceFloor());
    }

    /**
     * Handles an internal request (someone inside an elevator pressed a floor button).
     * Routes directly to the specified elevator — no strategy needed.
     */
    public void handleInternalRequest(InternalRequest request) {
        validateFloor(request.getDestinationFloor());

        Elevator elevator = building.getElevator(request.getElevatorId());
        if (!elevator.isAvailable()) {
            throw new ElevatorMaintenanceException(
                    "Elevator " + request.getElevatorId() + " is in maintenance mode");
        }

        System.out.printf("📥 Internal request: Elevator %d → floor %d%n",
                request.getElevatorId(), request.getDestinationFloor());

        ElevatorService service = elevatorServices.get(request.getElevatorId());
        service.addStop(request.getDestinationFloor());
    }

    // ──────────────────────────────────────────────
    //  Maintenance
    // ──────────────────────────────────────────────

    public void setMaintenance(int elevatorId) {
        Elevator elevator = building.getElevator(elevatorId);
        elevator.setToMaintenance();
        System.out.printf("🔧 Elevator %d set to MAINTENANCE%n", elevatorId);
    }

    public void clearMaintenance(int elevatorId) {
        Elevator elevator = building.getElevator(elevatorId);
        elevator.clearMaintenance();
        System.out.printf("✅ Elevator %d cleared from maintenance%n", elevatorId);
    }

    // ──────────────────────────────────────────────
    //  Validation
    // ──────────────────────────────────────────────

    private void validateFloor(int floor) {
        if (floor < 0 || floor >= building.getTotalFloors()) {
            throw new InvalidFloorException(
                    "Floor " + floor + " is out of range [0, " + (building.getTotalFloors() - 1) + "]");
        }
    }

    // ──────────────────────────────────────────────
    //  Getters
    // ──────────────────────────────────────────────

    public Building getBuilding()                        { return building; }
    public ElevatorSelectionStrategy getStrategy()       { return strategy; }
    public ElevatorService getElevatorService(int id)    { return elevatorServices.get(id); }
}

