package org.systemdesign.chess.domain.game;

import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.Position;
import org.systemdesign.chess.domain.piece.Piece;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the chessboard state and manages piece placement and movement.
 *
 * Responsibilities:
 * 1. Maintain piece positions using a Map<Position, Piece>
 * 2. Provide piece lookup by position
 * 3. Place, move, and remove pieces
 * 4. Support board initialization and copying
 * 5. Track castling rights for both colors
 *
 * Mutability: Board is mutable to support game progression.
 */
public class Board {
    private static final int BOARD_SIZE = 8;

    private final Map<Position, Piece> pieces;
    private final Map<Color, CastlingRights> castlingRights;

    public Board() {
        this.pieces = new HashMap<>();
        this.castlingRights = new HashMap<>();
        initializeCastlingRights();
    }

    /**
     * Copy constructor for creating a deep copy of the board.
     * Useful for move simulation.
     */
    public Board(Board other) {
        this.pieces = new HashMap<>(other.pieces);
        this.castlingRights = new HashMap<>();
        for (Color color : Color.values()) {
            this.castlingRights.put(color, new CastlingRights(other.castlingRights.get(color)));
        }
    }

    /**
     * Initialize castling rights (both sides can castle initially).
     */
    private void initializeCastlingRights() {
        castlingRights.put(Color.WHITE, new CastlingRights(true, true));
        castlingRights.put(Color.BLACK, new CastlingRights(true, true));
    }

    /**
     * Returns the piece at the given position, or null if empty.
     */
    public Piece getPieceAt(Position position) {
        return pieces.get(position);
    }

    /**
     * Places a piece at the given position. Updates position if piece already exists elsewhere.
     */
    public void placePiece(Piece piece, Position position) {
        // Remove piece from old position if it exists
        pieces.values().removeIf(p -> p.equals(piece));

        pieces.put(position, piece);
        piece.setPosition(position);
    }

    /**
     * Removes a piece from the board (e.g., during capture).
     */
    public void removePiece(Position position) {
        pieces.remove(position);
    }

    /**
     * Checks if a position is empty.
     */
    public boolean isPositionEmpty(Position position) {
        return !pieces.containsKey(position);
    }

    /**
     * Gets all pieces of a specific color.
     */
    public java.util.Collection<Piece> getPiecesByColor(Color color) {
        return pieces.values().stream()
            .filter(p -> p.getColor() == color)
            .toList();
    }

    /**
     * Finds the king of the given color.
     */
    public Piece getKing(Color color) {
        return pieces.values().stream()
            .filter(p -> p.getColor() == color && p.getType() == ChessPieceType.KING)
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets castling rights for a color.
     */
    public CastlingRights getCastlingRights(Color color) {
        return castlingRights.get(color);
    }

    /**
     * Updates castling rights (e.g., after king or rook moves).
     */
    public void updateCastlingRights(Color color, boolean kingside, boolean queenside) {
        castlingRights.put(color, new CastlingRights(kingside, queenside));
    }

    /**
     * Returns a copy of all pieces for iteration.
     */
    public java.util.Collection<Piece> getAllPieces() {
        return new java.util.ArrayList<>(pieces.values());
    }

    /**
     * Resets the board to initial position.
     */
    public void initializeStandardPosition() {
        pieces.clear();
        initializeCastlingRights();

        // TODO: Initialize pieces in starting positions
        // This will be called by PieceFactory or Game initialization
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                Position pos = new Position(row, col);
                Piece piece = getPieceAt(pos);
                if (piece != null) {
                    sb.append(getPieceSymbol(piece)).append(" ");
                } else {
                    sb.append(". ");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String getPieceSymbol(Piece piece) {
        String symbol = switch (piece.getType()) {
            case PAWN -> "P";
            case KNIGHT -> "N";
            case BISHOP -> "B";
            case ROOK -> "R";
            case QUEEN -> "Q";
            case KING -> "K";
        };
        return piece.getColor() == Color.WHITE ? symbol : symbol.toLowerCase();
    }

    /**
     * Inner class to represent castling rights.
     */
    public static class CastlingRights {
        private boolean kingside;
        private boolean queenside;

        public CastlingRights(boolean kingside, boolean queenside) {
            this.kingside = kingside;
            this.queenside = queenside;
        }

        public CastlingRights(CastlingRights other) {
            this.kingside = other.kingside;
            this.queenside = other.queenside;
        }

        public boolean canCastleKingside() {
            return kingside;
        }

        public boolean canCastleQueenside() {
            return queenside;
        }

        public void disableBothSides() {
            this.kingside = false;
            this.queenside = false;
        }

        public void disableKingside() {
            this.kingside = false;
        }

        public void disableQueenside() {
            this.queenside = false;
        }
    }
}
