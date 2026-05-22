# Payment Gateway - Low Level Design

## Problem Statement
Design a Payment Gateway system that processes payments, supports multiple payment methods and providers, handles refunds, and maintains a transaction ledger.

---

## Functional Requirements
1. **Process Payments** — Support Credit Card, Debit Card, UPI, Net Banking, Wallet
2. **Refund Payments** — Full or partial refunds
3. **Payment Status Tracking** — INITIATED → PROCESSING → SUCCESS / FAILED / REFUNDED
4. **Idempotency** — No duplicate payments for the same order
5. **Multiple Payment Providers** — Stripe, PayPal, RazorPay routing
6. **Retry Failed Payments** — Retry with same or different method
7. **Transaction Ledger** — Audit log of all payment events

## Non-Functional Requirements (discussed, not coded)
- PCI-DSS compliance (card data encryption)
- High availability and fault tolerance
- Eventual consistency
- Idempotency via idempotency keys

---

## Design Patterns Used

| Pattern | Where Used | Why |
|---------|-----------|-----|
| **Strategy** | PaymentProcessor (Stripe, PayPal, RazorPay) | Swap payment provider at runtime |
| **State** | PaymentStatus transitions | Clean state machine for payment lifecycle |
| **Factory** | PaymentProcessorFactory | Decouple processor creation from usage |
| **Observer** | PaymentEventListener (Ledger, Notification) | React to payment events without coupling |
| **Builder** | PaymentRequest | Complex object construction |
| **Singleton** | PaymentGatewayService | Single entry point |

---

## Class Diagram (Textual)

```
PaymentGatewayService (Singleton)
  ├── PaymentProcessorFactory
  │     └── creates → PaymentProcessor (Strategy Interface)
  │           ├── StripePaymentProcessor
  │           ├── PayPalPaymentProcessor
  │           └── RazorPayPaymentProcessor
  ├── PaymentRepository (in-memory store)
  ├── RefundService
  ├── IdempotencyService
  └── PaymentEventManager (Observer)
        ├── LedgerEventListener
        └── NotificationEventListener

Models:
  - Payment (id, orderId, amount, currency, method, provider, status, timestamps)
  - PaymentRequest (builder pattern)
  - PaymentResponse
  - Refund (id, paymentId, amount, reason, status)
  - LedgerEntry (audit trail)

Enums:
  - PaymentStatus: INITIATED, PROCESSING, SUCCESS, FAILED, REFUNDED
  - PaymentMethod: CREDIT_CARD, DEBIT_CARD, UPI, NET_BANKING, WALLET
  - PaymentProvider: STRIPE, PAYPAL, RAZORPAY
  - Currency: INR, USD, EUR
```

---

## Payment Flow

```
1. Client calls paymentGateway.processPayment(request)
2. IdempotencyService checks for duplicate (same orderId + idempotency key)
3. Payment object created with status = INITIATED
4. PaymentProcessorFactory resolves the right processor (Strategy)
5. Payment status → PROCESSING
6. Processor.process() called → returns success/failure
7. Payment status → SUCCESS or FAILED
8. PaymentEventManager notifies observers (ledger, notification)
9. PaymentResponse returned to client
```

## Refund Flow

```
1. Client calls refundService.processRefund(paymentId, amount, reason)
2. Validate: payment exists, status = SUCCESS, refund amount ≤ paid amount
3. Processor.refund() called
4. Payment status → REFUNDED (if full refund)
5. Refund record created
6. Event published
```

