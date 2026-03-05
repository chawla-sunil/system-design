package org.designpatterns.behavioral.strategy;

import java.util.ArrayList;
import java.util.List;

public class QuickSortStrategy implements SortStrategy {
    @Override
    public List<Integer> sort(List<Integer> data) {
        List<Integer> result = new ArrayList<>(data);
        quickSort(result, 0, result.size() - 1);
        return result;
    }

    private void quickSort(List<Integer> list, int low, int high) {
        if (low < high) {
            int pi = partition(list, low, high);
            quickSort(list, low, pi - 1);
            quickSort(list, pi + 1, high);
        }
    }

    private int partition(List<Integer> list, int low, int high) {
        int pivot = list.get(high);
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (list.get(j) <= pivot) {
                i++;
                Integer temp = list.get(i);
                list.set(i, list.get(j));
                list.set(j, temp);
            }
        }
        Integer temp = list.get(i + 1);
        list.set(i + 1, list.get(high));
        list.set(high, temp);
        return i + 1;
    }

    @Override
    public String getName() { return "Quick Sort"; }
}
