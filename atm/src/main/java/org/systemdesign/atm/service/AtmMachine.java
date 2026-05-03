package org.systemdesign.atm.service;

import org.systemdesign.atm.enums.SessionState;
import org.systemdesign.atm.exception.InvalidAtmOperationException;
import org.systemdesign.atm.model.Card;
import org.systemdesign.atm.model.CashInventory;

import java.util.Set;

public class AtmMachine {

    private final String machineId;
    private final CashInventory cashInventory;
    private final Set<Integer> supportedDenominations;
    private final int maxPinAttempts;
    private final int perTransactionWithdrawLimit;

    private Card insertedCard;
    private SessionState sessionState;

    public AtmMachine(
            String machineId,
            CashInventory cashInventory,
            Set<Integer> supportedDenominations,
            int maxPinAttempts,
            int perTransactionWithdrawLimit
    ) {
        this.machineId = machineId;
        this.cashInventory = cashInventory;
        this.supportedDenominations = supportedDenominations;
        this.maxPinAttempts = maxPinAttempts;
        this.perTransactionWithdrawLimit = perTransactionWithdrawLimit;
        this.sessionState = SessionState.IDLE;
    }

    public void insertCard(Card card) {
        if (sessionState != SessionState.IDLE) {
            throw new InvalidAtmOperationException("A session is already active. Please eject current card first.");
        }
        this.insertedCard = card;
        this.sessionState = SessionState.CARD_INSERTED;
    }

    public void markAuthenticated() {
        if (sessionState != SessionState.CARD_INSERTED) {
            throw new InvalidAtmOperationException("PIN authentication is not expected in current state.");
        }
        this.sessionState = SessionState.AUTHENTICATED;
    }

    public void ejectCard() {
        this.insertedCard = null;
        this.sessionState = SessionState.IDLE;
    }

    public String getMachineId() {
        return machineId;
    }

    public CashInventory getCashInventory() {
        return cashInventory;
    }

    public Set<Integer> getSupportedDenominations() {
        return supportedDenominations;
    }

    public int getMaxPinAttempts() {
        return maxPinAttempts;
    }

    public int getPerTransactionWithdrawLimit() {
        return perTransactionWithdrawLimit;
    }

    public Card getInsertedCard() {
        return insertedCard;
    }

    public SessionState getSessionState() {
        return sessionState;
    }
}

