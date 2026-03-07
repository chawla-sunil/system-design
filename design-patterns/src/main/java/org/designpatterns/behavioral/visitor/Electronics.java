package org.designpatterns.behavioral.visitor;

/**
 * Concrete Element - Electronics item in the shopping cart.
 */
public class Electronics implements ShoppingElement {
    private final String name;
    private final double price;
    private final double weight;  // in kg

    public Electronics(String name, double price, double weight) {
        this.name = name;
        this.price = price;
        this.weight = weight;
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public double getWeight() { return weight; }

    @Override
    public void accept(ShoppingCartVisitor visitor) {
        visitor.visit(this);  // Double dispatch!
    }
}

