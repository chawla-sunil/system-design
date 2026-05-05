package org.systemdesign.chess.domain.game;

/**
 * Enum representing different types of moves in chess.
 */
public enum MoveType {
    NORMAL,        // Regular move or capture
    CASTLING,      // King-side or queen-side castling
    EN_PASSANT,    // Pawn capture en passant
    PROMOTION      // Pawn promotion to another piece
}

