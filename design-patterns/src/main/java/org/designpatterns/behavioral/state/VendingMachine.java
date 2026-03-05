package org.designpatterns.behavioral.state;

import java.util.HashMap;
import java.util.Map;

public class VendingMachine {
    private VendingMachineState state;
    private double balance;
    private String selectedProduct;
    private final Map<String, Double> prices = new HashMap<>();
    private final Map<String, Integer> stock = new HashMap<>();

    public VendingMachine() {
        this.state = new IdleState();
        this.balance = 0;
        prices.put("Cola", 1.50);
        prices.put("Chips", 2.00);
        prices.put("Water", 1.00);
        stock.put("Cola", 3);
        stock.put("Chips", 2);
        stock.put("Water", 5);
    }

    public void insertMoney(double amount) {
        System.out.println("\n> Insert $" + String.format("%.2f", amount) + " (State: " + state.getStateName() + ")");
        state.insertMoney(this, amount);
    }

    public void selectProduct(String product) {
        System.out.println("\n> Select '" + product + "' (State: " + state.getStateName() + ")");
        state.selectProduct(this, product);
    }

    // Internal methods used by states
    public void setState(VendingMachineState state) { this.state = state; }
    public VendingMachineState getState() { return state; }
    public double getBalance() { return balance; }
    public void addBalance(double amount) { this.balance += amount; }
    public void deductBalance(double amount) { this.balance -= amount; }
    public void resetBalance() { this.balance = 0; }
    public String getSelectedProduct() { return selectedProduct; }
    public void setSelectedProduct(String product) { this.selectedProduct = product; }

    public double getProductPrice(String product) {
        return prices.getOrDefault(product, -1.0);
    }

    public void reduceStock(String product) {
        stock.computeIfPresent(product, (k, v) -> v - 1);
    }
}
