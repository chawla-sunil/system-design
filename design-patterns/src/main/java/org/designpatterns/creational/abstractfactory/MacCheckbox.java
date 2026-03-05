package org.designpatterns.creational.abstractfactory;

public class MacCheckbox implements Checkbox {
    private boolean checked = false;

    @Override
    public void render() {
        System.out.println("  Rendering Mac-style checkbox (" + (checked ? "X" : " ") + ")");
    }

    @Override
    public boolean isChecked() {
        return checked;
    }
}
