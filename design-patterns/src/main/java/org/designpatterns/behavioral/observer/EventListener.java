package org.designpatterns.behavioral.observer;

public interface EventListener {
    void update(String eventType, String data);
    String getName();
}
