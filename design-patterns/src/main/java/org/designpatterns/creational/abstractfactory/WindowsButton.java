package org.designpatterns.creational.abstractfactory;

public class WindowsButton implements Button {
    @Override
    public void render() {
        System.out.println("  Rendering Windows-style button [====]");
    }

    @Override
    public void onClick(String action) {
        System.out.println("  Windows button clicked: " + action);
    }
}
