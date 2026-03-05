package org.designpatterns.structural.flyweight;

public class TextCharacter {
    private final char character;
    private final int row;
    private final int col;
    private final CharacterStyle style; // shared flyweight

    public TextCharacter(char character, int row, int col, CharacterStyle style) {
        this.character = character;
        this.row = row;
        this.col = col;
        this.style = style;
    }

    public void render() {
        style.render(character, row, col);
    }
}
