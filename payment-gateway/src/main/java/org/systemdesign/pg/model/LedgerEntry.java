package org.systemdesign.pg.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable ledger entry for audit trail.
 * Every payment event produces a ledger entry — this is your source of truth.
 */
public class LedgerEntry {

    private final String entryId;
    private final String paymentId;
    private final String eventType;   // e.g., "PAYMENT_INITIATED", "PAYMENT_SUCCESS", "REFUND_SUCCESS"
    private final String details;
    private final LocalDateTime timestamp;

    public LedgerEntry(String paymentId, String eventType, String details) {
        this.entryId = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public String getEntryId() { return entryId; }
    public String getPaymentId() { return paymentId; }
    public String getEventType() { return eventType; }
    public String getDetails() { return details; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "[LEDGER] " + timestamp + " | " + eventType + " | paymentId=" + paymentId +
                " | " + details;
    }
}

