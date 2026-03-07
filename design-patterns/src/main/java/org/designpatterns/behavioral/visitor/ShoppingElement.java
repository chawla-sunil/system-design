package org.designpatterns.behavioral.visitor;

/**
 * Element interface - declares accept() for the visitor.
 * Each element in our shopping cart can accept a visitor.
 */
public interface ShoppingElement {
    void accept(ShoppingCartVisitor visitor);
}

