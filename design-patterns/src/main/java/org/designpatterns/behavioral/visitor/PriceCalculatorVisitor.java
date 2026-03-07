package org.designpatterns.behavioral.visitor;

/**
 * Concrete Visitor - Calculates the total price with item-specific discounts.
 * Books: 10% discount, Electronics: 5% discount, Grocery: no discount (but perishable gets 15% off).
 */
public class PriceCalculatorVisitor implements ShoppingCartVisitor {
    private double totalPrice = 0;

    @Override
    public void visit(Book book) {
        double discounted = book.getPrice() * 0.90;  // 10% off books
        totalPrice += discounted;
        System.out.printf("  Book: \"%s\" - $%.2f (10%% off: $%.2f)%n",
                book.getTitle(), book.getPrice(), discounted);
    }

    @Override
    public void visit(Electronics electronics) {
        double discounted = electronics.getPrice() * 0.95;  // 5% off electronics
        totalPrice += discounted;
        System.out.printf("  Electronics: %s - $%.2f (5%% off: $%.2f)%n",
                electronics.getName(), electronics.getPrice(), discounted);
    }

    @Override
    public void visit(Grocery grocery) {
        double discounted = grocery.isPerishable()
                ? grocery.getPrice() * 0.85  // 15% off perishable
                : grocery.getPrice();
        totalPrice += discounted;
        System.out.printf("  Grocery: %s - $%.2f%s%n",
                grocery.getName(), discounted,
                grocery.isPerishable() ? " (15% perishable discount)" : "");
    }

    public double getTotalPrice() { return totalPrice; }
}

