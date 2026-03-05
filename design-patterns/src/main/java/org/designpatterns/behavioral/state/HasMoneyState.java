package org.designpatterns.behavioral.state;

public class HasMoneyState implements VendingMachineState {
    @Override
    public void insertMoney(VendingMachine machine, double amount) {
        machine.addBalance(amount);
        System.out.println("  [HasMoney] Added $" + String.format("%.2f", amount) + ". Balance: $" + String.format("%.2f", machine.getBalance()));
    }

    @Override
    public void selectProduct(VendingMachine machine, String product) {
        double price = machine.getProductPrice(product);
        if (price < 0) {
            System.out.println("  [HasMoney] Product '" + product + "' not available!");
            return;
        }
        if (machine.getBalance() >= price) {
            System.out.println("  [HasMoney] Product '" + product + "' selected. Price: $" + String.format("%.2f", price));
            machine.setSelectedProduct(product);
            machine.setState(new DispensingState());
            machine.getState().dispense(machine);
        } else {
            System.out.println("  [HasMoney] Insufficient funds. Need $" + String.format("%.2f", price) + ", have $" + String.format("%.2f", machine.getBalance()));
        }
    }

    @Override
    public void dispense(VendingMachine machine) {
        System.out.println("  [HasMoney] Please select a product first!");
    }

    @Override
    public String getStateName() { return "HAS_MONEY"; }
}
