package org.designpatterns.behavioral.templatemethod;

import java.util.List;

public abstract class DataProcessor {

    // Template Method - defines the algorithm skeleton
    public final void process() {
        System.out.println("--- Processing with " + getProcessorName() + " ---");
        List<String> rawData = readData();
        List<String> parsed = parseData(rawData);
        List<String> processed = processData(parsed);
        saveData(processed);
        System.out.println("--- Done ---\n");
    }

    // Steps to be implemented by subclasses
    protected abstract List<String> readData();
    protected abstract List<String> parseData(List<String> rawData);
    protected abstract String getProcessorName();

    // Common step with default implementation (hook - can be overridden)
    protected List<String> processData(List<String> data) {
        System.out.println("  [Process] Validating " + data.size() + " records (default)");
        return data;
    }

    // Common step shared by all subclasses
    protected void saveData(List<String> data) {
        System.out.println("  [Save] Saving " + data.size() + " records to database");
    }
}
