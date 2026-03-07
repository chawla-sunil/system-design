package org.designpatterns.behavioral.interpreter;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Parser/Client - Parses a simple math expression string into an Expression tree.
 * Supports: +, -, * operators with left-to-right evaluation (no precedence).
 * Uses a simple tokenizer approach for demonstration.
 */
public class ExpressionParser {

    /**
     * Parses expressions like "3 + 5 - 2" or "10 * 2 + 3".
     * Evaluates strictly left-to-right for simplicity.
     */
    public Expression parse(String expression) {
        String[] tokens = expression.trim().split("\\s+");

        if (tokens.length == 0) {
            throw new IllegalArgumentException("Empty expression");
        }

        Expression result = new NumberExpression(tokens[0]);

        for (int i = 1; i < tokens.length; i += 2) {
            if (i + 1 >= tokens.length) {
                throw new IllegalArgumentException("Invalid expression: missing operand after " + tokens[i]);
            }
            String operator = tokens[i];
            Expression right = new NumberExpression(tokens[i + 1]);

            result = switch (operator) {
                case "+" -> new AddExpression(result, right);
                case "-" -> new SubtractExpression(result, right);
                case "*" -> new MultiplyExpression(result, right);
                default -> throw new IllegalArgumentException("Unknown operator: " + operator);
            };
        }

        return result;
    }
}

