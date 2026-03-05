package org.designpatterns.behavioral.templatemethod;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CsvDataProcessor extends DataProcessor {

    @Override
    protected List<String> readData() {
        System.out.println("  [Read] Reading CSV file...");
        return List.of("Alice,Engineering,95000", "Bob,Marketing,72000", "Charlie,HR,68000");
    }

    @Override
    protected List<String> parseData(List<String> rawData) {
        System.out.println("  [Parse] Parsing CSV rows...");
        return rawData.stream()
                .map(row -> {
                    String[] fields = row.split(",");
                    return "Name=" + fields[0] + ", Dept=" + fields[1] + ", Salary=" + fields[2];
                })
                .collect(Collectors.toList());
    }

    @Override
    protected String getProcessorName() { return "CSV Processor"; }
}
