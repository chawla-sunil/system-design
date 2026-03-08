package org.tictactoe.strategy;

import org.tictactoe.model.Move;
import org.tictactoe.model.enums.PieceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Checks if a player has filled an entire column.
 *
 * Same O(1) counting approach as RowWinStrategy, but tracks columns.
 */
public class ColumnWinStrategy implements WinningStrategy {

    // pieceType → colCounts[col]
    private final Map<PieceType, int[]> colCounts = new HashMap<>();

    @Override
    public boolean registerMove(Move move, int boardSize) {
        int[] counts = colCounts.computeIfAbsent(
                move.player().getPieceType(), k -> new int[boardSize]);
        counts[move.col()]++;
        return counts[move.col()] == boardSize;
    }

    @Override
    public void unregisterMove(Move move, int boardSize) {
        int[] counts = colCounts.get(move.player().getPieceType());
        if (counts != null) {
            counts[move.col()]--;
        }
    }
}

