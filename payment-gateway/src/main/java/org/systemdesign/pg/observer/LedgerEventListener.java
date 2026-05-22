package org.systemdesign.pg.observer;

import org.systemdesign.pg.model.LedgerEntry;
import org.systemdesign.pg.model.Payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ledger listener — creates immutable audit entries for every payment event.
 *
 * In real world: this would write to an append-only database table or event store.
 * Critical for:
 *  - Financial auditing & compliance
 *  - Dispute resolution
 *  - Reconciliation with payment providers
 */
public class LedgerEventListener implements PaymentEventListener {

    private final List<LedgerEntry> ledger = new ArrayList<>();

    @Override
    public void onPaymentInitiated(Payment payment) {
        LedgerEntry entry = new LedgerEntry(payment.getPaymentId(), "PAYMENT_INITIATED",
                "Payment initiated for order " + payment.getOrderId() +
                        ", amount=" + payment.getAmount() + " " + payment.getCurrency());
        ledger.add(entry);
        System.out.println(entry);
    }

    @Override
    public void onPaymentSuccess(Payment payment) {
        LedgerEntry entry = new LedgerEntry(payment.getPaymentId(), "PAYMENT_SUCCESS",
                "Payment successful via " + payment.getPaymentProvider());
        ledger.add(entry);
        System.out.println(entry);
    }

    @Override
    public void onPaymentFailed(Payment payment) {
        LedgerEntry entry = new LedgerEntry(payment.getPaymentId(), "PAYMENT_FAILED",
                "Payment failed via " + payment.getPaymentProvider());
        ledger.add(entry);
        System.out.println(entry);
    }

    @Override
    public void onRefundSuccess(Payment payment, BigDecimal refundAmount) {
        LedgerEntry entry = new LedgerEntry(payment.getPaymentId(), "REFUND_SUCCESS",
                "Refund of " + refundAmount + " " + payment.getCurrency() + " processed");
        ledger.add(entry);
        System.out.println(entry);
    }

    @Override
    public void onRefundFailed(Payment payment) {
        LedgerEntry entry = new LedgerEntry(payment.getPaymentId(), "REFUND_FAILED",
                "Refund failed for payment " + payment.getPaymentId());
        ledger.add(entry);
        System.out.println(entry);
    }

    public List<LedgerEntry> getLedger() {
        return Collections.unmodifiableList(ledger);
    }

    public List<LedgerEntry> getLedgerForPayment(String paymentId) {
        return ledger.stream()
                .filter(e -> e.getPaymentId().equals(paymentId))
                .toList();
    }
}

