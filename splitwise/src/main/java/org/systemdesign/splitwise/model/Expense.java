package org.systemdesign.splitwise.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Expense(
    String id,
    String description,
    String paidByUserId,
    long totalAmountInCents,
    List<ExpenseShare> shares,
    SplitType splitType,
    String groupId,
    Instant createdAt
) {

    public Expense {
        if (Objects.isNull(id) || id.isBlank()) {
            throw new IllegalArgumentException("expense id cannot be blank");
        }
        if (Objects.isNull(description) || description.isBlank()) {
            throw new IllegalArgumentException("description cannot be blank");
        }
        if (Objects.isNull(paidByUserId) || paidByUserId.isBlank()) {
            throw new IllegalArgumentException("paidByUserId cannot be blank");
        }
        if (Objects.isNull(splitType)) {
            throw new IllegalArgumentException("splitType cannot be null");
        }
        if (totalAmountInCents <= 0) {
            throw new IllegalArgumentException("totalAmountInCents must be positive");
        }

        shares = List.copyOf(shares);
        if (shares.isEmpty()) {
            throw new IllegalArgumentException("expense must have at least one share");
        }

        long shareTotal = shares.stream().mapToLong(ExpenseShare::amountInCents).sum();
        if (shareTotal != totalAmountInCents) {
            throw new IllegalArgumentException("sum of shares must equal total amount");
        }

        id = id.trim();
        description = description.trim();
        paidByUserId = paidByUserId.trim();
        groupId = groupId == null || groupId.isBlank() ? null : groupId.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}

