package org.designpatterns.behavioral.templatemethod;

public class TemplateMethodDemo {
    public static void run() {
        System.out.println("=== TEMPLATE METHOD PATTERN DEMO ===\n");

        // Same algorithm structure, different implementations
        DataProcessor csvProcessor = new CsvDataProcessor();
        csvProcessor.process();

        DataProcessor jsonProcessor = new JsonDataProcessor();
        jsonProcessor.process();
    }
}
