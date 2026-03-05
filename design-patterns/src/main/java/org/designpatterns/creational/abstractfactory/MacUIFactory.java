package org.designpatterns.creational.abstractfactory;

/** Concrete Factory: Creates Mac UI components */
public class MacUIFactory implements UIFactory {
    @Override
    public Button createButton() {
        return new MacButton();
    }

    @Override
    public Checkbox createCheckbox() {
        return new MacCheckbox();
    }
}
