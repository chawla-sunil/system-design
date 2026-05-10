package org.systemdesign.splitwise.strategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.systemdesign.splitwise.exception.ValidationException;
import org.systemdesign.splitwise.model.ExpenseShare;
import org.systemdesign.splitwise.model.SplitInput;

public final class ExactSplitStrategy implements SplitStrategy {

    @Override
    public List<ExpenseShare> split(long totalAmountInCents, List<String> participants, List<SplitInput> splitInputs) {
        if (splitInputs.size() != participants.size()) {
            throw new ValidationException("exact split requires one amount per participant");
        }

        Map<String, Long> sharesByUser = new LinkedHashMap<>();
        for (SplitInput input : splitInputs) {
            if (sharesByUser.put(input.userId(), input.value()) != null) {
                throw new ValidationException("duplicate split input for user " + input.userId());
            }
        }

        long total = 0;
        for (String participant : participants) {
            Long share = sharesByUser.get(participant);
            if (share == null) {
                throw new ValidationException("missing exact share for participant " + participant);
            }
            total += share;
        }

        if (total != totalAmountInCents) {
            throw new ValidationException("sum of exact shares must match total amount");
        }

        return participants.stream()
            .map(participant -> new ExpenseShare(participant, sharesByUser.get(participant)))
            .collect(Collectors.toList());
    }
}

