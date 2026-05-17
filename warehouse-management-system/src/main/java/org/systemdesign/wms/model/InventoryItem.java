package org.systemdesign.wms.model;

/**
 * Represents the quantity of a specific product at a specific storage location.
 * This is the junction entity between Product and StorageLocation.
 */
public class InventoryItem {

    private final Product product;
    private final StorageLocation location;
    private int quantity;

    public InventoryItem(Product product, StorageLocation location, int quantity) {
        this.product = product;
        this.location = location;
        this.quantity = quantity;
    }

    public Product getProduct() { return product; }
    public StorageLocation getLocation() { return location; }
    public int getQuantity() { return quantity; }

    /**
     * Adjust quantity by delta (positive = add, negative = remove).
     * Caller must hold lock on the StorageLocation.
     */
    public void adjustQuantity(int delta) {
        if (this.quantity + delta < 0) {
            throw new RuntimeException("Cannot reduce quantity below 0. Current: "
                    + quantity + ", Delta: " + delta);
        }
        this.quantity += delta;
    }

    @Override
    public String toString() {
        return "InventoryItem{product=" + product.getSku()
                + ", location=" + location.getLocationCode()
                + ", qty=" + quantity + "}";
    }
}

