package org.designpatterns.structural.flyweight;

public class CharacterStyle {
    private final String fontFamily;
    private final int fontSize;
    private final String color;

    public CharacterStyle(String fontFamily, int fontSize, String color) {
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        this.color = color;
        System.out.println("  [Created] CharacterStyle: " + this);
    }

    public void render(char character, int row, int col) {
        System.out.println("  Rendering '" + character + "' at (" + row + "," + col + ") with " + fontFamily + "/" + fontSize + "pt/" + color);
    }

    @Override
    public String toString() {
        return fontFamily + "/" + fontSize + "pt/" + color;
    }
}
