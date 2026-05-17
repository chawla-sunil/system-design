package org.systemdesign.wms.model;

import java.util.UUID;

/**
 * Represents a Stock Keeping Unit (SKU) — a specific type of product.
 * Immutable value object.
 */
public class Product {

    private final String productId;
    private final String sku;
    private final String name;
    private final String description;
    private final double weight;       // in kg
    private final double volume;       // in cubic meters
    private final int reorderThreshold; // low-stock alert threshold

    public Product(String sku, String name, String description,
                   double weight, double volume, int reorderThreshold) {
        this.productId = UUID.randomUUID().toString();
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.weight = weight;
        this.volume = volume;
        this.reorderThreshold = reorderThreshold;
    }

    public String getProductId() { return productId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getWeight() { return weight; }
    public double getVolume() { return volume; }
    public int getReorderThreshold() { return reorderThreshold; }

    @Override
    public String toString() {
        return "Product{sku='" + sku + "', name='" + name + "'}";
    }
}

