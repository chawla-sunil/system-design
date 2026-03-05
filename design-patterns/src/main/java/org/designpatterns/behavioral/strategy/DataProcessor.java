package org.designpatterns.behavioral.strategy;

import java.util.List;

/**
 * Context class that uses a SortStrategy.
 * The strategy can be swapped at runtime.
 */
public class DataProcessor {
    private SortStrategy strategy;

    public DataProcessor(SortStrategy strategy) {
        this.strategy = strategy;
    }

    public void setStrategy(SortStrategy strategy) {
        this.strategy = strategy;
    }

    public List<Integer> process(List<Integer> data) {
        System.out.println("  Using strategy: " + strategy.getName());
        List<Integer> sorted = strategy.sort(data);
        System.out.println("  Result: " + sorted);
        return sorted;
    }
}
