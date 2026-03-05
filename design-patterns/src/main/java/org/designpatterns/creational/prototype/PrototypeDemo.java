package org.designpatterns.creational.prototype;

public class PrototypeDemo {

    public static void run() {
        System.out.println("=== PROTOTYPE PATTERN DEMO ===\n");

        // 1. Create original documents (expensive)
        System.out.println("--- Creating original prototypes ---");
        Report monthlyReport = new Report("Monthly Sales", "Sales data for Q1...", "Finance Team", "MONTHLY");
        Spreadsheet budgetSheet = new Spreadsheet("Budget 2024", "Budget allocations...", "CFO", 100, 20);

        // 2. Register prototypes
        DocumentRegistry registry = new DocumentRegistry();
        registry.registerPrototype("monthly-report", monthlyReport);
        registry.registerPrototype("budget", budgetSheet);

        // 3. Clone documents (cheap - no expensive init)
        System.out.println("\n--- Cloning from prototypes ---");
        Document reportClone1 = registry.createDocument("monthly-report");
        reportClone1.setTitle("Monthly Sales - April");

        Document reportClone2 = registry.createDocument("monthly-report");
        reportClone2.setTitle("Monthly Sales - May");

        Document budgetClone = registry.createDocument("budget");
        budgetClone.setTitle("Budget 2025");

        System.out.println("\n--- Results ---");
        System.out.println("Original: " + monthlyReport);
        System.out.println("Clone 1:  " + reportClone1);
        System.out.println("Clone 2:  " + reportClone2);
        System.out.println("Budget:   " + budgetClone);
        System.out.println("Same object? " + (monthlyReport == reportClone1)); // false

        System.out.println();
    }
}
