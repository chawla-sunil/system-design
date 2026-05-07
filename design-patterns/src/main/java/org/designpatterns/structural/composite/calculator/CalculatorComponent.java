package org.designpatterns.structural.composite.calculator;

public interface CalculatorComponent {
    double evaluate();
    String toExpression();
    default void display() {
        System.out.println(toExpression());
    }
}

