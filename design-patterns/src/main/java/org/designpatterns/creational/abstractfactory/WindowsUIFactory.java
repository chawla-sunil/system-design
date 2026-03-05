package org.designpatterns.creational.abstractfactory;

/** Concrete Factory: Creates Windows UI components */
public class WindowsUIFactory implements UIFactory {
    @Override
    public Button createButton() {
        return new WindowsButton();
    }

    @Override
    public Checkbox createCheckbox() {
        return new WindowsCheckbox();
    }
}
