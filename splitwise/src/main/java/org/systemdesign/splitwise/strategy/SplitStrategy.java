package org.systemdesign.splitwise.strategy;

import java.util.List;
import org.systemdesign.splitwise.model.ExpenseShare;
import org.systemdesign.splitwise.model.SplitInput;

public interface SplitStrategy {

    List<ExpenseShare> split(long totalAmountInCents, List<String> participants, List<SplitInput> splitInputs);
}

