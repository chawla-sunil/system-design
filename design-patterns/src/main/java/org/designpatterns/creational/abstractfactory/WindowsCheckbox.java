package org.designpatterns.creational.abstractfactory;

public class WindowsCheckbox implements Checkbox {
    private boolean checked = false;

    @Override
    public void render() {
        System.out.println("  Rendering Windows-style checkbox [" + (checked ? "X" : " ") + "]");
    }

    @Override
    public boolean isChecked() {
        return checked;
    }
}
