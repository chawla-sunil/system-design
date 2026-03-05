package org.designpatterns.creational.abstractfactory;

public class MacButton implements Button {
    @Override
    public void render() {
        System.out.println("  Rendering Mac-style button (====)");
    }

    @Override
    public void onClick(String action) {
        System.out.println("  Mac button clicked: " + action);
    }
}
