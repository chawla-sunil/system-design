package org.systemdesign.chess.factory;

import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.Position;
import org.systemdesign.chess.domain.game.Board;
import org.systemdesign.chess.domain.piece.Bishop;
import org.systemdesign.chess.domain.piece.King;
import org.systemdesign.chess.domain.piece.Knight;
import org.systemdesign.chess.domain.piece.Pawn;
import org.systemdesign.chess.domain.piece.Piece;
import org.systemdesign.chess.domain.piece.Queen;
import org.systemdesign.chess.domain.piece.Rook;

/**
 * Factory class for creating and initializing chess pieces.
 *
 * Uses the Factory Pattern to:
 * 1. Encapsulate piece creation logic
 * 2. Initialize standard chess starting position
 * 3. Provide a centralized place to manage piece instantiation
 */
public class PieceFactory {

    /**
     * Creates a piece of the specified type.
     */
    public Piece createPiece(ChessPieceType type, Color color, Position position) {
        return switch (type) {
            case PAWN -> new Pawn(color, position);
            case KNIGHT -> new Knight(color, position);
            case BISHOP -> new Bishop(color, position);
            case ROOK -> new Rook(color, position);
            case QUEEN -> new Queen(color, position);
            case KING -> new King(color, position);
        };
    }

    /**
     * Initializes the board with the standard chess starting position.
     *
     * Standard position:
     * - Pawns on rank 2 (white) and rank 7 (black)
     * - Back rank pieces in standard order: Rook, Knight, Bishop, Queen, King, Bishop, Knight, Rook
     */
    public void initializeStandardPosition(Board board) {
        // White pieces (rows 6-7 in 0-indexed, where 0 is rank 8)
        // Pawns on rank 2 (row 6)
        for (int col = 0; col < 8; col++) {
            Pawn whitePawn = new Pawn(Color.WHITE, new Position(6, col));
            board.placePiece(whitePawn, new Position(6, col));
        }

        // White back rank (row 7)
        placeBackRank(board, Color.WHITE, 7);

        // Black pieces
        // Black back rank (row 0)
        placeBackRank(board, Color.BLACK, 0);

        // Pawns on rank 7 (row 1)
        for (int col = 0; col < 8; col++) {
            Pawn blackPawn = new Pawn(Color.BLACK, new Position(1, col));
            board.placePiece(blackPawn, new Position(1, col));
        }
    }

    /**
     * Helper method to place the back rank pieces (Rook, Knight, Bishop, Queen, King, etc.)
     */
    private void placeBackRank(Board board, Color color, int row) {
        // Pattern: R, N, B, Q, K, B, N, R
        ChessPieceType[] backRankOrder = {
            ChessPieceType.ROOK,
            ChessPieceType.KNIGHT,
            ChessPieceType.BISHOP,
            ChessPieceType.QUEEN,
            ChessPieceType.KING,
            ChessPieceType.BISHOP,
            ChessPieceType.KNIGHT,
            ChessPieceType.ROOK
        };

        for (int col = 0; col < 8; col++) {
            Piece piece = createPiece(backRankOrder[col], color, new Position(row, col));
            board.placePiece(piece, new Position(row, col));

            // Mark rooks and king as unmoved (for castling eligibility)
            if (piece instanceof Rook) {
                ((Rook) piece).setHasMoved(false);
            } else if (piece instanceof King) {
                ((King) piece).setHasMoved(false);
            }
        }
    }
}
