package org.designpatterns.behavioral.iterator;

import java.util.ArrayList;
import java.util.List;

public class EmployeeCollection {
    private final List<Employee> employees = new ArrayList<>();

    public void addEmployee(Employee employee) {
        employees.add(employee);
    }

    public EmployeeIterator iterator() {
        return new AllEmployeeIterator();
    }

    public EmployeeIterator departmentIterator(String department) {
        return new DepartmentIterator(department);
    }

    public EmployeeIterator salaryCeilingIterator(double maxSalary) {
        return new SalaryCeilingIterator(maxSalary);
    }

    // Inner class: iterates over all employees
    private class AllEmployeeIterator implements EmployeeIterator {
        private int index = 0;

        @Override
        public boolean hasNext() { return index < employees.size(); }

        @Override
        public Employee next() { return employees.get(index++); }
    }

    // Inner class: filters by department
    private class DepartmentIterator implements EmployeeIterator {
        private final String department;
        private int index = 0;

        DepartmentIterator(String department) {
            this.department = department;
        }

        @Override
        public boolean hasNext() {
            while (index < employees.size()) {
                if (employees.get(index).getDepartment().equals(department)) return true;
                index++;
            }
            return false;
        }

        @Override
        public Employee next() { return employees.get(index++); }
    }

    // Inner class: filters by salary ceiling
    private class SalaryCeilingIterator implements EmployeeIterator {
        private final double maxSalary;
        private int index = 0;

        SalaryCeilingIterator(double maxSalary) {
            this.maxSalary = maxSalary;
        }

        @Override
        public boolean hasNext() {
            while (index < employees.size()) {
                if (employees.get(index).getSalary() <= maxSalary) return true;
                index++;
            }
            return false;
        }

        @Override
        public Employee next() { return employees.get(index++); }
    }
}
