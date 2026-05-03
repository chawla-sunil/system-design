package org.systemdesign.vendingmachine.model;

public final class Slot {
    private final String code;
    private final Product product;
    private int quantity;

    public Slot(String code, Product product, int quantity) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        this.code = code;
        this.product = product;
        this.quantity = quantity;
    }

    public String code() {
        return code;
    }

    public Product product() {
        return product;
    }

    public int quantity() {
        return quantity;
    }

    public boolean isOutOfStock() {
        return quantity == 0;
    }

    public void add(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        quantity += amount;
    }

    public void dispenseOne() {
        if (quantity <= 0) {
            throw new IllegalStateException("Slot " + code + " is out of stock");
        }
        quantity--;
    }
}

