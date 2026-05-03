package org.systemdesign.vendingmachine;

import java.util.Map;
import org.systemdesign.vendingmachine.exception.ChangeNotAvailableException;
import org.systemdesign.vendingmachine.exception.InsufficientFundsException;
import org.systemdesign.vendingmachine.exception.OutOfStockException;
import org.systemdesign.vendingmachine.model.Coin;
import org.systemdesign.vendingmachine.model.Product;
import org.systemdesign.vendingmachine.model.VendResult;

public final class VendingMachineTest {

    public static void main(String[] args) {
        shouldDispenseProductAndReturnChange();
        shouldThrowWhenFundsAreInsufficient();
        shouldThrowWhenProductIsOutOfStock();
        shouldRefundInsertedCoinsOnCancel();
        shouldThrowWhenExactChangeCannotBeReturned();
        System.out.println("All vending machine tests passed.");
    }

    private static void shouldDispenseProductAndReturnChange() {
        VendingMachine machine = baseMachine();
        machine.insertCoin(Coin.QUARTER);
        machine.insertCoin(Coin.QUARTER);

        VendResult result = machine.purchase("A1");

        assertEquals("Coke", result.product().name(), "product name");
        assertEquals(50, result.paidAmount(), "paid amount");
        assertEquals(15, result.changeAmount(), "change amount");
        assertEquals(1, result.changeCoins().get(Coin.DIME), "dime count");
        assertEquals(1, result.changeCoins().get(Coin.NICKEL), "nickel count");
        assertEquals(1, machine.stock("A1"), "stock after dispense");
        assertEquals(VendingMachineState.IDLE, machine.state(), "state after purchase");
    }

    private static void shouldThrowWhenFundsAreInsufficient() {
        VendingMachine machine = baseMachine();
        machine.insertCoin(Coin.QUARTER);
        assertThrows(InsufficientFundsException.class, () -> machine.purchase("A1"), "insufficient funds");
    }

    private static void shouldThrowWhenProductIsOutOfStock() {
        VendingMachine machine = baseMachine();

        machine.insertCoin(Coin.QUARTER);
        machine.insertCoin(Coin.DIME);
        machine.purchase("A1");

        machine.insertCoin(Coin.QUARTER);
        machine.insertCoin(Coin.DIME);
        machine.purchase("A1");

        machine.insertCoin(Coin.QUARTER);
        machine.insertCoin(Coin.DIME);
        assertThrows(OutOfStockException.class, () -> machine.purchase("A1"), "out of stock");
    }

    private static void shouldRefundInsertedCoinsOnCancel() {
        VendingMachine machine = baseMachine();
        machine.insertCoin(Coin.QUARTER);
        machine.insertCoin(Coin.DIME);

        Map<Coin, Integer> refunded = machine.cancelAndRefund();

        assertEquals(1, refunded.get(Coin.QUARTER), "refund quarter");
        assertEquals(1, refunded.get(Coin.DIME), "refund dime");
        assertEquals(0, machine.currentBalance(), "balance after refund");
        assertEquals(VendingMachineState.IDLE, machine.state(), "state after refund");
    }

    private static void shouldThrowWhenExactChangeCannotBeReturned() {
        VendingMachine machine = new VendingMachine();
        machine.loadSlot("A1", new Product("P1", "Coke", 35), 1);
        machine.loadCoins(Map.of(Coin.QUARTER, 10));
        machine.insertCoin(Coin.QUARTER);
        machine.insertCoin(Coin.QUARTER);

        assertThrows(ChangeNotAvailableException.class, () -> machine.purchase("A1"), "exact change unavailable");
    }

    private static VendingMachine baseMachine() {
        VendingMachine machine = new VendingMachine();
        machine.loadSlot("A1", new Product("P1", "Coke", 35), 2);
        machine.loadCoins(Map.of(Coin.QUARTER, 2, Coin.DIME, 5, Coin.NICKEL, 5));
        return machine;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable call, String message) {
        try {
            call.run();
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                return;
            }
            throw new IllegalStateException(message + ": expected=" + expected.getSimpleName()
                + ", actual=" + t.getClass().getSimpleName(), t);
        }

        throw new IllegalStateException(message + ": expected exception " + expected.getSimpleName());
    }
}
