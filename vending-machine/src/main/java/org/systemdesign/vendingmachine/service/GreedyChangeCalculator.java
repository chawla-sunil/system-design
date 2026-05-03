package org.systemdesign.vendingmachine.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.systemdesign.vendingmachine.exception.ChangeNotAvailableException;
import org.systemdesign.vendingmachine.model.Coin;

public class GreedyChangeCalculator implements ChangeCalculator {

    @Override
    public Map<Coin, Integer> calculateChange(int amount, Map<Coin, Integer> availableCoins) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        if (amount == 0) {
            return Collections.emptyMap();
        }

        int remaining = amount;
        Map<Coin, Integer> result = new LinkedHashMap<>();

        for (Coin coin : coinsByDescendingValue()) {
            int coinValue = coin.value();
            if (coinValue > remaining) {
                continue;
            }

            int maxByAmount = remaining / coinValue;
            int available = availableCoins.getOrDefault(coin, 0);
            int toUse = Math.min(maxByAmount, available);

            if (toUse > 0) {
                result.put(coin, toUse);
                remaining -= toUse * coinValue;
            }
        }

        if (remaining != 0) {
            throw new ChangeNotAvailableException("Cannot return exact change for amount: " + amount);
        }

        return result;
    }

    private Coin[] coinsByDescendingValue() {
        Coin[] coins = Coin.values().clone();
        Arrays.sort(coins, Comparator.comparingInt(Coin::value).reversed());
        return coins;
    }
}
