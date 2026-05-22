package org.systemdesign.pg;

import org.systemdesign.pg.enums.*;
import org.systemdesign.pg.model.*;
import org.systemdesign.pg.service.PaymentGatewayService;

import java.math.BigDecimal;

/**
 * ═══════════════════════════════════════════════════════════════════════
 *  PAYMENT GATEWAY — LLD INTERVIEW DEMO
 * ═══════════════════════════════════════════════════════════════════════
 *
 * This class demonstrates the full payment gateway flow as you would
 * walk through in an LLD interview.
 *
 * Scenarios covered:
 *  1. Successful payment via Stripe (Credit Card)
 *  2. Payment via RazorPay (UPI)
 *  3. Duplicate/Idempotent payment (same order + key)
 *  4. Full refund
 *  5. Partial refund
 *  6. Retry a failed payment
 *  7. View audit ledger
 */
public class Main {

    public static void main(String[] args) {

        PaymentGatewayService gateway = PaymentGatewayService.getInstance();

        // ═══════════════════════════════════════════
        // SCENARIO 1: Successful Credit Card Payment via Stripe
        // ═══════════════════════════════════════════
        System.out.println("\n🔷🔷🔷 SCENARIO 1: Credit Card Payment via Stripe 🔷🔷🔷");

        PaymentRequest request1 = new PaymentRequest.Builder()
                .orderId("ORD-1001")
                .userId("USER-42")
                .merchantId("MERCHANT-AMAZON")
                .amount(new BigDecimal("2499.99"))
                .currency(Currency.INR)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentProvider(PaymentProvider.STRIPE)
                .idempotencyKey("IDEM-1001-A")
                .build();

        PaymentResponse response1 = gateway.processPayment(request1);
        System.out.println(">>> Final Response: " + response1);

        // ═══════════════════════════════════════════
        // SCENARIO 2: UPI Payment via RazorPay
        // ═══════════════════════════════════════════
        System.out.println("\n🔷🔷🔷 SCENARIO 2: UPI Payment via RazorPay 🔷🔷🔷");

        PaymentRequest request2 = new PaymentRequest.Builder()
                .orderId("ORD-1002")
                .userId("USER-55")
                .merchantId("MERCHANT-FLIPKART")
                .amount(new BigDecimal("899.00"))
                .currency(Currency.INR)
                .paymentMethod(PaymentMethod.UPI)
                .paymentProvider(PaymentProvider.RAZORPAY)
                .idempotencyKey("IDEM-1002-A")
                .build();

        PaymentResponse response2 = gateway.processPayment(request2);
        System.out.println(">>> Final Response: " + response2);

        // ═══════════════════════════════════════════
        // SCENARIO 3: Idempotency Check — Duplicate Payment
        // ═══════════════════════════════════════════
        System.out.println("\n🔷🔷🔷 SCENARIO 3: Duplicate Payment (Idempotency) 🔷🔷🔷");
        System.out.println("Attempting same payment as Scenario 1 with same idempotency key...");

        PaymentRequest duplicateRequest = new PaymentRequest.Builder()
                .orderId("ORD-1001")
                .userId("USER-42")
                .merchantId("MERCHANT-AMAZON")
                .amount(new BigDecimal("2499.99"))
                .currency(Currency.INR)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentProvider(PaymentProvider.STRIPE)
                .idempotencyKey("IDEM-1001-A")  // Same key!
                .build();

        PaymentResponse duplicateResponse = gateway.processPayment(duplicateRequest);
        System.out.println(">>> Duplicate Response: " + duplicateResponse);

        // ═══════════════════════════════════════════
        // SCENARIO 4: Full Refund
        // ═══════════════════════════════════════════
        if (response1.getStatus() == PaymentStatus.SUCCESS) {
            System.out.println("\n🔷🔷🔷 SCENARIO 4: Full Refund 🔷🔷🔷");

            try {
                Refund refund = gateway.processRefund(
                        response1.getPaymentId(),
                        new BigDecimal("2499.99"),
                        "Customer requested cancellation"
                );
                System.out.println(">>> Refund Result: " + refund);
            } catch (Exception e) {
                System.out.println(">>> Refund Error: " + e.getMessage());
            }
        }

        // ═══════════════════════════════════════════
        // SCENARIO 5: Partial Refund
        // ═══════════════════════════════════════════
        if (response2.getStatus() == PaymentStatus.SUCCESS) {
            System.out.println("\n🔷🔷🔷 SCENARIO 5: Partial Refund 🔷🔷🔷");

            try {
                Refund partialRefund = gateway.processRefund(
                        response2.getPaymentId(),
                        new BigDecimal("300.00"),
                        "Partial item return"
                );
                System.out.println(">>> Partial Refund Result: " + partialRefund);

                // Check remaining refundable amount
                Payment payment2 = gateway.getPayment(response2.getPaymentId());
                System.out.println(">>> Payment Status: " + payment2.getStatus());
                System.out.println(">>> Remaining Refundable: " + payment2.getRefundableAmount());
            } catch (Exception e) {
                System.out.println(">>> Refund Error: " + e.getMessage());
            }
        }

        // ═══════════════════════════════════════════
        // SCENARIO 6: Payment via PayPal (Wallet)
        // ═══════════════════════════════════════════
        System.out.println("\n🔷🔷🔷 SCENARIO 6: Payment via PayPal (Wallet) 🔷🔷🔷");

        PaymentRequest request3 = new PaymentRequest.Builder()
                .orderId("ORD-1003")
                .userId("USER-77")
                .merchantId("MERCHANT-MYNTRA")
                .amount(new BigDecimal("1599.50"))
                .currency(Currency.USD)
                .paymentMethod(PaymentMethod.WALLET)
                .paymentProvider(PaymentProvider.PAYPAL)
                .idempotencyKey("IDEM-1003-A")
                .build();

        PaymentResponse response3 = gateway.processPayment(request3);
        System.out.println(">>> Final Response: " + response3);

        // If the PayPal payment failed, retry with a different idempotency key
        if (response3.getStatus() == PaymentStatus.FAILED) {
            System.out.println("\n🔄 Retrying failed payment with new idempotency key...");

            PaymentRequest retryRequest = new PaymentRequest.Builder()
                    .orderId("ORD-1003")
                    .userId("USER-77")
                    .merchantId("MERCHANT-MYNTRA")
                    .amount(new BigDecimal("1599.50"))
                    .currency(Currency.USD)
                    .paymentMethod(PaymentMethod.WALLET)
                    .paymentProvider(PaymentProvider.PAYPAL)
                    .idempotencyKey("IDEM-1003-B")  // New key for retry!
                    .build();

            PaymentResponse retryResponse = gateway.processPayment(retryRequest);
            System.out.println(">>> Retry Response: " + retryResponse);
        }

        // ══════════���════════════════════════════════
        // SCENARIO 7: View Full Audit Ledger
        // ═══════════════════════════════════════════
        System.out.println("\n🔷🔷🔷 SCENARIO 7: Full Audit Ledger 🔷🔷🔷");
        System.out.println("─────────────────────────────────────────────");
        gateway.getFullLedger().forEach(System.out::println);
        System.out.println("─────────────────────────────────────────────");

        // ═══════════════════════════════════════════
        // SCENARIO 8: Error Handling — Invalid refund
        // ═══════════════════════════════════════════
        System.out.println("\n🔷🔷🔷 SCENARIO 8: Error Handling 🔷🔷🔷");

        try {
            gateway.processRefund("NON-EXISTENT-ID", new BigDecimal("100"), "test");
        } catch (Exception e) {
            System.out.println(">>> Expected Error: " + e.getMessage());
        }

        try {
            new PaymentRequest.Builder()
                    .orderId("ORD-999")
                    .amount(new BigDecimal("-100"))
                    .build();
        } catch (Exception e) {
            System.out.println(">>> Validation Error: " + e.getMessage());
        }

        System.out.println("\n✅ Payment Gateway LLD Demo Complete!");
    }
}