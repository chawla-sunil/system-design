package org.systemdesign.chess.domain;

/**
 * Enum representing the types of chess pieces.
 */
public enum ChessPieceType {
    PAWN(1),
    KNIGHT(3),
    BISHOP(3),
    ROOK(5),
    QUEEN(9),
    KING(Integer.MAX_VALUE);

    private final int value; // Material value for evaluation

    ChessPieceType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

