package org.systemdesign.atm;

import org.systemdesign.atm.model.Account;
import org.systemdesign.atm.model.Card;
import org.systemdesign.atm.model.CashInventory;
import org.systemdesign.atm.model.TransactionRecord;
import org.systemdesign.atm.service.AtmMachine;
import org.systemdesign.atm.service.AtmService;
import org.systemdesign.atm.service.BankService;
import org.systemdesign.atm.service.InMemoryBankService;
import org.systemdesign.atm.strategy.GreedyCashDispenseStrategy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        // Made by Auto Model
        AtmService atmService = getAtmService();

        System.out.println("--- ATM Session Started ---");
        atmService.insertCard("4000-1234-5678-0001");
        atmService.enterPin("1234");

        BigDecimal beforeWithdraw = atmService.checkBalance();
        System.out.println("Balance before withdrawal: " + beforeWithdraw);

        Map<Integer, Integer> dispensed = atmService.withdraw(2700);
        System.out.println("Dispensed notes: " + dispensed);

        BigDecimal afterWithdraw = atmService.checkBalance();
        System.out.println("Balance after withdrawal: " + afterWithdraw);

        BigDecimal afterDeposit = atmService.deposit(Map.of(500, 2, 200, 3));
        System.out.println("Balance after deposit: " + afterDeposit);

        List<TransactionRecord> statement = atmService.miniStatement(5);
        System.out.println("Recent transactions:");
        statement.forEach(System.out::println);

        atmService.ejectCard();
        System.out.println("--- ATM Session Ended ---");
    }

    private static AtmService getAtmService() {
        Account account = new Account("ACC-101", "Sunil", "1234", BigDecimal.valueOf(12_000));
        Card card = new Card("4000-1234-5678-0001", "ACC-101");

        BankService bankService = new InMemoryBankService(List.of(account), List.of(card));
        AtmMachine atmMachine = new AtmMachine(
                "ATM-BLR-01",
                new CashInventory(Map.of(500, 20, 200, 30, 100, 40)),
                Set.of(100, 200, 500),
                3,
                10_000
        );

        return new AtmService(atmMachine, bankService, new GreedyCashDispenseStrategy(), Clock.systemUTC());
    }
}