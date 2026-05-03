package org.systemdesign.atm.model;

public class Card {

    private final String cardNumber;
    private final String accountId;
    private boolean blocked;

    public Card(String cardNumber, String accountId) {
        this.cardNumber = cardNumber;
        this.accountId = accountId;
        this.blocked = false;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getAccountId() {
        return accountId;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void block() {
        this.blocked = true;
    }
}

