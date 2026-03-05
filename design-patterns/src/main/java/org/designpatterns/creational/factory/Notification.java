package org.designpatterns.creational.factory;

/**
 * Factory Method Pattern - Notification System Example
 *
 * Defines an interface for creating objects but lets subclasses decide which class to instantiate.
 */
public interface Notification {
    void notifyUser(String message);
    String getChannel();
}
