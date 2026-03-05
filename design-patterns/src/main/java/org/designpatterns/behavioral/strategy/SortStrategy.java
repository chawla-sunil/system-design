package org.designpatterns.behavioral.strategy;

import java.util.List;

/**
 * Strategy Pattern - Sorting Strategy Interface
 *
 * Defines a family of algorithms. Each strategy encapsulates a sorting algorithm.
 */
public interface SortStrategy {
    List<Integer> sort(List<Integer> data);
    String getName();
}
