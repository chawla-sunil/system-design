package org.designpatterns.behavioral.visitor;

/**
 * Concrete Element - Book item in the shopping cart.
 */
public class Book implements ShoppingElement {
    private final String title;
    private final double price;
    private final String isbn;

    public Book(String title, double price, String isbn) {
        this.title = title;
        this.price = price;
        this.isbn = isbn;
    }

    public String getTitle() { return title; }
    public double getPrice() { return price; }
    public String getIsbn() { return isbn; }

    @Override
    public void accept(ShoppingCartVisitor visitor) {
        visitor.visit(this);  // Double dispatch!
    }
}

