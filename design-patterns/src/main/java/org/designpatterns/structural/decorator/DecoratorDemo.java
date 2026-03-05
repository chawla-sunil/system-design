package org.designpatterns.structural.decorator;

public class DecoratorDemo {
    public static void run() {
        System.out.println("=== DECORATOR PATTERN DEMO ===\n");

        // Plain coffee
        Coffee coffee = new SimpleCoffee();
        System.out.println(coffee.getDescription() + " -> $" + String.format("%.2f", coffee.getCost()));

        // Coffee with milk
        Coffee milkCoffee = new MilkDecorator(new SimpleCoffee());
        System.out.println(milkCoffee.getDescription() + " -> $" + String.format("%.2f", milkCoffee.getCost()));

        // Coffee with milk, sugar, and whipped cream (stacking decorators)
        Coffee fancyCoffee = new WhippedCreamDecorator(
                new SugarDecorator(
                        new MilkDecorator(
                                new SimpleCoffee())));
        System.out.println(fancyCoffee.getDescription() + " -> $" + String.format("%.2f", fancyCoffee.getCost()));

        // Double milk coffee
        Coffee doubleMilk = new MilkDecorator(new MilkDecorator(new SimpleCoffee()));
        System.out.println(doubleMilk.getDescription() + " -> $" + String.format("%.2f", doubleMilk.getCost()));

        System.out.println();
    }
}
