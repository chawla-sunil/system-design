package org.systemdesign.atm.service;

import org.systemdesign.atm.exception.AuthenticationException;
import org.systemdesign.atm.exception.CashUnavailableException;
import org.systemdesign.atm.model.Account;
import org.systemdesign.atm.model.Card;
import org.systemdesign.atm.model.CashInventory;
import org.systemdesign.atm.strategy.GreedyCashDispenseStrategy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency-free smoke tests that can be run with `java -ea ...AtmServiceTest`.
 */
public class AtmServiceTest {

    public static void main(String[] args) {
        shouldWithdrawAndDebitBalance();
        shouldDepositAndCreditBalance();
        shouldLockAccountOnRepeatedInvalidPin();
        shouldRollbackWhenExactDispenseIsNotPossible();
        System.out.println("All ATM smoke tests passed.");
    }

    private static void shouldWithdrawAndDebitBalance() {
        AtmService atmService = setupAtmService(BigDecimal.valueOf(5000), Map.of(500, 5, 200, 10, 100, 5));
        atmService.insertCard("CARD-1");
        atmService.enterPin("1234");

        Map<Integer, Integer> dispensed = atmService.withdraw(1200);
        assert dispensed.equals(Map.of(500, 2, 200, 1));
        assert atmService.checkBalance().compareTo(BigDecimal.valueOf(3800)) == 0;
    }

    private static void shouldDepositAndCreditBalance() {
        AtmService atmService = setupAtmService(BigDecimal.valueOf(5000), Map.of(500, 5, 200, 10, 100, 5));
        atmService.insertCard("CARD-1");
        atmService.enterPin("1234");

        BigDecimal latestBalance = atmService.deposit(Map.of(500, 1, 200, 2));
        assert latestBalance.compareTo(BigDecimal.valueOf(5900)) == 0;
    }

    private static void shouldLockAccountOnRepeatedInvalidPin() {
        Account account = new Account("ACC-1", "Tester", "1234", BigDecimal.valueOf(5000));
        Card card = new Card("CARD-1", "ACC-1");
        BankService bankService = new InMemoryBankService(List.of(account), List.of(card));

        AtmMachine atmMachine = new AtmMachine(
                "ATM-1",
                new CashInventory(Map.of(500, 5, 200, 10, 100, 5)),
                Set.of(100, 200, 500),
                3,
                10_000
        );

        AtmService atmService = new AtmService(atmMachine, bankService, new GreedyCashDispenseStrategy(), Clock.systemUTC());
        atmService.insertCard("CARD-1");

        try {
            atmService.enterPin("0000");
            throw new AssertionError("Expected invalid PIN failure");
        } catch (AuthenticationException ignored) {
        }
        try {
            atmService.enterPin("1111");
            throw new AssertionError("Expected invalid PIN failure");
        } catch (AuthenticationException ignored) {
        }
        try {
            atmService.enterPin("2222");
            throw new AssertionError("Expected account lock on third invalid PIN");
        } catch (AuthenticationException ignored) {
        }

        assert account.isLocked();
    }

    private static void shouldRollbackWhenExactDispenseIsNotPossible() {
        Account account = new Account("ACC-2", "Tester", "1234", BigDecimal.valueOf(3000));
        Card card = new Card("CARD-2", "ACC-2");
        BankService bankService = new InMemoryBankService(List.of(account), List.of(card));

        AtmMachine constrainedMachine = new AtmMachine(
                "ATM-2",
                new CashInventory(Map.of(500, 1)),
                Set.of(100, 200, 500),
                3,
                10_000
        );

        AtmService atmService = new AtmService(constrainedMachine, bankService, new GreedyCashDispenseStrategy(), Clock.systemUTC());
        atmService.insertCard("CARD-2");
        atmService.enterPin("1234");

        try {
            atmService.withdraw(1000);
            throw new AssertionError("Expected cash unavailable exception");
        } catch (CashUnavailableException ignored) {
        }

        assert atmService.checkBalance().compareTo(BigDecimal.valueOf(3000)) == 0;
    }

    private static AtmService setupAtmService(BigDecimal initialBalance, Map<Integer, Integer> initialNotes) {
        Account account = new Account("ACC-1", "Tester", "1234", initialBalance);
        Card card = new Card("CARD-1", "ACC-1");

        BankService bankService = new InMemoryBankService(List.of(account), List.of(card));
        AtmMachine atmMachine = new AtmMachine(
                "ATM-1",
                new CashInventory(initialNotes),
                Set.of(100, 200, 500),
                3,
                10_000
        );

        return new AtmService(atmMachine, bankService, new GreedyCashDispenseStrategy(), Clock.systemUTC());
    }
}
