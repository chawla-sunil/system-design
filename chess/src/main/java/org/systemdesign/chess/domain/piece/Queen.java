package org.systemdesign.chess.domain.piece;

import org.systemdesign.chess.domain.game.Board;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Position;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a Queen piece.
 *
 * Queen movement rules:
 * - Combines Rook (horizontal/vertical) and Bishop (diagonal) movements
 * - Moves any number of squares in any direction
 * - Cannot jump over pieces
 * - Most powerful piece
 */
public class Queen extends Piece {
    // All 8 directions: horizontal, vertical, and diagonal
    private static final int[][] ALL_DIRECTIONS = {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1},      // Rook-like (straight)
        {-1, -1}, {-1, 1}, {1, -1}, {1, 1}     // Bishop-like (diagonal)
    };

    public Queen(Color color, Position position) {
        super(color, ChessPieceType.QUEEN, position);
    }

    @Override
    public Set<Position> getValidMoves(Board board) {
        Set<Position> validMoves = new HashSet<>();
        Position currentPos = getPosition();
        int row = currentPos.getRow();
        int col = currentPos.getCol();

        for (int[] direction : ALL_DIRECTIONS) {
            addMovesInDirection(board, validMoves, row, col, direction[0], direction[1]);
        }

        return validMoves;
    }

    /**
     * Helper method to add all valid moves in a given direction until blocked.
     */
    private void addMovesInDirection(Board board, Set<Position> moves, int row, int col,
                                     int rowDelta, int colDelta) {
        int newRow = row + rowDelta;
        int newCol = col + colDelta;

        while (Position.isValid(newRow, newCol)) {
            Position targetPos = new Position(newRow, newCol);

            if (isPositionEmpty(targetPos, board)) {
                moves.add(targetPos);
            } else if (isOpponentAtPosition(targetPos, board)) {
                moves.add(targetPos);
                break; // Can capture but not move beyond
            } else {
                break; // Hit own piece, stop
            }

            newRow += rowDelta;
            newCol += colDelta;
        }
    }

    @Override
    public boolean canCaptureAt(Position target, Board board) {
        Set<Position> moves = getValidMoves(board);
        return moves.contains(target) && isOpponentAtPosition(target, board);
    }
}

