package org.systemdesign.pg.processor;

import org.systemdesign.pg.enums.PaymentProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * FACTORY PATTERN — Creates and caches PaymentProcessor instances.
 *
 * Why Factory?
 *  - Decouples processor creation from usage
 *  - Easy to add new providers without modifying existing code (Open/Closed Principle)
 *  - Caches processors (they are stateless, so reusable)
 */
public class PaymentProcessorFactory {

    private static final Map<PaymentProvider, PaymentProcessor> processorCache = new HashMap<>();

    static {
        processorCache.put(PaymentProvider.STRIPE, new StripePaymentProcessor());
        processorCache.put(PaymentProvider.PAYPAL, new PayPalPaymentProcessor());
        processorCache.put(PaymentProvider.RAZORPAY, new RazorPayPaymentProcessor());
    }

    public static PaymentProcessor getProcessor(PaymentProvider provider) {
        PaymentProcessor processor = processorCache.get(provider);
        if (processor == null) {
            throw new IllegalArgumentException("No processor found for provider: " + provider);
        }
        return processor;
    }

    /**
     * Register a new processor at runtime (extensibility).
     */
    public static void registerProcessor(PaymentProvider provider, PaymentProcessor processor) {
        processorCache.put(provider, processor);
    }
}

