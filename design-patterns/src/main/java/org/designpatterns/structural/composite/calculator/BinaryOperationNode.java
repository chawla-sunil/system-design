package org.designpatterns.structural.composite.calculator;

public class BinaryOperationNode implements CalculatorComponent {
    public enum Operation {
        ADD("+") {
            @Override
            double apply(double left, double right) { return left + right; }
        },
        SUBTRACT("-") {
            @Override
            double apply(double left, double right) { return left - right; }
        },
        MULTIPLY("*") {
            @Override
            double apply(double left, double right) { return left * right; }
        },
        DIVIDE("/") {
            @Override
            double apply(double left, double right) {
                if (Double.compare(right, 0.0d) == 0) {
                    throw new ArithmeticException("Division by zero is not allowed");
                }
                return left / right;
            }
        };

        private final String symbol;

        Operation(String symbol) {
            this.symbol = symbol;
        }

        String getSymbol() {
            return symbol;
        }

        abstract double apply(double left, double right);
    }

    private final Operation operation;
    private final CalculatorComponent left;
    private final CalculatorComponent right;

    public BinaryOperationNode(Operation operation, CalculatorComponent left, CalculatorComponent right) {
        this.operation = operation;
        this.left = left;
        this.right = right;
    }

    @Override
    public double evaluate() {
        return operation.apply(left.evaluate(), right.evaluate());
    }

    @Override
    public String toExpression() {
        return "(" + left.toExpression() + " " + operation.getSymbol() + " " + right.toExpression() + ")";
    }
}

