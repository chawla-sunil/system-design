package org.systemdesign.splitwise.model;

public record BalanceEntry(String debtorUserId, String creditorUserId, long amountInCents) {

    public BalanceEntry {
        if (debtorUserId == null || debtorUserId.isBlank()) {
            throw new IllegalArgumentException("debtorUserId cannot be blank");
        }
        if (creditorUserId == null || creditorUserId.isBlank()) {
            throw new IllegalArgumentException("creditorUserId cannot be blank");
        }
        if (debtorUserId.equals(creditorUserId)) {
            throw new IllegalArgumentException("debtor and creditor cannot be same");
        }
        if (amountInCents <= 0) {
            throw new IllegalArgumentException("amountInCents must be positive");
        }
    }
}

