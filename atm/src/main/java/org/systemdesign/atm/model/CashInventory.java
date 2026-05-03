package org.systemdesign.atm.model;

import org.systemdesign.atm.exception.CashUnavailableException;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class CashInventory {

    private final NavigableMap<Integer, Integer> notes;

    public CashInventory(Map<Integer, Integer> initialNotes) {
        this.notes = new TreeMap<>(Collections.reverseOrder());
        addNotes(initialNotes);
    }

    public synchronized void addNotes(Map<Integer, Integer> notesToAdd) {
        notesToAdd.forEach((denomination, count) -> {
            if (denomination <= 0 || count <= 0) {
                throw new IllegalArgumentException("Denomination and count must be positive.");
            }
            notes.merge(denomination, count, Integer::sum);
        });
    }

    public synchronized void removeNotes(Map<Integer, Integer> notesToRemove) {
        for (Map.Entry<Integer, Integer> entry : notesToRemove.entrySet()) {
            int denomination = entry.getKey();
            int count = entry.getValue();
            int current = notes.getOrDefault(denomination, 0);
            if (count > current) {
                throw new CashUnavailableException("ATM has insufficient notes for denomination: " + denomination);
            }
        }

        for (Map.Entry<Integer, Integer> entry : notesToRemove.entrySet()) {
            int denomination = entry.getKey();
            int updated = notes.getOrDefault(denomination, 0) - entry.getValue();
            if (updated == 0) {
                notes.remove(denomination);
            } else {
                notes.put(denomination, updated);
            }
        }
    }

    public synchronized NavigableMap<Integer, Integer> snapshot() {
        return new TreeMap<>(notes);
    }

    public synchronized int totalCash() {
        return notes.entrySet().stream().mapToInt(entry -> entry.getKey() * entry.getValue()).sum();
    }
}

