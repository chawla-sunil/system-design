package org.systemdesign.wms.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a physical storage location in the warehouse — identified by rack, shelf, bin.
 * Each location can hold multiple products (InventoryItems).
 */
public class StorageLocation {

    private final String locationId;
    private final String rack;
    private final String shelf;
    private final String bin;
    private final double maxCapacity;       // max volume in cubic meters
    private double currentOccupiedVolume;

    // productId → InventoryItem
    private final Map<String, InventoryItem> inventoryItems;

    public StorageLocation(String rack, String shelf, String bin, double maxCapacity) {
        this.locationId = UUID.randomUUID().toString();
        this.rack = rack;
        this.shelf = shelf;
        this.bin = bin;
        this.maxCapacity = maxCapacity;
        this.currentOccupiedVolume = 0.0;
        this.inventoryItems = new ConcurrentHashMap<>();
    }

    public String getLocationId() { return locationId; }
    public String getRack() { return rack; }
    public String getShelf() { return shelf; }
    public String getBin() { return bin; }
    public double getMaxCapacity() { return maxCapacity; }
    public double getCurrentOccupiedVolume() { return currentOccupiedVolume; }
    public double getAvailableCapacity() { return maxCapacity - currentOccupiedVolume; }
    public Map<String, InventoryItem> getInventoryItems() { return inventoryItems; }

    public String getLocationCode() {
        return rack + "-" + shelf + "-" + bin;
    }

    /**
     * Add stock of a product to this location.
     * Thread-safe: synchronized on this location.
     */
    public synchronized void addStock(Product product, int quantity) {
        double volumeNeeded = product.getVolume() * quantity;
        if (volumeNeeded > getAvailableCapacity()) {
            throw new RuntimeException("Location " + getLocationCode()
                    + " does not have enough capacity. Available: "
                    + getAvailableCapacity() + ", Needed: " + volumeNeeded);
        }

        InventoryItem item = inventoryItems.get(product.getProductId());
        if (item == null) {
            item = new InventoryItem(product, this, quantity);
            inventoryItems.put(product.getProductId(), item);
        } else {
            item.adjustQuantity(quantity);
        }
        currentOccupiedVolume += volumeNeeded;
    }

    /**
     * Remove stock of a product from this location.
     * Thread-safe: synchronized on this location.
     */
    public synchronized void removeStock(Product product, int quantity) {
        InventoryItem item = inventoryItems.get(product.getProductId());
        if (item == null || item.getQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock of " + product.getSku()
                    + " at location " + getLocationCode());
        }
        item.adjustQuantity(-quantity);
        currentOccupiedVolume -= product.getVolume() * quantity;

        if (item.getQuantity() == 0) {
            inventoryItems.remove(product.getProductId());
        }
    }

    public int getStockForProduct(Product product) {
        InventoryItem item = inventoryItems.get(product.getProductId());
        return item != null ? item.getQuantity() : 0;
    }

    @Override
    public String toString() {
        return "StorageLocation{" + getLocationCode()
                + ", occupied=" + currentOccupiedVolume
                + "/" + maxCapacity + "}";
    }
}

