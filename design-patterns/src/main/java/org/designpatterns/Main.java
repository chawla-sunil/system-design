package org.designpatterns;

import org.designpatterns.creational.singleton.SingletonDemo;
import org.designpatterns.creational.factory.FactoryMethodDemo;
import org.designpatterns.creational.abstractfactory.AbstractFactoryDemo;
import org.designpatterns.creational.builder.BuilderDemo;
import org.designpatterns.creational.prototype.PrototypeDemo;

import org.designpatterns.structural.adapter.AdapterDemo;
import org.designpatterns.structural.bridge.BridgeDemo;
import org.designpatterns.structural.composite.CompositeDemo;
import org.designpatterns.structural.decorator.DecoratorDemo;
import org.designpatterns.structural.facade.FacadeDemo;
import org.designpatterns.structural.flyweight.FlyweightDemo;
import org.designpatterns.structural.proxy.ProxyDemo;

import org.designpatterns.behavioral.strategy.StrategyDemo;
import org.designpatterns.behavioral.observer.ObserverDemo;
import org.designpatterns.behavioral.command.CommandDemo;
import org.designpatterns.behavioral.chainofresponsibility.ChainOfResponsibilityDemo;
import org.designpatterns.behavioral.iterator.IteratorDemo;
import org.designpatterns.behavioral.mediator.MediatorDemo;
import org.designpatterns.behavioral.state.StateDemo;
import org.designpatterns.behavioral.templatemethod.TemplateMethodDemo;
import org.designpatterns.behavioral.visitor.VisitorDemo;
import org.designpatterns.behavioral.memento.MementoDemo;
import org.designpatterns.behavioral.interpreter.InterpreterDemo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Design Patterns Demo Runner
 *
 * Run with no arguments to see all patterns, or pass a pattern name to run a specific one.
 * Example: java Main singleton
 * Example: java Main (runs all)
 */
public class Main {

    private static final Map<String, Runnable> DEMOS = new LinkedHashMap<>();

    static {
        // Creational Patterns
        DEMOS.put("singleton", SingletonDemo::run);
        DEMOS.put("factory", FactoryMethodDemo::run);
        DEMOS.put("abstractfactory", AbstractFactoryDemo::run);
        DEMOS.put("builder", BuilderDemo::run);
        DEMOS.put("prototype", PrototypeDemo::run);

        // Structural Patterns
        DEMOS.put("adapter", AdapterDemo::run);
        DEMOS.put("bridge", BridgeDemo::run);
        DEMOS.put("composite", CompositeDemo::run);
        DEMOS.put("decorator", DecoratorDemo::run);
        DEMOS.put("facade", FacadeDemo::run);
        DEMOS.put("flyweight", FlyweightDemo::run);
        DEMOS.put("proxy", ProxyDemo::run);

        // Behavioral Patterns
        DEMOS.put("strategy", StrategyDemo::run);
        DEMOS.put("observer", ObserverDemo::run);
        DEMOS.put("command", CommandDemo::run);
        DEMOS.put("chain", ChainOfResponsibilityDemo::run);
        DEMOS.put("iterator", IteratorDemo::run);
        DEMOS.put("mediator", MediatorDemo::run);
        DEMOS.put("state", StateDemo::run);
        DEMOS.put("templatemethod", TemplateMethodDemo::run);
        DEMOS.put("visitor", VisitorDemo::run);
        DEMOS.put("memento", MementoDemo::run);
        DEMOS.put("interpreter", InterpreterDemo::run);
    }

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      DESIGN PATTERNS - DEMO RUNNER      ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        if (args.length > 0) {
            String pattern = args[0].toLowerCase();
            Runnable demo = DEMOS.get(pattern);
            if (demo != null) {
                demo.run();
            } else {
                System.out.println("Unknown pattern: " + pattern);
                System.out.println("Available: " + String.join(", ", DEMOS.keySet()));
            }
        } else {
            System.out.println("Running all " + DEMOS.size() + " design pattern demos...\n");
            DEMOS.forEach((name, demo) -> {
                try {
                    demo.run();
                } catch (Exception e) {
                    System.out.println("[ERROR] " + name + ": " + e.getMessage());
                }
            });
            System.out.println("All demos completed.");
        }
    }
}
