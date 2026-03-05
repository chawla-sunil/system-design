package org.designpatterns.behavioral.observer;

public class PriceDisplay implements EventListener {
    private final String displayName;

    public PriceDisplay(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public void update(String eventType, String data) {
        System.out.println("  [" + displayName + "] " + eventType + " -> " + data);
    }

    @Override
    public String getName() { return displayName; }
}
