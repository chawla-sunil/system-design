package org.systemdesign.atm.model;

import org.systemdesign.atm.enums.TransactionStatus;
import org.systemdesign.atm.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionRecord {

    private final String id;
    private final TransactionType transactionType;
    private final BigDecimal amount;
    private final TransactionStatus status;
    private final String message;
    private final BigDecimal resultingBalance;
    private final LocalDateTime timestamp;

    public TransactionRecord(
            TransactionType transactionType,
            BigDecimal amount,
            TransactionStatus status,
            String message,
            BigDecimal resultingBalance,
            LocalDateTime timestamp
    ) {
        this.id = UUID.randomUUID().toString();
        this.transactionType = transactionType;
        this.amount = amount;
        this.status = status;
        this.message = message;
        this.resultingBalance = resultingBalance;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getResultingBalance() {
        return resultingBalance;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "TransactionRecord{" +
                "id='" + id + '\'' +
                ", type=" + transactionType +
                ", amount=" + amount +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", resultingBalance=" + resultingBalance +
                ", timestamp=" + timestamp +
                '}';
    }
}

