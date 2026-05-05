package org.systemdesign.chess.domain;

/**
 * Enum representing the color of a chess piece.
 * WHITE and BLACK represent the two players.
 */
public enum Color {
    WHITE,
    BLACK;

    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}

