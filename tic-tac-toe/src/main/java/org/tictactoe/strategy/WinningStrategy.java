package org.tictactoe.strategy;

import org.tictactoe.model.Move;

/**
 * Strategy interface for checking win conditions.
 *
 * Interview note: Each implementation checks ONE way to win (row, column,
 * diagonal, anti-diagonal). This follows SRP and makes it easy to add
 * custom win conditions without modifying existing code (Open/Closed Principle).
 *
 * The key design choice: strategies maintain O(1) counters instead of
 * scanning the board. registerMove/unregisterMove enable undo support.
 */
public interface WinningStrategy {

    /**
     * Register a move — update internal counters.
     * Called when a piece is placed on the board.
     *
     * @return true if this move results in a win for the player
     */
    boolean registerMove(Move move, int boardSize);

    /**
     * Unregister a move — reverse internal counters.
     * Called when a move is undone.
     */
    void unregisterMove(Move move, int boardSize);
}

