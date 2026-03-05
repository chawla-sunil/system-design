package org.designpatterns.behavioral.state;

public class IdleState implements VendingMachineState {
    @Override
    public void insertMoney(VendingMachine machine, double amount) {
        System.out.println("  [Idle] Money inserted: $" + String.format("%.2f", amount));
        machine.addBalance(amount);
        machine.setState(new HasMoneyState());
    }

    @Override
    public void selectProduct(VendingMachine machine, String product) {
        System.out.println("  [Idle] Please insert money first!");
    }

    @Override
    public void dispense(VendingMachine machine) {
        System.out.println("  [Idle] Please insert money and select a product first!");
    }

    @Override
    public String getStateName() { return "IDLE"; }
}
