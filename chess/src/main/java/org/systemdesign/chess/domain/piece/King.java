package org.systemdesign.chess.domain.piece;

import org.systemdesign.chess.domain.game.Board;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Position;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a King piece.
 *
 * King movement rules:
 * - Moves 1 square in any direction (horizontal, vertical, diagonal)
 * - Cannot move into check
 * - Can castle with rook (special move)
 * - Most important piece (losing it means checkmate)
 */
public class King extends Piece {
    private boolean hasMoved = false; // Track for castling eligibility

    // All 8 adjacent squares
    private static final int[][] KING_MOVES = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1}
    };

    public King(Color color, Position position) {
        super(color, ChessPieceType.KING, position);
    }

    public boolean isHasMoved() {
        return hasMoved;
    }

    public void setHasMoved(boolean hasMoved) {
        this.hasMoved = hasMoved;
    }

    @Override
    public Set<Position> getValidMoves(Board board) {
        Set<Position> validMoves = new HashSet<>();
        Position currentPos = getPosition();
        int row = currentPos.getRow();
        int col = currentPos.getCol();

        for (int[] move : KING_MOVES) {
            int newRow = row + move[0];
            int newCol = col + move[1];

            if (Position.isValid(newRow, newCol)) {
                Position targetPos = new Position(newRow, newCol);
                // King can move to empty square or capture opponent
                if (isPositionEmpty(targetPos, board) || isOpponentAtPosition(targetPos, board)) {
                    validMoves.add(targetPos);
                }
            }
        }

        // Castling moves will be added by GameRules, not here
        // (because castling requires additional validation)

        return validMoves;
    }

    @Override
    public boolean canCaptureAt(Position target, Board board) {
        Set<Position> moves = getValidMoves(board);
        return moves.contains(target) && isOpponentAtPosition(target, board);
    }
}

