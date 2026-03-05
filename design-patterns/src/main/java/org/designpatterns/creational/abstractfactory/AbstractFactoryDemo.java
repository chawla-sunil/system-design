package org.designpatterns.creational.abstractfactory;

public class AbstractFactoryDemo {

    public static void run() {
        System.out.println("=== ABSTRACT FACTORY PATTERN DEMO ===\n");

        // Client code works with factories and products via abstract interfaces
        System.out.println("--- Windows UI ---");
        renderUI(new WindowsUIFactory());

        System.out.println("\n--- Mac UI ---");
        renderUI(new MacUIFactory());

        System.out.println();
    }

    /** Client code: only depends on abstract interfaces, not concrete classes */
    private static void renderUI(UIFactory factory) {
        Button button = factory.createButton();
        Checkbox checkbox = factory.createCheckbox();

        button.render();
        button.onClick("Submit form");
        checkbox.render();
        System.out.println("  Checkbox checked: " + checkbox.isChecked());
    }
}
