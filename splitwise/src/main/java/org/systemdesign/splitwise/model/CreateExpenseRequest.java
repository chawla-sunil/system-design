package org.systemdesign.splitwise.model;

import java.util.List;
import java.util.Objects;

public record CreateExpenseRequest(
    String description,
    String paidByUserId,
    long totalAmountInCents,
    List<String> participants,
    SplitType splitType,
    List<SplitInput> splitInputs,
    String groupId
) {

    public CreateExpenseRequest {
        if (Objects.isNull(description) || description.isBlank()) {
            throw new IllegalArgumentException("description cannot be blank");
        }
        if (Objects.isNull(paidByUserId) || paidByUserId.isBlank()) {
            throw new IllegalArgumentException("paidByUserId cannot be blank");
        }
        if (totalAmountInCents <= 0) {
            throw new IllegalArgumentException("totalAmountInCents must be positive");
        }
        if (Objects.isNull(splitType)) {
            throw new IllegalArgumentException("splitType cannot be null");
        }

        participants = participants == null ? List.of() : List.copyOf(participants);
        splitInputs = splitInputs == null ? List.of() : List.copyOf(splitInputs);

        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants cannot be empty");
        }

        description = description.trim();
        paidByUserId = paidByUserId.trim();
        groupId = groupId == null || groupId.isBlank() ? null : groupId.trim();
    }
}

