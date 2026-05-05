package org.systemdesign.chess.domain.game;

/**
 * Enum representing the status of a chess game.
 */
public enum GameStatus {
    ACTIVE,
    CHECKMATE,
    STALEMATE,
    DRAW_BY_REPETITION,
    DRAW_BY_50_MOVE_RULE,
    RESIGNED
}

