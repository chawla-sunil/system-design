package org.designpatterns.structural.flyweight;

import java.util.ArrayList;
import java.util.List;

public class FlyweightDemo {
    public static void run() {
        System.out.println("=== FLYWEIGHT PATTERN DEMO ===\n");

        CharacterStyleFactory factory = new CharacterStyleFactory();
        List<TextCharacter> document = new ArrayList<>();

        System.out.println("--- Creating character styles (flyweights) ---");
        // Many characters share the same style objects
        String text = "Hello World";
        for (int i = 0; i < text.length(); i++) {
            CharacterStyle style;
            if (Character.isUpperCase(text.charAt(i))) {
                style = factory.getStyle("Arial", 14, "red");     // Bold/heading style
            } else if (text.charAt(i) == ' ') {
                style = factory.getStyle("Arial", 12, "black");   // Normal style
            } else {
                style = factory.getStyle("Arial", 12, "black");   // Normal style
            }
            document.add(new TextCharacter(text.charAt(i), 0, i, style));
        }

        System.out.println("\n--- Rendering document ---");
        for (TextCharacter tc : document) {
            tc.render();
        }

        System.out.println("\nTotal characters: " + document.size());
        System.out.println("Unique style objects (flyweights): " + factory.getCacheSize());
        System.out.println("Memory saved by sharing: " + (document.size() - factory.getCacheSize()) + " objects");

        System.out.println();
    }
}
