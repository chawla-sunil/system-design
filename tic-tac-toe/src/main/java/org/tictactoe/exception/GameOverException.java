package org.tictactoe.exception;

/**
 * Thrown when a move is attempted after the game has already ended.
 */
public class GameOverException extends RuntimeException {

    public GameOverException(String message) {
        super(message);
    }
}

