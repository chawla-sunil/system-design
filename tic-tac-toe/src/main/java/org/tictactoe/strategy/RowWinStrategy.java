package org.tictactoe.strategy;

import org.tictactoe.model.Move;
import org.tictactoe.model.enums.PieceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Checks if a player has filled an entire row.
 *
 * Interview note: Uses a Map of (PieceType → int[]) to track how many
 * pieces each player has placed in each row. When a count reaches boardSize,
 * that player has won via a complete row.
 *
 * Time complexity: O(1) per move.
 * Space complexity: O(N × numPlayers).
 */
public class RowWinStrategy implements WinningStrategy {

    // pieceType → rowCounts[row]
    private final Map<PieceType, int[]> rowCounts = new HashMap<>();

    @Override
    public boolean registerMove(Move move, int boardSize) {
        int[] counts = rowCounts.computeIfAbsent(
                move.player().getPieceType(), k -> new int[boardSize]);
        counts[move.row()]++;
        return counts[move.row()] == boardSize;
    }

    @Override
    public void unregisterMove(Move move, int boardSize) {
        int[] counts = rowCounts.get(move.player().getPieceType());
        if (counts != null) {
            counts[move.row()]--;
        }
    }
}

