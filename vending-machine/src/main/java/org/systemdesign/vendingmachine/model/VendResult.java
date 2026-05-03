package org.systemdesign.vendingmachine.model;

import java.util.Map;

public record VendResult(Product product, int paidAmount, int changeAmount, Map<Coin, Integer> changeCoins) {
}

