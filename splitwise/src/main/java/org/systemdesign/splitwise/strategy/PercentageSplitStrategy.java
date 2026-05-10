package org.systemdesign.splitwise.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.systemdesign.splitwise.exception.ValidationException;
import org.systemdesign.splitwise.model.ExpenseShare;
import org.systemdesign.splitwise.model.SplitInput;

public final class PercentageSplitStrategy implements SplitStrategy {
    private static final long HUNDRED = 100L;

    @Override
    public List<ExpenseShare> split(long totalAmountInCents, List<String> participants, List<SplitInput> splitInputs) {
        if (splitInputs.size() != participants.size()) {
            throw new ValidationException("percentage split requires one percentage per participant");
        }

        Map<String, Long> percentagesByUser = new LinkedHashMap<>();
        long totalPercentage = 0;
        for (SplitInput input : splitInputs) {
            if (input.value() < 0 || input.value() > HUNDRED) {
                throw new ValidationException("percentage must be between 0 and 100");
            }
            if (percentagesByUser.put(input.userId(), input.value()) != null) {
                throw new ValidationException("duplicate percentage split for user " + input.userId());
            }
            totalPercentage += input.value();
        }

        if (totalPercentage != HUNDRED) {
            throw new ValidationException("sum of percentages must be exactly 100");
        }

        List<ShareRemainder> workingShares = new ArrayList<>(participants.size());
        long assigned = 0;
        for (int index = 0; index < participants.size(); index++) {
            String participant = participants.get(index);
            Long percentage = percentagesByUser.get(participant);
            if (percentage == null) {
                throw new ValidationException("missing percentage for participant " + participant);
            }

            long rawProduct = totalAmountInCents * percentage;
            long amount = rawProduct / HUNDRED;
            long remainder = rawProduct % HUNDRED;
            assigned += amount;
            workingShares.add(new ShareRemainder(participant, amount, remainder, index));
        }

        long centsLeft = totalAmountInCents - assigned;
        workingShares.sort(Comparator
            .comparingLong(ShareRemainder::remainder).reversed()
            .thenComparingInt(ShareRemainder::originalOrder));

        for (int index = 0; index < centsLeft; index++) {
            ShareRemainder share = workingShares.get(index);
            share.amount = share.amount + 1;
        }

        workingShares.sort(Comparator.comparingInt(ShareRemainder::originalOrder));

        List<ExpenseShare> result = new ArrayList<>(workingShares.size());
        for (ShareRemainder share : workingShares) {
            result.add(new ExpenseShare(share.userId, share.amount));
        }
        return result;
    }

    private static final class ShareRemainder {
        private final String userId;
        private long amount;
        private final long remainder;
        private final int originalOrder;

        private ShareRemainder(String userId, long amount, long remainder, int originalOrder) {
            this.userId = userId;
            this.amount = amount;
            this.remainder = remainder;
            this.originalOrder = originalOrder;
        }

        private long remainder() {
            return remainder;
        }

        private int originalOrder() {
            return originalOrder;
        }
    }
}

