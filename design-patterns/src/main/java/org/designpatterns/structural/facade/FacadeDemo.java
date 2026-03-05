package org.designpatterns.structural.facade;

public class FacadeDemo {
    public static void run() {
        System.out.println("=== FACADE PATTERN DEMO ===\n");

        // Without facade: client would need to interact with 4 different services
        // With facade: single method call handles everything
        OrderFacade orderFacade = new OrderFacade();

        String orderId = orderFacade.placeOrder(
                "PROD-001", 2, "4111111111111234",
                59.99, "123 Main St, Springfield", "john@example.com");

        System.out.println("\nOrder ID: " + orderId);
        System.out.println();
    }
}
