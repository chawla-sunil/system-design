package org.designpatterns.behavioral.visitor;

/**
 * Concrete Element - Grocery item in the shopping cart.
 */
public class Grocery implements ShoppingElement {
    private final String name;
    private final double price;
    private final boolean perishable;

    public Grocery(String name, double price, boolean perishable) {
        this.name = name;
        this.price = price;
        this.perishable = perishable;
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public boolean isPerishable() { return perishable; }

    @Override
    public void accept(ShoppingCartVisitor visitor) {
        visitor.visit(this);  // Double dispatch!
    }
}

