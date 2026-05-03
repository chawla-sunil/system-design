package org.systemdesign.atm.strategy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;

public class GreedyCashDispenseStrategy implements CashDispenseStrategy {

    @Override
    public Map<Integer, Integer> dispense(int amount, NavigableMap<Integer, Integer> availableNotes) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount should be positive.");
        }

        int remaining = amount;
        Map<Integer, Integer> result = new LinkedHashMap<>();

        for (Map.Entry<Integer, Integer> entry : availableNotes.entrySet()) {
            int denomination = entry.getKey();
            int available = entry.getValue();
            int notesNeeded = remaining / denomination;
            int notesToUse = Math.min(notesNeeded, available);

            if (notesToUse > 0) {
                result.put(denomination, notesToUse);
                remaining -= denomination * notesToUse;
            }
        }

        if (remaining != 0) {
            return Map.of();
        }
        return result;
    }
}

