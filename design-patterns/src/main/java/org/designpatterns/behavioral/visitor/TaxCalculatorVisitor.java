package org.designpatterns.behavioral.visitor;

/**
 * Concrete Visitor - Calculates tax for each item type.
 * Books: 0% tax (exempt), Electronics: 18% tax, Grocery: 5% tax.
 */
public class TaxCalculatorVisitor implements ShoppingCartVisitor {
    private double totalTax = 0;

    @Override
    public void visit(Book book) {
        // Books are tax-exempt
        System.out.printf("  Book: \"%s\" - Tax: $0.00 (tax-exempt)%n", book.getTitle());
    }

    @Override
    public void visit(Electronics electronics) {
        double tax = electronics.getPrice() * 0.18;  // 18% tax
        totalTax += tax;
        System.out.printf("  Electronics: %s - Tax: $%.2f (18%%)%n",
                electronics.getName(), tax);
    }

    @Override
    public void visit(Grocery grocery) {
        double tax = grocery.getPrice() * 0.05;  // 5% tax
        totalTax += tax;
        System.out.printf("  Grocery: %s - Tax: $%.2f (5%%)%n",
                grocery.getName(), tax);
    }

    public double getTotalTax() { return totalTax; }
}

