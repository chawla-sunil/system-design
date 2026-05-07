package org.designpatterns.structural.composite;

import org.designpatterns.structural.composite.calculator.BinaryOperationNode;
import org.designpatterns.structural.composite.calculator.CalculatorComponent;
import org.designpatterns.structural.composite.calculator.NumberNode;
import org.designpatterns.structural.composite.filesystem.Directory;
import org.designpatterns.structural.composite.filesystem.File;

public class CompositeDemo {
    public static void run() {
        System.out.println("=== COMPOSITE PATTERN DEMO ===\n");

        System.out.println("-- File System Example --");

        // Build file system tree
        Directory root = new Directory("root");

        Directory src = new Directory("src");
        src.add(new File("Main.java", 5));
        src.add(new File("Utils.java", 3));

        Directory test = new Directory("test");
        test.add(new File("MainTest.java", 4));

        Directory docs = new Directory("docs");
        docs.add(new File("README.md", 2));
        docs.add(new File("GUIDE.md", 8));

        root.add(src);
        root.add(test);
        root.add(docs);
        root.add(new File("pom.xml", 1));

        // Uniform treatment of files and directories
        root.display("");
        System.out.println("\nTotal size of root: " + root.getSize() + " KB");
        System.out.println("Total size of src:  " + src.getSize() + " KB");
        System.out.println("==========================");

        System.out.println("\n-- Calculator Example --");

        // Expression: (3 + 5) * (10 - 2)
        CalculatorComponent expression = new BinaryOperationNode(
                BinaryOperationNode.Operation.MULTIPLY,
                new BinaryOperationNode(
                        BinaryOperationNode.Operation.ADD,
                        new NumberNode(3),
                        new NumberNode(5)
                ),
                new BinaryOperationNode(
                        BinaryOperationNode.Operation.SUBTRACT,
                        new NumberNode(10),
                        new NumberNode(2)
                )
        );

        expression.display();
        System.out.println("Result: " + expression.evaluate());

        CalculatorComponent divideExpression = new BinaryOperationNode(
                BinaryOperationNode.Operation.DIVIDE,
                new NumberNode(20),
                new NumberNode(4)
        );

        divideExpression.display();
        System.out.println("Divide Result: " + divideExpression.evaluate());

        System.out.println();
    }
}
