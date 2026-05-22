package org.systemdesign.pg.observer;

import org.systemdesign.pg.model.Payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Event Manager — manages and notifies all registered listeners.
 *
 * This is the Subject/Publisher in the Observer pattern.
 * Decouples payment processing from side effects (logging, notifications, analytics).
 */
public class PaymentEventManager {

    private final List<PaymentEventListener> listeners = new ArrayList<>();

    public void subscribe(PaymentEventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(PaymentEventListener listener) {
        listeners.remove(listener);
    }

    public void notifyPaymentInitiated(Payment payment) {
        for (PaymentEventListener listener : listeners) {
            listener.onPaymentInitiated(payment);
        }
    }

    public void notifyPaymentSuccess(Payment payment) {
        for (PaymentEventListener listener : listeners) {
            listener.onPaymentSuccess(payment);
        }
    }

    public void notifyPaymentFailed(Payment payment) {
        for (PaymentEventListener listener : listeners) {
            listener.onPaymentFailed(payment);
        }
    }

    public void notifyRefundSuccess(Payment payment, BigDecimal refundAmount) {
        for (PaymentEventListener listener : listeners) {
            listener.onRefundSuccess(payment, refundAmount);
        }
    }

    public void notifyRefundFailed(Payment payment) {
        for (PaymentEventListener listener : listeners) {
            listener.onRefundFailed(payment);
        }
    }
}

