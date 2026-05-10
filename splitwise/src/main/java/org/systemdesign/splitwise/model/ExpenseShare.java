package org.systemdesign.splitwise.model;

import java.util.Objects;

public record ExpenseShare(String userId, long amountInCents) {

    public ExpenseShare {
        if (Objects.isNull(userId) || userId.isBlank()) {
            throw new IllegalArgumentException("expense share userId cannot be blank");
        }
        if (amountInCents < 0) {
            throw new IllegalArgumentException("expense share cannot be negative");
        }

        userId = userId.trim();
    }
}

