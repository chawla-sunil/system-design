package org.systemdesign.vendingmachine.model;

public enum Coin {
    NICKEL(5),
    DIME(10),
    QUARTER(25);

    private final int value;

    Coin(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}

