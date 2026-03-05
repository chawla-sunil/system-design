package org.designpatterns.structural.adapter;

import java.util.List;

public class AdapterDemo {
    public static void run() {
        System.out.println("=== ADAPTER PATTERN DEMO ===\n");

        PaymentProcessor stripe = new StripeAdapter(new StripeApi());
        PaymentProcessor paypal = new PayPalAdapter(new PayPalApi(), "merchant@shop.com");

        List<PaymentProcessor> processors = List.of(stripe, paypal);

        for (PaymentProcessor processor : processors) {
            System.out.println("--- Processing with " + processor.getProviderName() + " ---");
            processor.processPayment(99.99, "USD");
            processor.refund("txn_12345", 99.99);
        }

        System.out.println();
    }
}
