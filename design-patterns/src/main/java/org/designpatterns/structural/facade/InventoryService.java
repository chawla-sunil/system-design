package org.designpatterns.structural.facade;

public class InventoryService {
    public boolean checkStock(String productId) {
        System.out.println("  [Inventory] Checking stock for product: " + productId);
        return true; // Simulated
    }

    public void reserveStock(String productId, int quantity) {
        System.out.println("  [Inventory] Reserved " + quantity + " units of " + productId);
    }
}
