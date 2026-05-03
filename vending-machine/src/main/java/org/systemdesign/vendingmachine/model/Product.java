package org.systemdesign.vendingmachine.model;

public record Product(String id, String name, int priceInCents) {

    public Product {
        if (priceInCents <= 0) {
            throw new IllegalArgumentException("priceInCents must be positive");
        }
    }
}

