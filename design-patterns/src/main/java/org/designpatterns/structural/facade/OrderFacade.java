package org.designpatterns.structural.facade;

public class OrderFacade {
    private final InventoryService inventory;
    private final PaymentService payment;
    private final ShippingService shipping;
    private final NotificationService notification;

    public OrderFacade() {
        this.inventory = new InventoryService();
        this.payment = new PaymentService();
        this.shipping = new ShippingService();
        this.notification = new NotificationService();
    }

    public String placeOrder(String productId, int quantity, String cardNumber,
                             double amount, String address, String email) {
        System.out.println("--- OrderFacade: Processing order ---");

        // Step 1: Check and reserve inventory
        if (!inventory.checkStock(productId)) {
            throw new RuntimeException("Product out of stock: " + productId);
        }
        inventory.reserveStock(productId, quantity);

        // Step 2: Process payment
        if (!payment.validateCard(cardNumber)) {
            throw new RuntimeException("Invalid payment card");
        }
        String paymentTxn = payment.chargeCard(cardNumber, amount);

        // Step 3: Create shipment
        String trackingId = shipping.createShipment(paymentTxn, address);
        shipping.schedulePickup(trackingId);

        // Step 4: Notify customer
        notification.sendOrderConfirmation(email, paymentTxn);
        notification.sendShippingUpdate(email, trackingId);

        System.out.println("--- Order completed successfully ---");
        return paymentTxn;
    }
}
