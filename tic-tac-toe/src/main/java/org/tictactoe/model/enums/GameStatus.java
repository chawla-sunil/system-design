package org.tictactoe.model.enums;

/**
 * Represents the current state of the game.
 */
public enum GameStatus {
    IN_PROGRESS,
    X_WON,
    O_WON,
    DRAW;

    public boolean isOver() {
        return this != IN_PROGRESS;
    }
}

