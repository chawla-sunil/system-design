package org.tictactoe;

import org.tictactoe.exception.GameOverException;
import org.tictactoe.exception.InvalidMoveException;
import org.tictactoe.factory.GameFactory;
import org.tictactoe.service.GameService;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  TIC-TAC-TOE — LLD DEMO
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Scenarios demonstrated:
 *    1. A complete game where X wins (diagonal)
 *    2. Invalid move handling (out of bounds, occupied cell)
 *    3. Undo last move
 *    4. A game that ends in a DRAW
 *    5. Attempting to move after game is over
 *    6. Custom 4×4 board game
 */
public class Main {

    public static void main(String[] args) {

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      TIC-TAC-TOE LLD DEMO — START       ║");
        System.out.println("╚══════════════════════════════════════════╝");

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        //  SCENARIO 1: X wins via main diagonal
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        System.out.println("\n\n▶ SCENARIO 1: X wins via diagonal");
        System.out.println("─".repeat(42));

        GameService game1 = GameFactory.createStandardGame("Alice", "Bob");
        game1.displayBoard();

        /*
         * Game flow:
         *   Alice(X): (0,0)  →  X _ _    Bob(O): (0,1)  →  X O _
         *                       _ _ _                       _ _ _
         *                       _ _ _                       _ _ _
         *
         *   Alice(X): (1,1)  →  X O _    Bob(O): (0,2)  →  X O O
         *                       _ X _                       _ X _
         *                       _ _ _                       _ _ _
         *
         *   Alice(X): (2,2)  →  X O O    ← X wins! (main diagonal)
         *                       _ X _
         *                       _ _ X
         */
        System.out.println("\n[MOVE] " + game1.getCurrentPlayer() + " plays (0,0)");
        game1.makeMove(0, 0);
        game1.displayBoard();

        System.out.println("\n[MOVE] " + game1.getCurrentPlayer() + " plays (0,1)");
        game1.makeMove(0, 1);
        game1.displayBoard();

        System.out.println("\n[MOVE] " + game1.getCurrentPlayer() + " plays (1,1)");
        game1.makeMove(1, 1);
        game1.displayBoard();

        System.out.println("\n[MOVE] " + game1.getCurrentPlayer() + " plays (0,2)");
        game1.makeMove(0, 2);
        game1.displayBoard();

        System.out.println("\n[MOVE] " + game1.getCurrentPlayer() + " plays (2,2) — winning move!");
        game1.makeMove(2, 2);
        game1.displayBoard();

        System.out.println("\n✅ Game 1 result: " + game1.getGameStatus());
        System.out.println("   Total moves: " + game1.getMoveCount());
        System.out.println("   Move history: " + game1.getMoveHistory());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        //  SCENARIO 2: Invalid move handling
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        System.out.println("\n\n▶ SCENARIO 2: Invalid move handling");
        System.out.println("─".repeat(42));

        GameService game2 = GameFactory.createStandardGame("Charlie", "Diana");
        game2.makeMove(1, 1); // Charlie plays center

        // Try out-of-bounds move
        try {
            System.out.println("[MOVE] Diana tries (5,5) — out of bounds...");
            game2.makeMove(5, 5);
        } catch (InvalidMoveException e) {
            System.out.println("❌ Caught: " + e.getMessage());
        }

        // Try occupied cell
        try {
            System.out.println("[MOVE] Diana tries (1,1) — already occupied...");
            game2.makeMove(1, 1);
        } catch (InvalidMoveException e) {
            System.out.println("❌ Caught: " + e.getMessage());
        }

        // Try playing after game over (reuse game1)
        try {
            System.out.println("[MOVE] Trying to play on finished game1...");
            game1.makeMove(2, 0);
        } catch (GameOverException e) {
            System.out.println("❌ Caught: " + e.getMessage());
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        //  SCENARIO 3: Undo last move
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        System.out.println("\n\n▶ SCENARIO 3: Undo last move");
        System.out.println("─".repeat(42));

        GameService game3 = GameFactory.createStandardGame("Eve", "Frank");

        game3.makeMove(0, 0); // Eve: X at (0,0)
        game3.makeMove(1, 1); // Frank: O at (1,1)
        game3.makeMove(0, 1); // Eve: X at (0,1)

        System.out.println("Before undo:");
        game3.displayBoard();

        game3.undoLastMove(); // Undo Eve's last move at (0,1)

        System.out.println("\nAfter undo:");
        game3.displayBoard();

        // Eve can now play a different move
        System.out.println("\n[MOVE] Eve plays (2,2) instead");
        game3.makeMove(2, 2);
        game3.displayBoard();

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        //  SCENARIO 4: Game ends in a DRAW
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        System.out.println("\n\n▶ SCENARIO 4: Game ends in a DRAW");
        System.out.println("─".repeat(42));

        GameService game4 = GameFactory.createStandardGame("Grace", "Hank");

        /*
         *   X O X
         *   X X O
         *   O X O
         *
         *   All 9 cells filled, no winner → DRAW
         */
        game4.makeMove(0, 0); // Grace X
        game4.makeMove(0, 1); // Hank  O
        game4.makeMove(0, 2); // Grace X
        game4.makeMove(1, 2); // Hank  O
        game4.makeMove(1, 0); // Grace X
        game4.makeMove(2, 0); // Hank  O
        game4.makeMove(1, 1); // Grace X
        game4.makeMove(2, 2); // Hank  O
        game4.makeMove(2, 1); // Grace X

        game4.displayBoard();
        System.out.println("\n✅ Game 4 result: " + game4.getGameStatus());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        //  SCENARIO 5: Custom 4×4 board
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        System.out.println("\n\n▶ SCENARIO 5: Custom 4×4 board — X wins via row");
        System.out.println("─".repeat(42));

        GameService game5 = GameFactory.createCustomGame("Ivan", "Judy", 4);

        // X fills row 0: (0,0), (0,1), (0,2), (0,3)
        // O fills row 1: (1,0), (1,1), (1,2)
        game5.makeMove(0, 0); // Ivan X
        game5.makeMove(1, 0); // Judy O
        game5.makeMove(0, 1); // Ivan X
        game5.makeMove(1, 1); // Judy O
        game5.makeMove(0, 2); // Ivan X
        game5.makeMove(1, 2); // Judy O
        game5.makeMove(0, 3); // Ivan X — wins!

        game5.displayBoard();
        System.out.println("\n✅ Game 5 result: " + game5.getGameStatus());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║       TIC-TAC-TOE LLD DEMO — END        ║");
        System.out.println("╚══════════════════════════════════════════╝");
    }
}