package org.designpatterns.behavioral.strategy;

import java.util.List;

public class StrategyDemo {
    public static void run() {
        System.out.println("=== STRATEGY PATTERN DEMO ===\n");

        List<Integer> data = List.of(64, 34, 25, 12, 22, 11, 90);
        System.out.println("Original data: " + data);

        // Use bubble sort for small datasets
        System.out.println("\n--- Small dataset: Bubble Sort ---");
        DataProcessor processor = new DataProcessor(new BubbleSortStrategy());
        processor.process(data);

        // Switch to quick sort at runtime for larger datasets
        System.out.println("\n--- Switching to Quick Sort at runtime ---");
        processor.setStrategy(new QuickSortStrategy());
        processor.process(data);

        // Larger dataset
        System.out.println("\n--- Larger dataset: Quick Sort ---");
        List<Integer> largeData = List.of(38, 27, 43, 3, 9, 82, 10, 55, 1, 72);
        processor.process(largeData);

        System.out.println();
    }
}
