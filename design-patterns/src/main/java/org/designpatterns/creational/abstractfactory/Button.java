package org.designpatterns.creational.abstractfactory;

/** Abstract Product: Button */
public interface Button {
    void render();
    void onClick(String action);
}
