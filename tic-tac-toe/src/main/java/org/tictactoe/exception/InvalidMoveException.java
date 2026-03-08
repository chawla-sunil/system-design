package org.tictactoe.exception;

/**
 * Thrown when a player attempts an invalid move (out of bounds or cell occupied).
 */
public class InvalidMoveException extends RuntimeException {

    public InvalidMoveException(String message) {
        super(message);
    }
}

