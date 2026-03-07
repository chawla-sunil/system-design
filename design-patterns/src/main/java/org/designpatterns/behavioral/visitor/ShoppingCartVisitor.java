package org.designpatterns.behavioral.visitor;

/**
 * Visitor interface - declares a visit method for each concrete element type.
 * Adding a new operation = adding a new visitor (no need to change element classes).
 */
public interface ShoppingCartVisitor {
    void visit(Book book);
    void visit(Electronics electronics);
    void visit(Grocery grocery);
}

