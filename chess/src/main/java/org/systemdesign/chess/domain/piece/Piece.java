package org.systemdesign.chess.domain.piece;

import org.systemdesign.chess.domain.game.Board;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Position;

import java.util.Set;

/**
 * Abstract base class for all chess pieces.
 *
 * Each piece type (Pawn, Knight, Bishop, Rook, Queen, King) is responsible for:
 * 1. Defining how it moves (getValidMoves)
 * 2. Defining what it can capture
 *
 * Uses the Strategy Pattern to allow different movement logic per piece type.
 */
public abstract class Piece {
    private final Color color;
    private final ChessPieceType type;
    private Position position;

    public Piece(Color color, ChessPieceType type, Position position) {
        this.color = color;
        this.type = type;
        this.position = position;
    }

    public Color getColor() {
        return color;
    }

    public ChessPieceType getType() {
        return type;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    /**
     * Returns all valid moves for this piece at its current position on the given board.
     * This method must account for the board state (obstacles, other pieces, etc.).
     *
     * Note: This returns pseudo-legal moves. The Game class is responsible for
     * filtering out moves that would leave the king in check.
     *
     * @param board the current board state
     * @return set of valid moves for this piece
     */
    public abstract Set<Position> getValidMoves(Board board);

    /**
     * Checks if this piece can capture an opponent at the given position.
     * Useful for check/checkmate detection.
     *
     * @param target target position to check
     * @param board the current board state
     * @return true if this piece can capture at the target position
     */
    public abstract boolean canCaptureAt(Position target, Board board);

    /**
     * Helper method to check if a position is occupied by an opponent's piece.
     */
    protected boolean isOpponentAtPosition(Position pos, Board board) {
        Piece piece = board.getPieceAt(pos);
        return piece != null && piece.getColor() != this.color;
    }

    /**
     * Helper method to check if a position is empty.
     */
    protected boolean isPositionEmpty(Position pos, Board board) {
        return board.getPieceAt(pos) == null;
    }

    @Override
    public String toString() {
        return color + " " + type;
    }
}
