package org.systemdesign.splitwise.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.systemdesign.splitwise.model.BalanceEntry;
import org.systemdesign.splitwise.model.Expense;
import org.systemdesign.splitwise.model.ExpenseShare;

public final class BalanceService {

    public List<BalanceEntry> calculateBalances(Collection<Expense> expenses) {
        Map<String, Map<String, Long>> ledger = new LinkedHashMap<>();
        for (Expense expense : expenses) {
            for (ExpenseShare share : expense.shares()) {
                if (share.amountInCents() == 0 || share.userId().equals(expense.paidByUserId())) {
                    continue;
                }
                addDebt(ledger, share.userId(), expense.paidByUserId(), share.amountInCents());
            }
        }

        List<BalanceEntry> result = new ArrayList<>();
        ledger.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(debtorEntry -> debtorEntry.getValue().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(creditorEntry -> result.add(new BalanceEntry(
                    debtorEntry.getKey(),
                    creditorEntry.getKey(),
                    creditorEntry.getValue()
                ))));
        result.sort(Comparator
            .comparing(BalanceEntry::debtorUserId)
            .thenComparing(BalanceEntry::creditorUserId));
        return result;
    }

    public Map<String, Long> calculateNetBalances(Collection<BalanceEntry> balances) {
        Map<String, Long> netBalances = new LinkedHashMap<>();
        for (BalanceEntry balance : balances) {
            netBalances.merge(balance.debtorUserId(), -balance.amountInCents(), Long::sum);
            netBalances.merge(balance.creditorUserId(), balance.amountInCents(), Long::sum);
        }
        return netBalances;
    }

    private void addDebt(Map<String, Map<String, Long>> ledger, String debtor, String creditor, long amountInCents) {
        long reverseAmount = ledger.getOrDefault(creditor, Map.of()).getOrDefault(debtor, 0L);
        if (reverseAmount >= amountInCents) {
            writeAmount(ledger, creditor, debtor, reverseAmount - amountInCents);
            return;
        }

        if (reverseAmount > 0) {
            writeAmount(ledger, creditor, debtor, 0L);
            amountInCents -= reverseAmount;
        }
        long currentAmount = ledger.getOrDefault(debtor, Map.of()).getOrDefault(creditor, 0L);
        writeAmount(ledger, debtor, creditor, currentAmount + amountInCents);
    }

    private void writeAmount(Map<String, Map<String, Long>> ledger, String from, String to, long amount) {
        if (amount == 0) { // no need of this condition actually
            Map<String, Long> outgoing = ledger.get(from);
            if (outgoing == null) {
                return;
            }
            outgoing.remove(to);
            if (outgoing.isEmpty()) {
                ledger.remove(from);
            }
            return;
        }

        ledger.computeIfAbsent(from, ignored -> new LinkedHashMap<>()).put(to, amount);
    }
}

