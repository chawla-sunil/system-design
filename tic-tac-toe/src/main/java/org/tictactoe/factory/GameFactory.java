package org.tictactoe.factory;

import org.tictactoe.model.Board;
import org.tictactoe.model.Player;
import org.tictactoe.model.enums.PieceType;
import org.tictactoe.service.GameService;
import org.tictactoe.strategy.*;
import org.tictactoe.validator.MoveValidator;

import java.util.List;

/**
 * Factory that creates a fully wired GameService with sensible defaults.
 *
 * Interview note: Encapsulates the construction complexity. The client
 * doesn't need to know which strategies exist or how to compose them.
 * If we add a new strategy (e.g., CornerWinStrategy), we add it here —
 * zero changes in calling code.
 */
public class GameFactory {

    private GameFactory() {
        // Utility class — no instantiation
    }

    /**
     * Creates a standard 3×3 tic-tac-toe game.
     */
    public static GameService createStandardGame(String player1Name, String player2Name) {
        return createCustomGame(player1Name, player2Name, 3);
    }

    /**
     * Creates a custom N×N tic-tac-toe game.
     */
    public static GameService createCustomGame(String player1Name, String player2Name, int boardSize) {
        Board board = new Board(boardSize);

        Player player1 = new Player(player1Name, PieceType.X);
        Player player2 = new Player(player2Name, PieceType.O);

        List<WinningStrategy> strategies = List.of(
                new RowWinStrategy(),
                new ColumnWinStrategy(),
                new DiagonalWinStrategy(),
                new AntiDiagonalWinStrategy()
        );

        MoveValidator validator = new MoveValidator();

        return new GameService(board, List.of(player1, player2), strategies, validator);
    }
}

