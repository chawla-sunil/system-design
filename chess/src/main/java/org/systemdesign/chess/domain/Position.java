package org.systemdesign.chess.domain;

import java.util.Objects;

/**
 * Immutable value object representing a position on the chessboard.
 * Position is uniquely identified by row and column (0-7).
 *
 * This immutable design allows Position to be used reliably in Sets and Maps,
 * and ensures thread-safety.
 */
public class Position {
    private final int row;  // 0-7, where 0 is rank 1 (white's side)
    private final int col;  // 0-7, where 0 is file 'a'

    /**
     * Creates a Position at the given row and column.
     *
     * @param row Row (0-7, where 0 is rank 1)
     * @param col Column (0-7, where 0 is file 'a')
     * @throws IllegalArgumentException if position is outside the board
     */
    public Position(int row, int col) {
        if (row < 0 || row > 7 || col < 0 || col > 7) {
            throw new IllegalArgumentException(
                String.format("Invalid position: row=%d, col=%d. Must be in range [0,7]", row, col)
            );
        }
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    /**
     * Checks if this position is within the chessboard boundaries.
     */
    public static boolean isValid(int row, int col) {
        return row >= 0 && row <= 7 && col >= 0 && col <= 7;
    }

    /**
     * Returns the algebraic notation of this position (e.g., "a1", "e4", "h8").
     */
    public String toAlgebraic() {
        char file = (char) ('a' + col);
        int rank = 8 - row; // Rank 8 is at row 0
        return "" + file + rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return row == position.row && col == position.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @Override
    public String toString() {
        return toAlgebraic();
    }
}

