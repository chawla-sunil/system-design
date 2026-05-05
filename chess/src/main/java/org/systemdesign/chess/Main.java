package org.systemdesign.chess;

import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.service.Game;
import org.systemdesign.chess.domain.game.Player;
import org.systemdesign.chess.domain.Position;

/**
 * Main entry point for the Chess Game LLD implementation.
 *
 * This demonstrates the complete chess system with various scenarios:
 * 1. Initialize a game
 * 2. Make moves
 * 3. Display board state
 * 4. Handle game-ending conditions
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("CHESS GAME - LOW-LEVEL DESIGN (LLD)");
        System.out.println("=".repeat(60));
        System.out.println();

        // Initialize players
        Player whitePlayer = new Player(Color.WHITE, true, "Alice (White)");
        Player blackPlayer = new Player(Color.BLACK, true, "Bob (Black)");

        // Create game
        Game game = new Game(whitePlayer, blackPlayer);

        System.out.println("Game initialized: " + whitePlayer.getName() + " vs " + blackPlayer.getName());
        System.out.println("Current player: " + game.getCurrentPlayer().getName());
        System.out.println();

        // Display initial board state
        System.out.println("Initial Board State:");
        displayBoard(game);
        System.out.println();

        // ==================== SCENARIO 1: Basic Opening Moves ====================
        System.out.println("=".repeat(60));
        System.out.println("SCENARIO 1: Italian Game Opening");
        System.out.println("=".repeat(60));
        System.out.println();

        // Move 1: White e4 (e2 to e4)
        System.out.println("Move 1: White pawn e2 to e4");
        boolean move1 = game.makeMove(new Position(6, 4), new Position(4, 4));
        System.out.println("Move successful: " + move1);
        System.out.println("Current player: " + game.getCurrentPlayer().getName());
        System.out.println();

        // Move 2: Black c5 (c7 to c5)
        System.out.println("Move 2: Black pawn c7 to c5");
        boolean move2 = game.makeMove(new Position(1, 2), new Position(3, 2));
        System.out.println("Move successful: " + move2);
        System.out.println("Current player: " + game.getCurrentPlayer().getName());
        System.out.println();

        // Move 3: White Nf3 (g1 to f3)
        System.out.println("Move 3: White knight g1 to f3");
        boolean move3 = game.makeMove(new Position(7, 6), new Position(5, 5));
        System.out.println("Move successful: " + move3);
        System.out.println();

        // Display board after opening moves
        System.out.println("Board after opening moves:");
        displayBoard(game);
        System.out.println();

        // ==================== SCENARIO 2: Move Validation ====================
        System.out.println("=".repeat(60));
        System.out.println("SCENARIO 2: Move Validation");
        System.out.println("=".repeat(60));
        System.out.println();

        // Try invalid move (same source and target)
        System.out.println("Attempting invalid move: pawn c5 to c5 (same position)");
        boolean invalidMove = game.makeMove(new Position(3, 2), new Position(3, 2));
        System.out.println("Move successful: " + invalidMove);
        System.out.println();

        // Get valid moves for current piece
        System.out.println("Valid moves for black pawn at c5:");
        var validMoves = game.getValidMoves(new Position(3, 2));
        validMoves.forEach(pos -> System.out.println("  - " + pos));
        System.out.println();

        // ==================== SCENARIO 3: Check Detection ====================
        System.out.println("=".repeat(60));
        System.out.println("SCENARIO 3: Simple Check Detection & King Safety");
        System.out.println("=".repeat(60));
        System.out.println();

        // Reset for controlled scenario
        game.reset();
        System.out.println("Game reset to initial position");
        System.out.println();

        // Execute Scholar's Mate trap setup
        executeScholarsMateTrap(game);
        System.out.println();

        // ==================== GAME INFORMATION ====================
        System.out.println("=".repeat(60));
        System.out.println("GAME INFORMATION");
        System.out.println("=".repeat(60));
        System.out.println("Game Status: " + game.getGameStatus());
        System.out.println("Move History Size: " + game.getMoveHistory().size());
        System.out.println("Current Player: " + game.getCurrentPlayer().getName());
        System.out.println("Is Current Player in Check?: " + game.isCurrentPlayerInCheck());
        System.out.println();

        // ==================== DESIGN PATTERNS USED ====================
        printDesignPatternsInfo();
    }

    /**
     * Displays the board in a readable format.
     */
    private static void displayBoard(Game game) {
        System.out.println(game.getBoard().toString());
    }

    /**
     * Executes Scholar's Mate scenario to demonstrate check detection.
     */
    private static void executeScholarsMateTrap(Game game) {
        // 1. e4
        game.makeMove(new Position(6, 4), new Position(4, 4));
        System.out.println("1. e4");

        // 1...e5
        game.makeMove(new Position(1, 4), new Position(3, 4));
        System.out.println("1...e5");

        // 2. Bc4
        game.makeMove(new Position(7, 5), new Position(5, 2));
        System.out.println("2. Bc4");

        // 2...Nc6
        game.makeMove(new Position(0, 1), new Position(2, 2));
        System.out.println("2...Nc6");

        // 3. Qh5
        game.makeMove(new Position(7, 3), new Position(5, 7));
        System.out.println("3. Qh5");

        // 3...Nf6?
        game.makeMove(new Position(0, 6), new Position(2, 5));
        System.out.println("3...Nf6?");

        System.out.println();
        System.out.println("Board state after Scholar's Mate setup:");
        displayBoard(game);
        System.out.println();

        System.out.println("Is Black in Check?: " +
                          (game.getCurrentPlayer().getColor() == Color.WHITE ?
                           game.isCurrentPlayerInCheck() : false));
    }

    /**
     * Prints information about design patterns used in this implementation.
     */
    private static void printDesignPatternsInfo() {
        System.out.println("=".repeat(60));
        System.out.println("DESIGN PATTERNS USED");
        System.out.println("=".repeat(60));
        System.out.println();

        System.out.println("1. STRATEGY PATTERN: Piece Movement");
        System.out.println("   - Each piece type (Pawn, Knight, Bishop, etc.) implements");
        System.out.println("     its own movement logic via getValidMoves()");
        System.out.println();

        System.out.println("2. FACTORY PATTERN: PieceFactory");
        System.out.println("   - Centralized creation of pieces");
        System.out.println("   - Handles standard board initialization");
        System.out.println();

        System.out.println("3. BUILDER PATTERN: Move Creation");
        System.out.println("   - Flexible move construction with optional fields");
        System.out.println("   - e.g., Move.Builder(from, to, piece)");
        System.out.println("         .capturedPiece(opponent)");
        System.out.println("         .moveType(PROMOTION)");
        System.out.println("         .build()");
        System.out.println();

        System.out.println("4. IMMUTABLE PATTERN: Position & Move");
        System.out.println("   - Position is immutable value object");
        System.out.println("   - Move is immutable for reliable history");
        System.out.println();

        System.out.println("5. FACADE PATTERN: Game Class");
        System.out.println("   - Provides clean interface to complex subsystems");
        System.out.println("   - Hides board, players, rules complexity");
        System.out.println();

        System.out.println("6. INHERITANCE & POLYMORPHISM: Piece Hierarchy");
        System.out.println("   - Piece abstract base class");
        System.out.println("   - 6 concrete piece types with different behaviors");
        System.out.println();

        System.out.println("7. SINGLE RESPONSIBILITY: GameRules Class");
        System.out.println("   - Separates rule logic from game orchestration");
        System.out.println("   - Handles check, checkmate, stalemate, castling");
        System.out.println();
    }
}