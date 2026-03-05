package org.designpatterns.creational.abstractfactory;

/** Abstract Factory */
public interface UIFactory {
    Button createButton();
    Checkbox createCheckbox();
}
