package org.tictactoe.strategy;

import org.tictactoe.model.Move;
import org.tictactoe.model.enums.PieceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Checks if a player has filled the anti-diagonal (top-right → bottom-left).
 *
 * Interview note: A cell (row, col) is on the anti-diagonal if row + col == boardSize - 1.
 *
 * Example on a 3×3 board: cells (0,2), (1,1), (2,0) are on the anti-diagonal.
 *
 * Time: O(1) per move. Space: O(numPlayers).
 */
public class AntiDiagonalWinStrategy implements WinningStrategy {

    // pieceType → count of pieces on the anti-diagonal
    private final Map<PieceType, Integer> antiDiagCounts = new HashMap<>();

    @Override
    public boolean registerMove(Move move, int boardSize) {
        if (move.row() + move.col() != boardSize - 1) {
            return false; // not on the anti-diagonal
        }
        int count = antiDiagCounts.merge(move.player().getPieceType(), 1, Integer::sum);
        return count == boardSize;
    }

    @Override
    public void unregisterMove(Move move, int boardSize) {
        if (move.row() + move.col() != boardSize - 1) {
            return;
        }
        antiDiagCounts.computeIfPresent(move.player().getPieceType(), (k, v) -> v - 1);
    }
}

