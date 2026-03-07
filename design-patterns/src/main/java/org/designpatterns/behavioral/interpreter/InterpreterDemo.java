package org.designpatterns.behavioral.interpreter;

public class InterpreterDemo {
    public static void run() {
        System.out.println("=== INTERPRETER PATTERN DEMO ===\n");

        ExpressionParser parser = new ExpressionParser();

        // Simple expressions
        System.out.println("--- Simple Expressions ---");
        evaluate(parser, "3 + 5");
        evaluate(parser, "10 - 4");
        evaluate(parser, "6 * 7");

        // Compound expressions (left-to-right evaluation)
        System.out.println("\n--- Compound Expressions ---");
        evaluate(parser, "3 + 5 - 2");
        evaluate(parser, "10 * 2 + 3");
        evaluate(parser, "100 - 20 - 30 + 5");

        // Build expression tree programmatically
        System.out.println("\n--- Programmatic Expression Tree ---");
        // (3 + 5) * (10 - 2) = 8 * 8 = 64
        Expression expr = new MultiplyExpression(
                new AddExpression(new NumberExpression(3), new NumberExpression(5)),
                new SubtractExpression(new NumberExpression(10), new NumberExpression(2))
        );
        System.out.printf("  %s = %d%n", expr, expr.interpret());

        System.out.println();
    }

    private static void evaluate(ExpressionParser parser, String expression) {
        Expression expr = parser.parse(expression);
        System.out.printf("  %s = %d  (tree: %s)%n", expression, expr.interpret(), expr);
    }
}

