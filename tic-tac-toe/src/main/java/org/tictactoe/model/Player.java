package org.tictactoe.model;

import org.tictactoe.model.enums.PieceType;

/**
 * Represents a player in the game.
 *
 * Interview note: Player is immutable — once created, the name and piece type
 * don't change. If we needed a BotPlayer, we'd extend this class and add
 * a getMove(Board) method.
 */
public class Player {

    private final String name;
    private final PieceType pieceType;

    public Player(String name, PieceType pieceType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name cannot be null or blank");
        }
        if (pieceType == null) {
            throw new IllegalArgumentException("Piece type cannot be null");
        }
        this.name = name.trim();
        this.pieceType = pieceType;
    }

    public String getName() {
        return name;
    }

    public PieceType getPieceType() {
        return pieceType;
    }

    @Override
    public String toString() {
        return name + "(" + pieceType.symbol() + ")";
    }
}

