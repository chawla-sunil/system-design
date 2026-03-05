package org.designpatterns.behavioral.observer;

public class ObserverDemo {
    public static void run() {
        System.out.println("=== OBSERVER PATTERN DEMO ===\n");

        StockExchange exchange = new StockExchange();

        // Create observers
        PriceDisplay mainDisplay = new PriceDisplay("MainDisplay");
        PriceDisplay mobileApp = new PriceDisplay("MobileApp");
        AlertSystem alertSystem = new AlertSystem();

        // Subscribe to events
        System.out.println("--- Subscribing observers ---");
        exchange.subscribe("PRICE_CHANGE", mainDisplay);
        exchange.subscribe("PRICE_CHANGE", mobileApp);
        exchange.subscribe("PRICE_SPIKE", alertSystem);

        // Update prices - all subscribers get notified
        System.out.println("\n--- Price updates ---");
        exchange.updateStockPrice("AAPL", 150.00);
        System.out.println();
        exchange.updateStockPrice("AAPL", 160.00); // +6.6% spike!
        System.out.println();

        // Unsubscribe mobile app
        System.out.println("--- Unsubscribing MobileApp ---");
        exchange.unsubscribe("PRICE_CHANGE", mobileApp);
        System.out.println();

        exchange.updateStockPrice("AAPL", 155.00);

        System.out.println();
    }
}
