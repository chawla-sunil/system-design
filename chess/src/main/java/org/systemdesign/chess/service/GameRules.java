package org.systemdesign.chess.service;

import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.Position;
import org.systemdesign.chess.domain.game.Board;
import org.systemdesign.chess.domain.game.Move;
import org.systemdesign.chess.domain.game.MoveType;
import org.systemdesign.chess.domain.piece.King;
import org.systemdesign.chess.domain.piece.Piece;
import org.systemdesign.chess.domain.piece.Rook;

import java.util.Set;

/**
 * Encapsulates all chess rules and validation logic.
 *
 * Responsibilities:
 * 1. Check/checkmate/stalemate detection
 * 2. Move validation (including pin detection)
 * 3. Castling eligibility
 * 4. En passant validation
 * 5. Special move detection
 *
 * This class separates rule logic from game state management,
 * following Single Responsibility Principle.
 */
public class GameRules {

    /**
     * Determines if a given color is in check.
     * A player is in check if their king can be captured by an opponent's piece.
     *
     * Algorithm:
     * 1. Find the king
     * 2. For each opponent piece, check if it can attack the king
     * 3. If any piece can attack, color is in check
     */
    public static boolean isInCheck(Color color, Board board) {
        King king = (King) board.getKing(color);
        if (king == null) return false;

        Position kingPos = king.getPosition();
        Color opponentColor = color.opposite();

        // Check if any opponent piece can capture the king
        for (Piece piece : board.getPiecesByColor(opponentColor)) {
            if (piece.canCaptureAt(kingPos, board)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if a color is in checkmate.
     *
     * Checkmate conditions:
     * 1. King is in check
     * 2. No legal moves available
     *
     * Algorithm:
     * 1. Check if in check
     * 2. For each of player's pieces, try each possible move
     * 3. Simulate the move and check if king is still in check
     * 4. If any legal move exists, not checkmate
     */
    public static boolean isCheckmate(Color color, Board board) {
        // Must be in check for checkmate
        if (!isInCheck(color, board)) {
            return false;
        }

        // Check if there's any legal move to escape check
        return !hasLegalMove(color, board);
    }

    /**
     * Determines if a color is in stalemate.
     *
     * Stalemate conditions:
     * 1. King is NOT in check
     * 2. No legal moves available
     *
     * Stalemate results in a draw.
     */
    public static boolean isStalemate(Color color, Board board) {
        // Must NOT be in check for stalemate
        if (isInCheck(color, board)) {
            return false;
        }

        // Check if there's any legal move
        return !hasLegalMove(color, board);
    }

    /**
     * Helper method: checks if a color has at least one legal move.
     */
    private static boolean hasLegalMove(Color color, Board board) {
        for (Piece piece : board.getPiecesByColor(color)) {
            Set<Position> canditateMoves = piece.getValidMoves(board);
            for (Position target : canditateMoves) {
                // Simulate the move
                if (isMoveLegal(piece.getPosition(), target, board, color)) {
                    return true; // Found a legal move
                }
            }
        }
        return false; // No legal moves available
    }

    /**
     * Validates if a move is legal (doesn't leave king in check).
     *
     * Algorithm:
     * 1. Simulate the move on a copy of the board
     * 2. Check if king is in check after the move
     * 3. If king is safe, move is legal
     */
    public static boolean isMoveLegal(Position from, Position to, Board board, Color movingColor) {
        // Create a copy of the board to simulate the move
        Board boardCopy = new Board(board);

        // Get the piece
        Piece piece = boardCopy.getPieceAt(from);
        if (piece == null || piece.getColor() != movingColor) {
            return false;
        }

        // Get target (might be opponent's piece)
        Piece target = boardCopy.getPieceAt(to);

        // Execute the move on the copy
        boardCopy.placePiece(piece, to);
        if (target != null) {
            boardCopy.removePiece(to);
        }

        // Check if king is in check after the move
        boolean kingInCheck = isInCheck(movingColor, boardCopy);

        return !kingInCheck; // Legal if king is safe
    }

    /**
     * Validates if a move is legal considering piece-specific rules.
     */
    public static boolean isMoveValid(Move move, Board board) {
        Position from = move.getFrom();
        Position to = move.getTo();
        Piece piece = move.getPiece();

        // Check if piece belongs to the correct player (checked by Game usually)
        // Check if target is in piece's valid moves
        Set<Position> validMoves = piece.getValidMoves(board);
        if (!validMoves.contains(to)) {
            return false;
        }

        // Check if move leaves king in check
        return isMoveLegal(from, to, board, piece.getColor());
    }

    /**
     * Checks if castling is legal for the given player.
     *
     * Castling requirements:
     * 1. King hasn't moved
     * 2. Rook hasn't moved
     * 3. No pieces between king and rook
     * 4. King is not in check
     * 5. King doesn't move through or into check
     */
    public static boolean canCastle(Color color, boolean kingside, Board board) {
        King king = (King) board.getKing(color);
        if (king == null || king.isHasMoved()) {
            return false;
        }

        // King cannot castle while in check
        if (isInCheck(color, board)) {
            return false;
        }

        Board.CastlingRights rights = board.getCastlingRights(color);
        if (kingside && !rights.canCastleKingside()) {
            return false;
        }
        if (!kingside && !rights.canCastleQueenside()) {
            return false;
        }

        int kingRow = king.getPosition().getRow();
        int kingCol = king.getPosition().getCol();
        int rookCol = kingside ? 7 : 0;

        // Check for pieces between king and rook
        int startCol = kingside ? kingCol + 1 : 1;
        int endCol = kingside ? rookCol : kingCol;
        for (int col = startCol; col < endCol; col++) {
            if (!board.isPositionEmpty(new Position(kingRow, col))) {
                return false;
            }
        }

        // Check rook exists and hasn't moved
        Piece rook = board.getPieceAt(new Position(kingRow, rookCol));
        if (!(rook instanceof Rook) || rook.getColor() != color || ((Rook) rook).isHasMoved()) {
            return false;
        }

        // Check if king moves through check
        Position intermediatPosition = new Position(kingRow, kingside ? kingCol + 1 : kingCol - 1);
        Board boardCopy = new Board(board);
        boardCopy.placePiece(king, intermediatPosition);
        if (isInCheck(color, boardCopy)) {
            return false;
        }

        return true;
    }

    /**
     * Determines the type of move being made.
     */
    public static MoveType determineMoveType(Position from, Position to, Piece piece,
                                             Piece capturedPiece, Board board) {
        if (piece.getType() == ChessPieceType.KING) {
            // Check for castling
            if (Math.abs(to.getCol() - from.getCol()) == 2) {
                return MoveType.CASTLING;
            }
        }

        if (piece.getType() == ChessPieceType.PAWN) {
            // Check for promotion
            if ((piece.getColor() == Color.WHITE && to.getRow() == 0) ||
                (piece.getColor() == Color.BLACK && to.getRow() == 7)) {
                return MoveType.PROMOTION;
            }

            // Check for en passant (pawn moves diagonally but no piece captured)
            if (capturedPiece == null && from.getCol() != to.getCol()) {
                return MoveType.EN_PASSANT;
            }
        }

        return capturedPiece != null ? MoveType.NORMAL : MoveType.NORMAL;
    }
}
