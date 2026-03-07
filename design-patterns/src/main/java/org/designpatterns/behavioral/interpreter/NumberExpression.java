package org.designpatterns.behavioral.interpreter;

/**
 * Terminal Expression - Represents a number (leaf node in expression tree).
 */
public class NumberExpression implements Expression {
    private final int number;

    public NumberExpression(int number) {
        this.number = number;
    }

    public NumberExpression(String number) {
        this.number = Integer.parseInt(number.trim());
    }

    @Override
    public int interpret() {
        return number;
    }

    @Override
    public String toString() {
        return String.valueOf(number);
    }
}

