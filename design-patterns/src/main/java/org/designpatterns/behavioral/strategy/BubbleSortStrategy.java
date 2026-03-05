package org.designpatterns.behavioral.strategy;

import java.util.ArrayList;
import java.util.List;

public class BubbleSortStrategy implements SortStrategy {
    @Override
    public List<Integer> sort(List<Integer> data) {
        List<Integer> result = new ArrayList<>(data);
        int n = result.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (result.get(j).compareTo(result.get(j + 1)) > 0) {
                    Integer temp = result.get(j);
                    result.set(j, result.get(j + 1));
                    result.set(j + 1, temp);
                }
            }
        }
        return result;
    }

    @Override
    public String getName() { return "Bubble Sort"; }
}
