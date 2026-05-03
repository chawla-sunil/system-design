package org.systemdesign.atm.service;

import org.systemdesign.atm.exception.AuthenticationException;
import org.systemdesign.atm.exception.InvalidAtmOperationException;
import org.systemdesign.atm.model.Account;
import org.systemdesign.atm.model.Card;
import org.systemdesign.atm.model.TransactionRecord;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryBankService implements BankService {

    private final Map<String, Card> cardsByNumber;
    private final Map<String, Account> accountsById;

    public InMemoryBankService(Collection<Account> accounts, Collection<Card> cards) {
        this.cardsByNumber = new HashMap<>();
        this.accountsById = new HashMap<>();

        for (Account account : accounts) {
            accountsById.put(account.getAccountId(), account);
        }

        for (Card card : cards) {
            cardsByNumber.put(card.getCardNumber(), card);
            if (!accountsById.containsKey(card.getAccountId())) {
                throw new IllegalArgumentException("Card mapped to unknown account: " + card.getCardNumber());
            }
        }
    }

    @Override
    public Card getCardByNumber(String cardNumber) {
        Card card = cardsByNumber.get(cardNumber);
        if (card == null) {
            throw new InvalidAtmOperationException("Card not recognized by bank.");
        }
        if (card.isBlocked()) {
            throw new AuthenticationException("Card is blocked.");
        }
        return card;
    }

    @Override
    public void authenticate(Card card, String pin, int maxAttempts) {
        Account account = resolveAccount(card);
        account.verifyPin(pin, maxAttempts);
        if (account.isLocked()) {
            card.block();
            throw new AuthenticationException("Account is locked. Card blocked.");
        }
    }

    @Override
    public BigDecimal getBalance(Card card) {
        return resolveAccount(card).getBalance();
    }

    @Override
    public void debit(Card card, BigDecimal amount) {
        resolveAccount(card).debit(amount);
    }

    @Override
    public void credit(Card card, BigDecimal amount) {
        resolveAccount(card).credit(amount);
    }

    @Override
    public void recordTransaction(Card card, TransactionRecord transactionRecord) {
        resolveAccount(card).addTransaction(transactionRecord);
    }

    @Override
    public List<TransactionRecord> getMiniStatement(Card card, int limit) {
        return resolveAccount(card).getRecentTransactions(limit);
    }

    private Account resolveAccount(Card card) {
        Account account = accountsById.get(card.getAccountId());
        if (account == null) {
            throw new InvalidAtmOperationException("Linked account not found.");
        }
        return account;
    }
}

