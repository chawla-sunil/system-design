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
    private static final Comparator<UserAmount> USER_AMOUNT_DESC = Comparator
        .comparingLong(UserAmount::amount).reversed()
        .thenComparing(UserAmount::userId);

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

    /**
     * Alternative settle-up algorithm using one-time sorting + two pointers.
     * Time complexity: O(n log n), where n is the number of users with non-zero net balance.
     */
    // simplify method 2, instead of heap (PriorityQueue), we can do one-time sorting and use two pointers to settle up.
    // first one is better
    public List<Settlement> simplifyTwoPointer(List<BalanceEntry> balanceEntries) {
        Map<String, Long> netBalances = balanceService.calculateNetBalances(balanceEntries);
        List<UserAmount> creditors = new ArrayList<>();
        List<UserAmount> debtors = new ArrayList<>();

        for (Map.Entry<String, Long> entry : netBalances.entrySet()) {
            long amount = entry.getValue();
            if (amount > 0) {
                creditors.add(new UserAmount(entry.getKey(), amount));
            } else if (amount < 0) {
                debtors.add(new UserAmount(entry.getKey(), -amount));
            }
        }

        creditors.sort(USER_AMOUNT_DESC);
        debtors.sort(USER_AMOUNT_DESC);

        int creditorIndex = 0;
        int debtorIndex = 0;
        List<Settlement> result = new ArrayList<>();

        while (creditorIndex < creditors.size() && debtorIndex < debtors.size()) {
            UserAmount creditor = creditors.get(creditorIndex);
            UserAmount debtor = debtors.get(debtorIndex);

            long settledAmount = Math.min(creditor.amount(), debtor.amount());
            result.add(new Settlement(debtor.userId(), creditor.userId(), settledAmount));

            long creditorRemaining = creditor.amount() - settledAmount;
            long debtorRemaining = debtor.amount() - settledAmount;

            if (creditorRemaining == 0) {
                creditorIndex++;
            } else {
                creditors.set(creditorIndex, new UserAmount(creditor.userId(), creditorRemaining));
            }

            if (debtorRemaining == 0) {
                debtorIndex++;
            } else {
                debtors.set(debtorIndex, new UserAmount(debtor.userId(), debtorRemaining));
            }
        }

        return result;
    }

    private record UserAmount(String userId, long amount) {
    }
}

