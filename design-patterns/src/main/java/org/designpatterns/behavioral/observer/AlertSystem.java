package org.designpatterns.behavioral.observer;

public class AlertSystem implements EventListener {

    @Override
    public void update(String eventType, String data) {
        System.out.println("  [ALERT] *** " + eventType + ": " + data + " ***");
    }

    @Override
    public String getName() { return "AlertSystem"; }
}
