package org.systemdesign.atm.service;

import org.systemdesign.atm.enums.SessionState;
import org.systemdesign.atm.enums.TransactionStatus;
import org.systemdesign.atm.enums.TransactionType;
import org.systemdesign.atm.exception.CashUnavailableException;
import org.systemdesign.atm.exception.InvalidAtmOperationException;
import org.systemdesign.atm.model.Card;
import org.systemdesign.atm.model.TransactionRecord;
import org.systemdesign.atm.strategy.CashDispenseStrategy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AtmService {

    private final AtmMachine atmMachine;
    private final BankService bankService;
    private final CashDispenseStrategy cashDispenseStrategy;
    private final Clock clock;

    public AtmService(AtmMachine atmMachine, BankService bankService, CashDispenseStrategy cashDispenseStrategy, Clock clock) {
        this.atmMachine = atmMachine;
        this.bankService = bankService;
        this.cashDispenseStrategy = cashDispenseStrategy;
        this.clock = clock;
    }

    public void insertCard(String cardNumber) {
        Card card = bankService.getCardByNumber(cardNumber);
        atmMachine.insertCard(card);
    }

    public void enterPin(String pin) {
        requireState(SessionState.CARD_INSERTED, "Enter PIN after card insertion.");
        Card card = requireCard();
        bankService.authenticate(card, pin, atmMachine.getMaxPinAttempts());
        atmMachine.markAuthenticated();
    }

    public BigDecimal checkBalance() {
        requireState(SessionState.AUTHENTICATED, "Balance can be checked only after successful authentication.");
        Card card = requireCard();
        BigDecimal balance = bankService.getBalance(card);
        bankService.recordTransaction(card, successful(TransactionType.BALANCE_ENQUIRY, BigDecimal.ZERO, balance, "Balance enquiry"));
        return balance;
    }

    public Map<Integer, Integer> withdraw(int amount) {
        requireState(SessionState.AUTHENTICATED, "Withdraw is allowed only for authenticated session.");
        validateWithdrawalAmount(amount);

        Card card = requireCard();
        BigDecimal amountValue = BigDecimal.valueOf(amount);

        bankService.debit(card, amountValue);
        Map<Integer, Integer> dispensePlan = cashDispenseStrategy.dispense(amount, atmMachine.getCashInventory().snapshot());

        if (dispensePlan.isEmpty()) {
            bankService.credit(card, amountValue);
            bankService.recordTransaction(card, failed(TransactionType.WITHDRAWAL, amountValue, bankService.getBalance(card), "ATM cannot dispense requested amount with available denominations."));
            throw new CashUnavailableException("Unable to dispense exact amount with current notes.");
        }

        atmMachine.getCashInventory().removeNotes(dispensePlan);
        BigDecimal latestBalance = bankService.getBalance(card);
        bankService.recordTransaction(card, successful(TransactionType.WITHDRAWAL, amountValue, latestBalance, "Cash dispensed"));
        return dispensePlan;
    }

    public BigDecimal deposit(Map<Integer, Integer> notes) {
        requireState(SessionState.AUTHENTICATED, "Deposit is allowed only for authenticated session.");
        validateDepositNotes(notes);

        int totalDeposit = notes.entrySet().stream().mapToInt(entry -> entry.getKey() * entry.getValue()).sum();
        BigDecimal amount = BigDecimal.valueOf(totalDeposit);

        Card card = requireCard();
        atmMachine.getCashInventory().addNotes(notes);
        bankService.credit(card, amount);

        BigDecimal latestBalance = bankService.getBalance(card);
        bankService.recordTransaction(card, successful(TransactionType.DEPOSIT, amount, latestBalance, "Cash deposited"));
        return latestBalance;
    }

    public List<TransactionRecord> miniStatement(int limit) {
        requireState(SessionState.AUTHENTICATED, "Mini statement is available only for authenticated session.");
        return bankService.getMiniStatement(requireCard(), limit);
    }

    public void ejectCard() {
        atmMachine.ejectCard();
    }

    private Card requireCard() {
        Card card = atmMachine.getInsertedCard();
        if (card == null) {
            throw new InvalidAtmOperationException("No card is currently inserted.");
        }
        return card;
    }

    private void requireState(SessionState required, String message) {
        if (atmMachine.getSessionState() != required) {
            throw new InvalidAtmOperationException(message + " Current state: " + atmMachine.getSessionState());
        }
    }

    private void validateWithdrawalAmount(int amount) {
        if (amount <= 0) {
            throw new InvalidAtmOperationException("Withdrawal amount should be positive.");
        }
        if (amount > atmMachine.getPerTransactionWithdrawLimit()) {
            throw new InvalidAtmOperationException("Amount exceeds per-transaction limit of " + atmMachine.getPerTransactionWithdrawLimit());
        }
        boolean canBeComposed = atmMachine.getSupportedDenominations().stream().anyMatch(denomination -> amount % denomination == 0);
        if (!canBeComposed) {
            throw new InvalidAtmOperationException("Requested amount is not aligned with ATM denominations.");
        }
    }

    private void validateDepositNotes(Map<Integer, Integer> notes) {
        if (notes == null || notes.isEmpty()) {
            throw new InvalidAtmOperationException("Deposit should include at least one note.");
        }

        for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {
            if (!atmMachine.getSupportedDenominations().contains(entry.getKey())) {
                throw new InvalidAtmOperationException("Unsupported note denomination in deposit: " + entry.getKey());
            }
            if (entry.getValue() <= 0) {
                throw new InvalidAtmOperationException("Note count should be positive.");
            }
        }
    }

    private TransactionRecord successful(TransactionType type, BigDecimal amount, BigDecimal resultingBalance, String message) {
        return new TransactionRecord(type, amount, TransactionStatus.SUCCESS, message, resultingBalance, LocalDateTime.now(clock));
    }

    private TransactionRecord failed(TransactionType type, BigDecimal amount, BigDecimal resultingBalance, String message) {
        return new TransactionRecord(type, amount, TransactionStatus.FAILED, message, resultingBalance, LocalDateTime.now(clock));
    }
}

