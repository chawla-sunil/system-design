package org.tictactoe.strategy;

import org.tictactoe.model.Move;
import org.tictactoe.model.enums.PieceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Checks if a player has filled the main diagonal (top-left → bottom-right).
 *
 * Interview note: A cell (row, col) is on the main diagonal if row == col.
 * We only count moves on the diagonal — all other moves are ignored.
 *
 * Time: O(1) per move. Space: O(numPlayers).
 */
public class DiagonalWinStrategy implements WinningStrategy {

    // pieceType → count of pieces on the main diagonal
    private final Map<PieceType, Integer> diagCounts = new HashMap<>();

    @Override
    public boolean registerMove(Move move, int boardSize) {
        if (move.row() != move.col()) {
            return false; // not on the main diagonal
        }
        int count = diagCounts.merge(move.player().getPieceType(), 1, Integer::sum);
        return count == boardSize;
    }

    @Override
    public void unregisterMove(Move move, int boardSize) {
        if (move.row() != move.col()) {
            return;
        }
        diagCounts.computeIfPresent(move.player().getPieceType(), (k, v) -> v - 1);
    }
}

