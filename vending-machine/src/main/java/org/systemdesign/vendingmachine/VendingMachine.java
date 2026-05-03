package org.systemdesign.vendingmachine;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.systemdesign.vendingmachine.exception.InsufficientFundsException;
import org.systemdesign.vendingmachine.exception.InvalidSlotSelectionException;
import org.systemdesign.vendingmachine.exception.OutOfStockException;
import org.systemdesign.vendingmachine.model.Coin;
import org.systemdesign.vendingmachine.model.Product;
import org.systemdesign.vendingmachine.model.Slot;
import org.systemdesign.vendingmachine.model.VendResult;
import org.systemdesign.vendingmachine.service.ChangeCalculator;
import org.systemdesign.vendingmachine.service.GreedyChangeCalculator;

public class VendingMachine {
    private final Map<String, Slot> slots;
    private final Map<Coin, Integer> coinInventory;
    private final Map<Coin, Integer> insertedCoins;
    private final ChangeCalculator changeCalculator;

    private VendingMachineState state;

    public VendingMachine() {
        this(new GreedyChangeCalculator());
    }

    public VendingMachine(ChangeCalculator changeCalculator) {
        this.slots = new HashMap<>();
        this.coinInventory = new EnumMap<>(Coin.class);
        this.insertedCoins = new EnumMap<>(Coin.class);
        this.changeCalculator = changeCalculator;
        this.state = VendingMachineState.IDLE;
    }

    public synchronized void loadSlot(String slotCode, Product product, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        Slot existingSlot = slots.get(slotCode);
        if (existingSlot == null) {
            slots.put(slotCode, new Slot(slotCode, product, quantity));
            return;
        }

        if (!existingSlot.product().id().equals(product.id())) {
            throw new IllegalArgumentException("Cannot load a different product in an existing slot");
        }

        existingSlot.add(quantity);
    }

    public synchronized void loadCoins(Map<Coin, Integer> coinsToAdd) {
        for (Map.Entry<Coin, Integer> entry : coinsToAdd.entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Coin count cannot be negative");
            }
            coinInventory.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    public synchronized void insertCoin(Coin coin) {
        assertMachineOperational();
        insertedCoins.merge(coin, 1, Integer::sum);
        state = VendingMachineState.ACCEPTING_MONEY;
    }

    public synchronized int currentBalance() {
        return sum(insertedCoins);
    }

    public synchronized VendResult purchase(String slotCode) {
        assertMachineOperational();

        Slot slot = slots.get(slotCode);
        if (slot == null) {
            throw new InvalidSlotSelectionException("Invalid slot code: " + slotCode);
        }
        if (slot.isOutOfStock()) {
            throw new OutOfStockException("Selected product is out of stock");
        }

        int paidAmount = currentBalance();
        int price = slot.product().priceInCents();

        if (paidAmount < price) {
            throw new InsufficientFundsException("Insert " + (price - paidAmount) + " more cents");
        }

        int changeAmount = paidAmount - price;
        Map<Coin, Integer> availableForChange = merge(coinInventory, insertedCoins);
        Map<Coin, Integer> changeCoins = changeCalculator.calculateChange(changeAmount, availableForChange);

        state = VendingMachineState.DISPENSING;
        slot.dispenseOne();

        loadCoins(insertedCoins);
        deductCoins(changeCoins, coinInventory);

        insertedCoins.clear();
        state = VendingMachineState.IDLE;

        return new VendResult(slot.product(), paidAmount, changeAmount, Collections.unmodifiableMap(changeCoins));
    }

    public synchronized Map<Coin, Integer> cancelAndRefund() {
        Map<Coin, Integer> refund = new EnumMap<>(insertedCoins);
        insertedCoins.clear();
        state = VendingMachineState.IDLE;
        return refund;
    }

    public synchronized int stock(String slotCode) {
        Slot slot = slots.get(slotCode);
        if (slot == null) {
            return 0;
        }
        return slot.quantity();
    }

    public synchronized VendingMachineState state() {
        return state;
    }

    private void assertMachineOperational() {
        if (state == VendingMachineState.OUT_OF_SERVICE) {
            throw new IllegalStateException("Machine is out of service");
        }
    }

    private int sum(Map<Coin, Integer> coins) {
        return coins.entrySet().stream().mapToInt(entry -> entry.getKey().value() * entry.getValue()).sum();
    }

    private Map<Coin, Integer> merge(Map<Coin, Integer> base, Map<Coin, Integer> delta) {
        Map<Coin, Integer> merged = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            merged.put(coin, base.getOrDefault(coin, 0) + delta.getOrDefault(coin, 0));
        }
        return merged;
    }

    private void deductCoins(Map<Coin, Integer> coinsToDeduct, Map<Coin, Integer> source) {
        for (Map.Entry<Coin, Integer> entry : coinsToDeduct.entrySet()) {
            int existing = source.getOrDefault(entry.getKey(), 0);
            int updated = existing - entry.getValue();
            if (updated < 0) {
                throw new IllegalStateException("Cannot deduct more coins than available");
            }
            source.put(entry.getKey(), updated);
        }
    }
}

