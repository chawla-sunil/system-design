package org.systemdesign.atm.service;

import org.systemdesign.atm.model.Card;
import org.systemdesign.atm.model.TransactionRecord;

import java.math.BigDecimal;
import java.util.List;

public interface BankService {

    Card getCardByNumber(String cardNumber);

    void authenticate(Card card, String pin, int maxAttempts);

    BigDecimal getBalance(Card card);

    void debit(Card card, BigDecimal amount);

    void credit(Card card, BigDecimal amount);

    void recordTransaction(Card card, TransactionRecord transactionRecord);

    List<TransactionRecord> getMiniStatement(Card card, int limit);
}

