package org.tictactoe.model.enums;

/**
 * Represents the type of piece a player uses.
 * Interview note: Using enum ensures type-safety and prevents invalid piece types.
 */
public enum PieceType {
    X, O;

    public String symbol() {
        return this.name();
    }
}

