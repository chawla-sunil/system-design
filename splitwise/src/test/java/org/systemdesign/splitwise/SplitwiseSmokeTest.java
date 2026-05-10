package org.systemdesign.splitwise;

import java.util.List;
import org.systemdesign.splitwise.exception.ValidationException;
import org.systemdesign.splitwise.model.BalanceEntry;
import org.systemdesign.splitwise.model.CreateExpenseRequest;
import org.systemdesign.splitwise.model.SplitInput;
import org.systemdesign.splitwise.model.SplitType;

public final class SplitwiseSmokeTest {

    public static void main(String[] args) {
        shouldSplitEqualExpenseAndTrackBalance();
        shouldSplitExactExpense();
        shouldSplitPercentageExpenseWithRounding();
        shouldSimplifyCrossDebts();
        shouldRejectInvalidPercentages();
        System.out.println("All Splitwise smoke tests passed.");
    }

    private static void shouldSplitEqualExpenseAndTrackBalance() {
        SplitwiseService service = basicService();
        service.addExpense(new CreateExpenseRequest(
            "Lunch",
            "u1",
            900,
            List.of("u1", "u2", "u3"),
            SplitType.EQUAL,
            List.of(),
            null
        ));

        List<BalanceEntry> balances = service.getAllBalances();
        assertEquals(2, balances.size(), "equal split balance size");
        assertContainsBalance(balances, "u2", "u1", 300);
        assertContainsBalance(balances, "u3", "u1", 300);
    }

    private static void shouldSplitExactExpense() {
        SplitwiseService service = basicService();
        service.addExpense(new CreateExpenseRequest(
            "Cab",
            "u2",
            1_000,
            List.of("u1", "u2", "u3"),
            SplitType.EXACT,
            List.of(
                new SplitInput("u1", 250),
                new SplitInput("u2", 250),
                new SplitInput("u3", 500)
            ),
            null
        ));

        List<BalanceEntry> balances = service.getAllBalances();
        assertEquals(2, balances.size(), "exact split balance size");
        assertContainsBalance(balances, "u1", "u2", 250);
        assertContainsBalance(balances, "u3", "u2", 500);
    }

    private static void shouldSplitPercentageExpenseWithRounding() {
        SplitwiseService service = basicService();
        service.addExpense(new CreateExpenseRequest(
            "Tickets",
            "u3",
            1_001,
            List.of("u1", "u2", "u3"),
            SplitType.PERCENTAGE,
            List.of(
                new SplitInput("u1", 50),
                new SplitInput("u2", 25),
                new SplitInput("u3", 25)
            ),
            null
        ));

        List<BalanceEntry> balances = service.getAllBalances();
        assertContainsBalance(balances, "u1", "u3", 501);
        assertContainsBalance(balances, "u2", "u3", 250);
    }

    private static void shouldSimplifyCrossDebts() {
        SplitwiseService service = basicService();
        service.createGroup("g1", "Trip", List.of("u1", "u2", "u3"));

        service.addExpense(new CreateExpenseRequest(
            "Dinner",
            "u1",
            900,
            List.of("u1", "u2", "u3"),
            SplitType.EQUAL,
            List.of(),
            "g1"
        ));
        service.addExpense(new CreateExpenseRequest(
            "Snacks",
            "u2",
            600,
            List.of("u1", "u2", "u3"),
            SplitType.EQUAL,
            List.of(),
            "g1"
        ));

        List<String> settlements = service.describeGroupSettlementPlan("g1");
        assertEquals(2, settlements.size(), "settlement count");
        assertTrue(settlements.contains("Charu pays Aman ₹4.00"), "expected payment from Charu to Aman");
        assertTrue(settlements.contains("Charu pays Bhavna ₹1.00"), "expected payment from Charu to Bhavna");
    }

    private static void shouldRejectInvalidPercentages() {
        SplitwiseService service = basicService();
        assertThrows(ValidationException.class, () -> service.addExpense(new CreateExpenseRequest(
            "Bad split",
            "u1",
            1_000,
            List.of("u1", "u2"),
            SplitType.PERCENTAGE,
            List.of(
                new SplitInput("u1", 70),
                new SplitInput("u2", 20)
            ),
            null
        )), "invalid percentage split");
    }

    private static SplitwiseService basicService() {
        SplitwiseService service = new SplitwiseService();
        service.addUser("u1", "Aman", "aman@example.com");
        service.addUser("u2", "Bhavna", "bhavna@example.com");
        service.addUser("u3", "Charu", "charu@example.com");
        return service;
    }

    private static void assertContainsBalance(List<BalanceEntry> balances, String debtor, String creditor, long amount) {
        boolean found = balances.stream().anyMatch(balance -> balance.debtorUserId().equals(debtor)
            && balance.creditorUserId().equals(creditor)
            && balance.amountInCents() == amount);
        if (!found) {
            throw new IllegalStateException("Expected balance not found: " + debtor + " -> " + creditor + " = " + amount);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable call, String message) {
        try {
            call.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new IllegalStateException(message + ": expected=" + expected.getSimpleName()
                + ", actual=" + throwable.getClass().getSimpleName(), throwable);
        }
        throw new IllegalStateException(message + ": expected exception " + expected.getSimpleName());
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}

