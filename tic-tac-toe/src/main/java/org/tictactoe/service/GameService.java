package org.tictactoe.service;

import org.tictactoe.exception.GameOverException;
import org.tictactoe.model.Board;
import org.tictactoe.model.Move;
import org.tictactoe.model.Player;
import org.tictactoe.model.enums.GameStatus;
import org.tictactoe.model.enums.PieceType;
import org.tictactoe.strategy.WinningStrategy;
import org.tictactoe.validator.MoveValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Main game orchestrator — coordinates moves, validation, win detection, and turn management.
 *
 * Interview note: GameService is the "facade" for the entire game. It uses:
 *   - Board              → game state
 *   - MoveValidator      → legality checks
 *   - WinningStrategy[]  → O(1) win detection (pluggable)
 *   - Player[]           → turn rotation
 *
 * Each collaborator has one reason to change (SRP). GameService itself only
 * manages the game flow — not the rules, not the rendering, not the creation.
 */
public class GameService {

    private final Board board;
    private final List<Player> players;
    private final List<WinningStrategy> winningStrategies;
    private final MoveValidator moveValidator;
    private final List<Move> moveHistory;

    private int currentPlayerIndex;
    private GameStatus gameStatus;
    private int moveCount;

    public GameService(Board board,
                       List<Player> players,
                       List<WinningStrategy> winningStrategies,
                       MoveValidator moveValidator) {
        this.board = board;
        this.players = players;
        this.winningStrategies = winningStrategies;
        this.moveValidator = moveValidator;
        this.moveHistory = new ArrayList<>();
        this.currentPlayerIndex = 0;
        this.gameStatus = GameStatus.IN_PROGRESS;
        this.moveCount = 0;
    }

    // ──────────────────────────────────────────────
    //  MAKE MOVE
    // ──────────────────────────────────────────────

    /**
     * Makes a move for the current player at (row, col).
     *
     * Flow:
     * 1. Check game isn't over
     * 2. Validate the move (bounds + empty cell)
     * 3. Place the piece on the board
     * 4. Create a Move record and add to history
     * 5. Register the move with all winning strategies
     * 6. If any strategy reports a win → update game status
     * 7. If no win and board full → DRAW
     * 8. Switch to next player
     *
     * @return the Move that was made
     */
    public Move makeMove(int row, int col) {
        // Step 1: Game over check
        if (gameStatus.isOver()) {
            throw new GameOverException(
                    "Game is already over. Status: " + gameStatus);
        }

        // Step 2: Validate
        moveValidator.validate(board, row, col);

        // Step 3: Place piece
        Player currentPlayer = players.get(currentPlayerIndex);
        board.placePiece(row, col, currentPlayer.getPieceType());

        // Step 4: Record move
        Move move = new Move(row, col, currentPlayer);
        moveHistory.add(move);
        moveCount++;

        // Step 5 & 6: Check win via all strategies
        boolean won = false;
        for (WinningStrategy strategy : winningStrategies) {
            if (strategy.registerMove(move, board.getSize())) {
                won = true;
                // Don't break — all strategies need to register the move
                // for correct counter state (important for undo)
            }
        }

        if (won) {
            gameStatus = (currentPlayer.getPieceType() == PieceType.X)
                    ? GameStatus.X_WON
                    : GameStatus.O_WON;
            return move;
        }

        // Step 7: Check draw
        if (moveCount == board.getSize() * board.getSize()) {
            gameStatus = GameStatus.DRAW;
            return move;
        }

        // Step 8: Next player's turn
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        return move;
    }

    // ──────────────────────────────────────────────
    //  UNDO LAST MOVE
    // ──────────────────────────────────────────────

    /**
     * Undoes the last move. Returns true if undo was successful.
     *
     * Interview note: Because winning strategies use additive counters,
     * undo is just the reverse operation — decrement instead of increment.
     * This is essentially Event Sourcing in miniature.
     */
    public boolean undoLastMove() {
        if (moveHistory.isEmpty()) {
            System.out.println("[UNDO] No moves to undo.");
            return false;
        }

        // Pop the last move
        Move lastMove = moveHistory.remove(moveHistory.size() - 1);

        // Clear the cell on the board
        board.clearCell(lastMove.row(), lastMove.col());

        // Unregister from all winning strategies
        for (WinningStrategy strategy : winningStrategies) {
            strategy.unregisterMove(lastMove, board.getSize());
        }

        moveCount--;
        gameStatus = GameStatus.IN_PROGRESS; // revert any game-over status

        // Switch back to the player who made the undone move
        // (since it's now their turn again)
        currentPlayerIndex = players.indexOf(lastMove.player());

        System.out.println("[UNDO] Undid move: " + lastMove);
        return true;
    }

    // ──────────────────────────────────────────────
    //  GETTERS
    // ──────────────────────────────────────────────

    public Board getBoard() {
        return board;
    }

    public GameStatus getGameStatus() {
        return gameStatus;
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public List<Move> getMoveHistory() {
        return List.copyOf(moveHistory); // defensive copy
    }

    public int getMoveCount() {
        return moveCount;
    }

    /**
     * Displays the board along with current game info.
     */
    public void displayBoard() {
        board.display();
        System.out.println("  Status: " + gameStatus
                + (gameStatus == GameStatus.IN_PROGRESS
                ? " | Turn: " + getCurrentPlayer()
                : ""));
    }
}

