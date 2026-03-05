package org.designpatterns.behavioral.observer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockExchange {
    private final Map<String, List<EventListener>> listeners = new HashMap<>();
    private final Map<String, Double> stockPrices = new HashMap<>();

    public void subscribe(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
        System.out.println("  [Exchange] " + listener.getName() + " subscribed to: " + eventType);
    }

    public void unsubscribe(String eventType, EventListener listener) {
        List<EventListener> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            System.out.println("  [Exchange] " + listener.getName() + " unsubscribed from: " + eventType);
        }
    }

    public void updateStockPrice(String symbol, double price) {
        double oldPrice = stockPrices.getOrDefault(symbol, 0.0);
        stockPrices.put(symbol, price);

        String data = symbol + ": $" + String.format("%.2f", oldPrice) + " -> $" + String.format("%.2f", price);
        notifyListeners("PRICE_CHANGE", data);

        if (price > oldPrice * 1.05) {
            notifyListeners("PRICE_SPIKE", data + " [SPIKE +5%]");
        }
    }

    private void notifyListeners(String eventType, String data) {
        List<EventListener> eventListeners = listeners.getOrDefault(eventType, List.of());
        for (EventListener listener : eventListeners) {
            listener.update(eventType, data);
        }
    }
}
