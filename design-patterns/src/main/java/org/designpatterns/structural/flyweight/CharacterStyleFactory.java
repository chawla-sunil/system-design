package org.designpatterns.structural.flyweight;

import java.util.HashMap;
import java.util.Map;

public class CharacterStyleFactory {
    private final Map<String, CharacterStyle> cache = new HashMap<>();

    public CharacterStyle getStyle(String fontFamily, int fontSize, String color) {
        String key = fontFamily + "-" + fontSize + "-" + color;

        if (!cache.containsKey(key)) {
            cache.put(key, new CharacterStyle(fontFamily, fontSize, color));
        }
        return cache.get(key);
    }

    public int getCacheSize() {
        return cache.size();
    }
}
