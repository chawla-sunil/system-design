package org.designpatterns.behavioral.interpreter;

/**
 * Non-Terminal Expression - Represents multiplication of two expressions.
 */
public class MultiplyExpression implements Expression {
    private final Expression left;
    private final Expression right;

    public MultiplyExpression(Expression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public int interpret() {
        return left.interpret() * right.interpret();
    }

    @Override
    public String toString() {
        return "(" + left + " * " + right + ")";
    }
}

