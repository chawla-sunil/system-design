package org.designpatterns.behavioral.visitor;

import java.util.List;

public class VisitorDemo {
    public static void run() {
        System.out.println("=== VISITOR PATTERN DEMO ===\n");

        // Create shopping cart items
        List<ShoppingElement> cart = List.of(
                new Book("Design Patterns", 49.99, "978-0201633610"),
                new Book("Clean Code", 39.99, "978-0132350884"),
                new Electronics("Mechanical Keyboard", 129.99, 0.8),
                new Electronics("USB-C Hub", 45.99, 0.2),
                new Grocery("Organic Milk", 5.99, true),
                new Grocery("Rice (5kg)", 12.99, false)
        );

        // Visitor 1: Price calculation with discounts
        System.out.println("--- Price Calculation (with discounts) ---");
        PriceCalculatorVisitor priceVisitor = new PriceCalculatorVisitor();
        for (ShoppingElement item : cart) {
            item.accept(priceVisitor);
        }
        System.out.printf("  TOTAL after discounts: $%.2f%n", priceVisitor.getTotalPrice());

        // Visitor 2: Tax calculation (new operation without modifying elements!)
        System.out.println("\n--- Tax Calculation ---");
        TaxCalculatorVisitor taxVisitor = new TaxCalculatorVisitor();
        for (ShoppingElement item : cart) {
            item.accept(taxVisitor);
        }
        System.out.printf("  TOTAL tax: $%.2f%n", taxVisitor.getTotalTax());

        System.out.printf("\n  GRAND TOTAL: $%.2f (price) + $%.2f (tax) = $%.2f%n",
                priceVisitor.getTotalPrice(), taxVisitor.getTotalTax(),
                priceVisitor.getTotalPrice() + taxVisitor.getTotalTax());

        System.out.println();
    }
}

