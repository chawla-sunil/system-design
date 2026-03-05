package org.designpatterns.behavioral.templatemethod;

import java.util.List;
import java.util.stream.Collectors;

public class JsonDataProcessor extends DataProcessor {

    @Override
    protected List<String> readData() {
        System.out.println("  [Read] Reading JSON file...");
        return List.of("{\"name\":\"Alice\"}", "{\"name\":\"Bob\"}", "{\"name\":\"Charlie\"}");
    }

    @Override
    protected List<String> parseData(List<String> rawData) {
        System.out.println("  [Parse] Parsing JSON objects...");
        return rawData.stream()
                .map(json -> json.replace("{", "").replace("}", "").replace("\"", ""))
                .collect(Collectors.toList());
    }

    // Override the hook method
    @Override
    protected List<String> processData(List<String> data) {
        System.out.println("  [Process] JSON-specific validation and transformation");
        return data.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }

    @Override
    protected String getProcessorName() { return "JSON Processor"; }
}
