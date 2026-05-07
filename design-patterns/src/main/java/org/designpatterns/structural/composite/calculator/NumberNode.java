package org.designpatterns.structural.composite.calculator;

public class NumberNode implements CalculatorComponent {
    private final double value;

    public NumberNode(double value) {
        this.value = value;
    }

    @Override
    public double evaluate() {
        return value;
    }

    @Override
    public String toExpression() {
        return String.valueOf(value);
    }
}

