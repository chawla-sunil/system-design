package org.systemdesign.splitwise.strategy;

import java.util.EnumMap;
import java.util.Map;
import org.systemdesign.splitwise.exception.ValidationException;
import org.systemdesign.splitwise.model.SplitType;

public final class SplitStrategyFactory {
    private final Map<SplitType, SplitStrategy> strategies = new EnumMap<>(SplitType.class);

    public SplitStrategyFactory() {
        strategies.put(SplitType.EQUAL, new EqualSplitStrategy());
        strategies.put(SplitType.EXACT, new ExactSplitStrategy());
        strategies.put(SplitType.PERCENTAGE, new PercentageSplitStrategy());
    }

    public SplitStrategy getStrategy(SplitType splitType) {
        SplitStrategy strategy = strategies.get(splitType);
        if (strategy == null) {
            throw new ValidationException("unsupported split type: " + splitType);
        }
        return strategy;
    }
}

