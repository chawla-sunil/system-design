package org.systemdesign.vendingmachine.service;

import java.util.Map;
import org.systemdesign.vendingmachine.model.Coin;

public interface ChangeCalculator {
    Map<Coin, Integer> calculateChange(int amount, Map<Coin, Integer> availableCoins);
}

