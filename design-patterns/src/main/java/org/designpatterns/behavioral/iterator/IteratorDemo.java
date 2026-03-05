package org.designpatterns.behavioral.iterator;

public class IteratorDemo {
    public static void run() {
        System.out.println("=== ITERATOR PATTERN DEMO ===\n");

        EmployeeCollection company = new EmployeeCollection();
        company.addEmployee(new Employee("Alice", "Engineering", 95000));
        company.addEmployee(new Employee("Bob", "Engineering", 88000));
        company.addEmployee(new Employee("Charlie", "Marketing", 72000));
        company.addEmployee(new Employee("Diana", "Engineering", 105000));
        company.addEmployee(new Employee("Eve", "Marketing", 68000));
        company.addEmployee(new Employee("Frank", "HR", 75000));

        // Iterate all employees
        System.out.println("--- All Employees ---");
        EmployeeIterator allIter = company.iterator();
        while (allIter.hasNext()) {
            System.out.println("  " + allIter.next());
        }

        // Iterate only Engineering department
        System.out.println("\n--- Engineering Department ---");
        EmployeeIterator engIter = company.departmentIterator("Engineering");
        while (engIter.hasNext()) {
            System.out.println("  " + engIter.next());
        }

        // Iterate employees with salary <= 80000
        System.out.println("\n--- Salary <= $80,000 ---");
        EmployeeIterator salaryIter = company.salaryCeilingIterator(80000);
        while (salaryIter.hasNext()) {
            System.out.println("  " + salaryIter.next());
        }

        System.out.println();
    }
}
