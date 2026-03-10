package org.systemdesign.elevator;

import org.systemdesign.elevator.factory.BuildingFactory;
import org.systemdesign.elevator.model.ExternalRequest;
import org.systemdesign.elevator.model.InternalRequest;
import org.systemdesign.elevator.model.enums.Direction;
import org.systemdesign.elevator.service.ElevatorController;

/**
 * Demo — simulates a 10-floor building with 3 elevators.
 * <p>
 * Scenario:
 * 1. Person on floor 3 presses UP → elevator dispatched, then they press floor 7
 * 2. Person on floor 7 presses DOWN → elevator dispatched, then they press floor 0
 * 3. Person on floor 0 presses UP → elevator dispatched, then they press floor 9
 * 4. Elevator 2 goes into maintenance — subsequent requests skip it
 * 5. Multiple concurrent requests from different floors
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("     🛗  ELEVATOR SYSTEM — LLD DEMO  🛗     ");
        System.out.println("═══════════════════════════════════════════════\n");

        // ── Step 1: Create building (10 floors, 3 elevators, LOOK strategy) ──
        ElevatorController controller = BuildingFactory.createStandardBuilding("TechPark Tower", 10, 3);
        controller.start();
        Thread.sleep(500); // let services initialize

        System.out.println("\n── Scenario 1: Floor 3 → UP → destination floor 7 ──");
        controller.handleExternalRequest(new ExternalRequest(3, Direction.UP));
        Thread.sleep(4000); // wait for elevator to reach floor 3
        controller.handleInternalRequest(new InternalRequest(1, 3, 7));
        Thread.sleep(5000); // wait for elevator to reach floor 7

        System.out.println("\n── Scenario 2: Floor 7 → DOWN → destination floor 0 ──");
        controller.handleExternalRequest(new ExternalRequest(7, Direction.DOWN));
        Thread.sleep(2000);
        controller.handleInternalRequest(new InternalRequest(1, 7, 0));
        Thread.sleep(8000);

        System.out.println("\n── Scenario 3: Elevator 2 → MAINTENANCE ──");
        controller.setMaintenance(2);
        Thread.sleep(500);

        System.out.println("\n── Scenario 4: Concurrent requests from floor 1 and floor 8 ──");
        // Both requests fire — elevator 2 is in maintenance, so only 1 and 3 are used
        Thread t1 = new Thread(() -> controller.handleExternalRequest(new ExternalRequest(1, Direction.UP)));
        Thread t2 = new Thread(() -> controller.handleExternalRequest(new ExternalRequest(8, Direction.DOWN)));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        Thread.sleep(5000);

        // Internal requests for the concurrent passengers
        controller.handleInternalRequest(new InternalRequest(1, 1, 5));
        controller.handleInternalRequest(new InternalRequest(3, 8, 2));
        Thread.sleep(10000);

        System.out.println("\n── Scenario 5: Clear maintenance on elevator 2 ──");
        controller.clearMaintenance(2);
        controller.handleExternalRequest(new ExternalRequest(9, Direction.DOWN));
        Thread.sleep(3000);

        // ── Shutdown ──
        System.out.println("\n── Shutting down ──");
        controller.shutdown();

        System.out.println("\n═══════════════════════════════════════════════");
        System.out.println("              Demo complete! 🎉               ");
        System.out.println("═══════════════════════════════════════════════");
    }
}