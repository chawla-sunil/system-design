package org.designpatterns.structural.composite;

public class CompositeDemo {
    public static void run() {
        System.out.println("=== COMPOSITE PATTERN DEMO ===\n");

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

        System.out.println();
    }
}
