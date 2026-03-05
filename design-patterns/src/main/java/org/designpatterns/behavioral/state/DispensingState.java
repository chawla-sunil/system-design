package org.designpatterns.behavioral.state;

public class DispensingState implements VendingMachineState {
    @Override
    public void insertMoney(VendingMachine machine, double amount) {
        System.out.println("  [Dispensing] Please wait, dispensing in progress...");
    }

    @Override
    public void selectProduct(VendingMachine machine, String product) {
        System.out.println("  [Dispensing] Please wait, dispensing in progress...");
    }

    @Override
    public void dispense(VendingMachine machine) {
        String product = machine.getSelectedProduct();
        double price = machine.getProductPrice(product);
        machine.deductBalance(price);
        machine.reduceStock(product);

        System.out.println("  [Dispensing] Dispensed: " + product + ". Change: $" + String.format("%.2f", machine.getBalance()));

        if (machine.getBalance() > 0) {
            System.out.println("  [Dispensing] Returning change: $" + String.format("%.2f", machine.getBalance()));
            machine.resetBalance();
        }
        machine.setState(new IdleState());
    }

    @Override
    public String getStateName() { return "DISPENSING"; }
}
