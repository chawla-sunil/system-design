package org.tictactoe.validator;

import org.tictactoe.exception.InvalidMoveException;
import org.tictactoe.model.Board;

/**
 * Validates that a move is legal before it's applied.
 *
 * Interview note: Separating validation into its own class follows SRP.
 * If we add new rules (e.g., "can't place adjacent to your last move"),
 * we only modify this class — GameService stays clean.
 */
public class MoveValidator {

    /**
     * Validates a move. Throws InvalidMoveException if invalid.
     *
     * Checks:
     * 1. Row and column are within board bounds
     * 2. The target cell is empty
     */
    public void validate(Board board, int row, int col) {
        if (!board.isWithinBounds(row, col)) {
            throw new InvalidMoveException(
                    String.format("Move (%d,%d) is out of bounds. Board size is %dx%d.",
                            row, col, board.getSize(), board.getSize()));
        }

        if (!board.isCellEmpty(row, col)) {
            throw new InvalidMoveException(
                    String.format("Cell (%d,%d) is already occupied by %s.",
                            row, col, board.getCell(row, col).getPiece().symbol()));
        }
    }
}

