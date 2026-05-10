package org.systemdesign.splitwise.strategy;

import java.util.ArrayList;
import java.util.List;
import org.systemdesign.splitwise.model.ExpenseShare;
import org.systemdesign.splitwise.model.SplitInput;

public final class EqualSplitStrategy implements SplitStrategy {

    @Override
    public List<ExpenseShare> split(long totalAmountInCents, List<String> participants, List<SplitInput> splitInputs) {
        long baseAmount = totalAmountInCents / participants.size();
        long remainder = totalAmountInCents % participants.size();

        List<ExpenseShare> shares = new ArrayList<>(participants.size());
        for (int index = 0; index < participants.size(); index++) {
            long share = baseAmount + (index < remainder ? 1 : 0);
            shares.add(new ExpenseShare(participants.get(index), share));
        }
        return shares;
    }
}

