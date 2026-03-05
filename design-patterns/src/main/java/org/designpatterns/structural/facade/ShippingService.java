package org.designpatterns.structural.facade;

public class ShippingService {
    public String createShipment(String orderId, String address) {
        String trackingId = "SHIP-" + System.currentTimeMillis();
        System.out.println("  [Shipping] Shipment created for order " + orderId + " -> " + trackingId);
        return trackingId;
    }

    public void schedulePickup(String trackingId) {
        System.out.println("  [Shipping] Pickup scheduled for " + trackingId);
    }
}
