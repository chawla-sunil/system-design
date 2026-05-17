package org.systemdesign.wms.service;

import org.systemdesign.wms.model.Product;
import org.systemdesign.wms.model.StorageLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages warehouse-level resources: storage locations and product catalog.
 */
public class WarehouseService {

    private final List<StorageLocation> locations;
    private final Map<String, Product> productsBySku;

    public WarehouseService() {
        this.locations = new ArrayList<>();
        this.productsBySku = new ConcurrentHashMap<>();
    }

    // ----- Location management -----

    public StorageLocation addLocation(String rack, String shelf, String bin, double maxCapacity) {
        StorageLocation location = new StorageLocation(rack, shelf, bin, maxCapacity);
        locations.add(location);
        System.out.println("📍 Added storage location: " + location.getLocationCode()
                + " (capacity: " + maxCapacity + " m³)");
        return location;
    }

    public List<StorageLocation> getAllLocations() {
        return locations;
    }

    public StorageLocation getLocationByCode(String rack, String shelf, String bin) {
        return locations.stream()
                .filter(loc -> loc.getRack().equals(rack)
                        && loc.getShelf().equals(shelf)
                        && loc.getBin().equals(bin))
                .findFirst()
                .orElse(null);
    }

    // ----- Product catalog management -----

    public Product addProduct(String sku, String name, String description,
                              double weight, double volume, int reorderThreshold) {
        if (productsBySku.containsKey(sku)) {
            throw new RuntimeException("Product with SKU '" + sku + "' already exists.");
        }
        Product product = new Product(sku, name, description, weight, volume, reorderThreshold);
        productsBySku.put(sku, product);
        System.out.println("🏷️  Registered product: " + product);
        return product;
    }

    public Product getProductBySku(String sku) {
        return productsBySku.get(sku);
    }

    public List<Product> getAllProducts() {
        return new ArrayList<>(productsBySku.values());
    }

    /**
     * Print warehouse layout.
     */
    public void printWarehouseLayout() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🏭 WAREHOUSE LAYOUT");
        System.out.println("=".repeat(60));
        for (StorageLocation loc : locations) {
            System.out.printf("  %-15s │ Capacity: %6.2f m³ │ Used: %6.2f m³ │ Free: %6.2f m³%n",
                    loc.getLocationCode(), loc.getMaxCapacity(),
                    loc.getCurrentOccupiedVolume(), loc.getAvailableCapacity());
        }
        System.out.println("=".repeat(60));
    }
}

