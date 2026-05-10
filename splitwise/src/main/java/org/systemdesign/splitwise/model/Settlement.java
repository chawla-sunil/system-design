package org.systemdesign.splitwise.model;

public record Settlement(String fromUserId, String toUserId, long amountInCents) {

    public Settlement {
        if (fromUserId == null || fromUserId.isBlank()) {
            throw new IllegalArgumentException("fromUserId cannot be blank");
        }
        if (toUserId == null || toUserId.isBlank()) {
            throw new IllegalArgumentException("toUserId cannot be blank");
        }
        if (fromUserId.equals(toUserId)) {
            throw new IllegalArgumentException("fromUserId and toUserId cannot be same");
        }
        if (amountInCents <= 0) {
            throw new IllegalArgumentException("amountInCents must be positive");
        }
    }
}

