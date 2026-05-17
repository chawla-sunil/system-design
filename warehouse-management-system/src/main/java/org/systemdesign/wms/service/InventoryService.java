package org.systemdesign.wms.service;

import org.systemdesign.wms.exception.InsufficientStockException;
import org.systemdesign.wms.exception.LocationFullException;
import org.systemdesign.wms.model.InventoryItem;
import org.systemdesign.wms.model.Product;
import org.systemdesign.wms.model.StorageLocation;
import org.systemdesign.wms.observer.InventoryObserver;
import org.systemdesign.wms.strategy.StorageAllocationStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core service for all inventory operations: receive, deduct, transfer, query.
 * Uses Strategy pattern for storage allocation and Observer pattern for stock alerts.
 */
public class InventoryService {

    private final List<StorageLocation> locations;
    private final StorageAllocationStrategy allocationStrategy;
    private final List<InventoryObserver> observers;

    public InventoryService(List<StorageLocation> locations,
                            StorageAllocationStrategy allocationStrategy) {
        this.locations = locations;
        this.allocationStrategy = allocationStrategy;
        this.observers = new ArrayList<>();
    }

    // ----- Observer management -----

    public void addObserver(InventoryObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(InventoryObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(Product product) {
        int totalStock = getTotalStock(product);
        for (InventoryObserver observer : observers) {
            observer.onStockLevelChanged(product, totalStock);
        }
    }

    // ----- Core inventory operations -----

    /**
     * Receive stock into the warehouse.
     * Uses the allocation strategy to decide where to place it.
     */
    public StorageLocation receiveStock(Product product, int quantity) {
        StorageLocation location = allocationStrategy.allocate(product, quantity, locations);
        if (location == null) {
            throw new LocationFullException("No suitable storage location found for "
                    + quantity + " units of " + product.getSku());
        }
        location.addStock(product, quantity);
        System.out.println("📦 Received " + quantity + " units of '" + product.getName()
                + "' at location " + location.getLocationCode());
        notifyObservers(product);
        return location;
    }

    /**
     * Receive stock at a specific location (used for manual placement or inbound orders).
     */
    public void receiveStockAtLocation(Product product, int quantity, StorageLocation location) {
        location.addStock(product, quantity);
        System.out.println("📦 Received " + quantity + " units of '" + product.getName()
                + "' at location " + location.getLocationCode());
        notifyObservers(product);
    }

    /**
     * Deduct stock from the warehouse.
     * Finds locations holding this product and deducts the required quantity.
     * Uses a greedy approach — takes from locations with the most stock first.
     */
    public void deductStock(Product product, int quantity) {
        int totalAvailable = getTotalStock(product);
        if (totalAvailable < quantity) {
            throw new InsufficientStockException("Insufficient stock for "
                    + product.getSku() + ". Available: " + totalAvailable
                    + ", Requested: " + quantity);
        }

        int remaining = quantity;
        for (StorageLocation loc : locations) {
            if (remaining <= 0) break;
            int available = loc.getStockForProduct(product);
            if (available > 0) {
                int toDeduct = Math.min(available, remaining);
                loc.removeStock(product, toDeduct);
                System.out.println("📤 Deducted " + toDeduct + " units of '"
                        + product.getName() + "' from " + loc.getLocationCode());
                remaining -= toDeduct;
            }
        }
        notifyObservers(product);
    }

    /**
     * Transfer stock from one location to another.
     */
    public void transferStock(Product product, int quantity,
                              StorageLocation from, StorageLocation to) {
        int availableAtSource = from.getStockForProduct(product);
        if (availableAtSource < quantity) {
            throw new InsufficientStockException("Cannot transfer " + quantity
                    + " units from " + from.getLocationCode()
                    + ". Available: " + availableAtSource);
        }
        from.removeStock(product, quantity);
        to.addStock(product, quantity);
        System.out.println("🔄 Transferred " + quantity + " units of '"
                + product.getName() + "' from " + from.getLocationCode()
                + " to " + to.getLocationCode());
    }

    /**
     * Get total stock of a product across all locations.
     */
    public int getTotalStock(Product product) {
        int total = 0;
        for (StorageLocation loc : locations) {
            total += loc.getStockForProduct(product);
        }
        return total;
    }

    /**
     * Get inventory breakdown by location for a product.
     */
    public List<InventoryItem> getStockByLocation(Product product) {
        List<InventoryItem> result = new ArrayList<>();
        for (StorageLocation loc : locations) {
            Map<String, InventoryItem> items = loc.getInventoryItems();
            InventoryItem item = items.get(product.getProductId());
            if (item != null && item.getQuantity() > 0) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Get all products that are at or below their reorder threshold.
     */
    public List<Product> getLowStockProducts(List<Product> allProducts) {
        List<Product> lowStock = new ArrayList<>();
        for (Product product : allProducts) {
            if (getTotalStock(product) <= product.getReorderThreshold()) {
                lowStock.add(product);
            }
        }
        return lowStock;
    }

    /**
     * Print a full inventory report.
     */
    public void printInventoryReport(List<Product> allProducts) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 INVENTORY REPORT");
        System.out.println("=".repeat(60));
        for (Product p : allProducts) {
            int total = getTotalStock(p);
            String alert = total <= p.getReorderThreshold() ? " ⚠️ LOW" : "";
            System.out.printf("  %-20s (SKU: %-10s) → %4d units%s%n",
                    p.getName(), p.getSku(), total, alert);
            List<InventoryItem> breakdown = getStockByLocation(p);
            for (InventoryItem item : breakdown) {
                System.out.printf("      └─ %-15s : %4d units%n",
                        item.getLocation().getLocationCode(), item.getQuantity());
            }
        }
        System.out.println("=".repeat(60));
    }
}

