package org.systemdesign.wms.model;

/**
 * Represents a line item in an order — a product and its requested quantity.
 */
public class OrderItem {

    private final Product product;
    private final int quantity;

    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }

    @Override
    public String toString() {
        return "OrderItem{" + product.getSku() + " x " + quantity + "}";
    }
}

