package org.systemdesign.chess.domain.piece;

import org.systemdesign.chess.domain.game.Board;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Position;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a Pawn piece.
 *
 * Pawn movement rules:
 * - Moves forward 1 square (or 2 squares on first move)
 * - Captures diagonally forward
 * - En passant capture (special move)
 * - Promotion on reaching the opposite end
 */
public class Pawn extends Piece {
    private boolean hasMoved = false; // Track if pawn has moved (for initial 2-square move)

    public Pawn(Color color, Position position) {
        super(color, ChessPieceType.PAWN, position);
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

        // Determine direction: white moves up (row decreases), black moves down (row increases)
        int direction = getColor() == Color.WHITE ? -1 : 1;

        // Move forward 1 square
        int newRow = row + direction;
        if (Position.isValid(newRow, col)) {
            Position oneSquareForward = new Position(newRow, col);
            if (isPositionEmpty(oneSquareForward, board)) {
                validMoves.add(oneSquareForward);

                // Move forward 2 squares on first move
                if (!hasMoved) {
                    int twoSquareRow = row + 2 * direction;
                    Position twoSquaresForward = new Position(twoSquareRow, col);
                    if (isPositionEmpty(twoSquaresForward, board)) {
                        validMoves.add(twoSquaresForward);
                    }
                }
            }
        }

        // Capture diagonally
        for (int deltaCol : new int[]{-1, 1}) {
            int newCol = col + deltaCol;
            if (Position.isValid(newRow, newCol)) {
                Position diagonalPos = new Position(newRow, newCol);
                if (isOpponentAtPosition(diagonalPos, board)) {
                    validMoves.add(diagonalPos);
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

