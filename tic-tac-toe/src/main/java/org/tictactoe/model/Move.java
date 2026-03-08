package org.tictactoe.model;

/**
 * Immutable record representing a single move in the game.
 *
 * Interview note: Using Java record for immutability. A move is a historical
 * fact — once recorded, it should never change. This also makes move history
 * safe to iterate and replay (Event Sourcing-style).
 */
public record Move(int row, int col, Player player) {

    @Override
    public String toString() {
        return player.getName() + " → (" + row + "," + col + ")";
    }
}

