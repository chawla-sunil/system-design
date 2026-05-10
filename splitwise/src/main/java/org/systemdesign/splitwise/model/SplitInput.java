package org.systemdesign.splitwise.model;

import java.util.Objects;

public record SplitInput(String userId, long value) {

    public SplitInput {
        if (Objects.isNull(userId) || userId.isBlank()) {
            throw new IllegalArgumentException("split input userId cannot be blank");
        }
        if (value < 0) {
            throw new IllegalArgumentException("split input value cannot be negative");
        }

        userId = userId.trim();
    }
}

