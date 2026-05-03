package org.systemdesign.atm.strategy;

import java.util.Map;
import java.util.NavigableMap;

public interface CashDispenseStrategy {

    Map<Integer, Integer> dispense(int amount, NavigableMap<Integer, Integer> availableNotes);
}

