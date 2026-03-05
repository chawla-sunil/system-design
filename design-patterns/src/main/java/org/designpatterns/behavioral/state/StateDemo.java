package org.designpatterns.behavioral.state;

public class StateDemo {
    public static void run() {
        System.out.println("=== STATE PATTERN DEMO ===\n");

        VendingMachine machine = new VendingMachine();

        // Scenario 1: Normal purchase
        System.out.println("--- Scenario 1: Normal purchase ---");
        machine.insertMoney(2.00);
        machine.selectProduct("Cola");  // $1.50 -> get $0.50 change

        // Scenario 2: Insufficient funds
        System.out.println("\n--- Scenario 2: Insufficient funds ---");
        machine.insertMoney(1.00);
        machine.selectProduct("Chips"); // $2.00 needed
        machine.insertMoney(1.00);      // Add more
        machine.selectProduct("Chips"); // Now enough

        // Scenario 3: Select without money
        System.out.println("\n--- Scenario 3: No money inserted ---");
        machine.selectProduct("Water");

        System.out.println();
    }
}
