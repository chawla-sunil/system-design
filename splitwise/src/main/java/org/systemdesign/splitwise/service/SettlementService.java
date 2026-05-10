package org.systemdesign.splitwise.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import org.systemdesign.splitwise.model.BalanceEntry;
import org.systemdesign.splitwise.model.Settlement;

public final class SettlementService {
    private final BalanceService balanceService;

    public SettlementService(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    public List<Settlement> simplify(List<BalanceEntry> balanceEntries) {
        Map<String, Long> netBalances = balanceService.calculateNetBalances(balanceEntries);
        PriorityQueue<UserAmount> creditors = new PriorityQueue<>(Comparator.comparingLong(UserAmount::amount).reversed());
        PriorityQueue<UserAmount> debtors = new PriorityQueue<>(Comparator.comparingLong(UserAmount::amount).reversed());

        for (Map.Entry<String, Long> entry : netBalances.entrySet()) {
            long amount = entry.getValue();
            if (amount > 0) {
                creditors.offer(new UserAmount(entry.getKey(), amount));
            } else if (amount < 0) {
                debtors.offer(new UserAmount(entry.getKey(), -amount));
            }
        }

        List<Settlement> result = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            UserAmount creditor = Objects.requireNonNull(creditors.poll());
            UserAmount debtor = Objects.requireNonNull(debtors.poll());
            long settledAmount = Math.min(creditor.amount(), debtor.amount());
            result.add(new Settlement(debtor.userId(), creditor.userId(), settledAmount));

            if (creditor.amount() > settledAmount) {
                creditors.offer(new UserAmount(creditor.userId(), creditor.amount() - settledAmount));
            }
            if (debtor.amount() > settledAmount) {
                debtors.offer(new UserAmount(debtor.userId(), debtor.amount() - settledAmount));
            }
        }
        return result;
    }

    private record UserAmount(String userId, long amount) {
    }
}

