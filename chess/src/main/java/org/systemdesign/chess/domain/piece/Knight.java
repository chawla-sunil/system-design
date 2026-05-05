package org.systemdesign.chess.domain.piece;

import org.systemdesign.chess.domain.game.Board;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Position;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a Knight piece.
 *
 * Knight movement rules:
 * - Moves in an L-shape: 2 squares in one direction, 1 square perpendicular
 * - Can jump over other pieces
 * - Has 8 possible moves (or fewer if near board edges)
 */
public class Knight extends Piece {
    // All possible knight moves in (row_delta, col_delta) format
    private static final int[][] KNIGHT_MOVES = {
        {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
        {1, -2}, {1, 2}, {2, -1}, {2, 1}
    };

    public Knight(Color color, Position position) {
        super(color, ChessPieceType.KNIGHT, position);
    }

    @Override
    public Set<Position> getValidMoves(Board board) {
        Set<Position> validMoves = new HashSet<>();
        Position currentPos = getPosition();
        int row = currentPos.getRow();
        int col = currentPos.getCol();

        for (int[] move : KNIGHT_MOVES) {
            int newRow = row + move[0];
            int newCol = col + move[1];

            if (Position.isValid(newRow, newCol)) {
                Position targetPos = new Position(newRow, newCol);
                // Knight can move to empty square or capture opponent
                if (isPositionEmpty(targetPos, board) || isOpponentAtPosition(targetPos, board)) {
                    validMoves.add(targetPos);
                }
            }
        }

        return validMoves;
    }

    @Override
    public boolean canCaptureAt(Position target, Board board) {
        Set<Position> moves = getValidMoves(board);
        return moves.contains(target) && isOpponentAtPosition(target, board);
    }
}

