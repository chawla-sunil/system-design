package org.systemdesign.atm.model;

import org.systemdesign.atm.exception.AuthenticationException;
import org.systemdesign.atm.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Account {

    private final String accountId;
    private final String holderName;
    private final String pin;
    private BigDecimal balance;
    private boolean locked;
    private int failedPinAttempts;
    private final List<TransactionRecord> transactions;

    public Account(String accountId, String holderName, String pin, BigDecimal initialBalance) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = initialBalance;
        this.locked = false;
        this.failedPinAttempts = 0;
        this.transactions = new ArrayList<>();
    }

    public synchronized boolean verifyPin(String enteredPin, int maxAttempts) {
        if (locked) {
            throw new AuthenticationException("Account is locked due to repeated invalid PIN attempts.");
        }

        if (pin.equals(enteredPin)) {
            failedPinAttempts = 0;
            return true;
        }

        failedPinAttempts++;
        if (failedPinAttempts >= maxAttempts) {
            locked = true;
            throw new AuthenticationException("PIN retry limit exceeded. Account has been locked.");
        }
        throw new AuthenticationException("Invalid PIN. Remaining attempts: " + (maxAttempts - failedPinAttempts));
    }

    public synchronized void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive.");
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient account balance.");
        }
        balance = balance.subtract(amount);
    }

    public synchronized void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive.");
        }
        balance = balance.add(amount);
    }

    public synchronized BigDecimal getBalance() {
        return balance;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    public synchronized boolean isLocked() {
        return locked;
    }

    public synchronized void addTransaction(TransactionRecord transactionRecord) {
        this.transactions.add(transactionRecord);
    }

    public synchronized List<TransactionRecord> getRecentTransactions(int limit) {
        int size = transactions.size();
        int start = Math.max(0, size - limit);
        List<TransactionRecord> recent = new ArrayList<>(transactions.subList(start, size));
        Collections.reverse(recent);
        return recent;
    }
}

