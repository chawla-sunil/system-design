package org.systemdesign.chess.domain.game;

import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Position;
import org.systemdesign.chess.domain.piece.Piece;

import java.util.Objects;

/**
 * Immutable class representing a single move in the game.
 *
 * A Move encapsulates:
 * - Source and target positions
 * - Piece being moved
 * - Captured piece (if any)
 * - Move type (normal, castling, en passant, promotion)
 * - Promotion piece (if applicable)
 *
 * Immutability ensures:
 * 1. Move history is safe (no accidental mutations)
 * 2. Can be used as keys in Maps/Sets
 * 3. Thread-safe
 * 4. Enables reliable undo/replay functionality
 */
public class Move {
    private final Position from;
    private final Position to;
    private final Piece piece;
    private final Piece capturedPiece;
    private final MoveType moveType;
    private final ChessPieceType promotionPiece; // Only for PROMOTION moves

    public Move(Position from, Position to, Piece piece) {
        this(from, to, piece, null, MoveType.NORMAL, null);
    }

    public Move(Position from, Position to, Piece piece, Piece capturedPiece,
                MoveType moveType, ChessPieceType promotionPiece) {
        this.from = Objects.requireNonNull(from, "from position cannot be null");
        this.to = Objects.requireNonNull(to, "to position cannot be null");
        this.piece = Objects.requireNonNull(piece, "piece cannot be null");
        this.capturedPiece = capturedPiece;
        this.moveType = Objects.requireNonNull(moveType, "moveType cannot be null");
        this.promotionPiece = promotionPiece;
    }

    public Position getFrom() {
        return from;
    }

    public Position getTo() {
        return to;
    }

    public Piece getPiece() {
        return piece;
    }

    public Piece getCapturedPiece() {
        return capturedPiece;
    }

    public MoveType getMoveType() {
        return moveType;
    }

    public ChessPieceType getPromotionPiece() {
        return promotionPiece;
    }

    public boolean isCapture() {
        return capturedPiece != null;
    }

    /**
     * Returns algebraic notation for this move.
     * Examples: "e4", "Nf3", "O-O" (castling), "e8=Q" (promotion)
     */
    public String getAlgebraicNotation() {
        if (moveType == MoveType.CASTLING) {
            // King-side castling (O-O) or Queen-side castling (O-O-O)
            return to.getCol() > from.getCol() ? "O-O" : "O-O-O";
        }

        StringBuilder notation = new StringBuilder();

        // Add piece symbol (except for pawns)
        if (piece.getType() != ChessPieceType.PAWN) {
            notation.append(getPieceSymbol(piece.getType()));
        }

        // Add source file if it's a pawn capturing or piece disambiguation is needed
        if (piece.getType() == ChessPieceType.PAWN && isCapture()) {
            notation.append((char) ('a' + from.getCol()));
        }

        // Add capture symbol
        if (isCapture()) {
            notation.append("x");
        }

        // Add destination square
        notation.append(to);

        // Add promotion notation
        if (moveType == MoveType.PROMOTION && promotionPiece != null) {
            notation.append("=").append(getPieceSymbol(promotionPiece));
        }

        return notation.toString();
    }

    private String getPieceSymbol(ChessPieceType type) {
        return switch (type) {
            case PAWN -> "";
            case KNIGHT -> "N";
            case BISHOP -> "B";
            case ROOK -> "R";
            case QUEEN -> "Q";
            case KING -> "K";
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Move move = (Move) o;
        return Objects.equals(from, move.from) &&
               Objects.equals(to, move.to) &&
               moveType == move.moveType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, moveType);
    }

    @Override
    public String toString() {
        return String.format("Move{%s to %s}", from, to);
    }

    /**
     * Builder pattern for creating moves with optional parameters.
     */
    public static class Builder {
        private final Position from;
        private final Position to;
        private final Piece piece;
        private Piece capturedPiece;
        private MoveType moveType = MoveType.NORMAL;
        private ChessPieceType promotionPiece;

        public Builder(Position from, Position to, Piece piece) {
            this.from = from;
            this.to = to;
            this.piece = piece;
        }

        public Builder capturedPiece(Piece capturedPiece) {
            this.capturedPiece = capturedPiece;
            return this;
        }

        public Builder moveType(MoveType moveType) {
            this.moveType = moveType;
            return this;
        }

        public Builder promotionPiece(ChessPieceType promotionPiece) {
            this.promotionPiece = promotionPiece;
            return this;
        }

        public Move build() {
            return new Move(from, to, piece, capturedPiece, moveType, promotionPiece);
        }
    }
}
