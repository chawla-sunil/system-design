package org.systemdesign.vendingmachine;

import java.util.Map;
import org.systemdesign.vendingmachine.model.Coin;
import org.systemdesign.vendingmachine.model.Product;
import org.systemdesign.vendingmachine.model.VendResult;

public class Main {

    public static void main(String[] args) {
        // Made by Auto Model
        VendingMachine machine = new VendingMachine();

        machine.loadSlot("A1", new Product("P1", "Coke", 35), 5);
        machine.loadSlot("A2", new Product("P2", "Chips", 25), 2);
        machine.loadCoins(Map.of(Coin.QUARTER, 10, Coin.DIME, 10, Coin.NICKEL, 10));

        machine.insertCoin(Coin.QUARTER);
        machine.insertCoin(Coin.QUARTER);

        VendResult result = machine.purchase("A1");

        System.out.println("Dispensed: " + result.product().name());
        System.out.println("Paid: " + result.paidAmount() + " cents");
        System.out.println("Change returned: " + result.changeAmount() + " cents -> " + result.changeCoins());
    }
}