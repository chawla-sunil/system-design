package org.systemdesign.chess.service;

import org.systemdesign.chess.domain.ChessPieceType;
import org.systemdesign.chess.domain.Color;
import org.systemdesign.chess.domain.Position;
import org.systemdesign.chess.domain.game.Board;
import org.systemdesign.chess.domain.game.GameStatus;
import org.systemdesign.chess.domain.game.Move;
import org.systemdesign.chess.domain.game.MoveType;
import org.systemdesign.chess.domain.game.Player;
import org.systemdesign.chess.domain.piece.King;
import org.systemdesign.chess.domain.piece.Piece;
import org.systemdesign.chess.domain.piece.Pawn;
import org.systemdesign.chess.domain.piece.Rook;
import org.systemdesign.chess.factory.PieceFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Main orchestrator for a chess game.
 *
 * Responsibilities:
 * 1. Manage game state (board, players, current turn)
 * 2. Validate and execute moves
 * 3. Detect game-ending conditions (checkmate, stalemate, resignation)
 * 4. Track move history (for undo, replay)
 * 5. Expose game flow API (makeMove, resign, etc.)
 *
 * Design Pattern: Facade - provides a clean interface to complex subsystems
 */
public class Game {
    private final Board board;
    private final Player whitePlayer;
    private final Player blackPlayer;
    private Player currentPlayer;
    private GameStatus gameStatus;
    private final Stack<Move> moveHistory;
    private int fiftyMoveCounter; // For draw by fifty-move rule
    private Position lastEnPassantTarget; // For en passant capture

    public Game(Player whitePlayer, Player blackPlayer) {
        this.board = new Board();
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.currentPlayer = whitePlayer; // White moves first
        this.gameStatus = GameStatus.ACTIVE;
        this.moveHistory = new Stack<>();
        this.fiftyMoveCounter = 0;

        initializeBoard();
    }

    /**
     * Initialize the standard starting position.
     */
    private void initializeBoard() {
        PieceFactory factory = new PieceFactory();
        factory.initializeStandardPosition(board);
    }

    /**
     * Returns the board (read-only access recommended).
     */
    public Board getBoard() {
        return board;
    }

    public Player getWhitePlayer() {
        return whitePlayer;
    }

    public Player getBlackPlayer() {
        return blackPlayer;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public GameStatus getGameStatus() {
        return gameStatus;
    }

    public List<Move> getMoveHistory() {
        return new ArrayList<>(moveHistory);
    }

    /**
     * Main method to execute a move.
     *
     * Algorithm:
     * 1. Validate move source and target are different
     * 2. Validate piece exists and belongs to current player
     * 3. Validate move is in piece's valid moves
     * 4. Validate move doesn't leave king in check
     * 5. Apply move to board
     * 6. Update game state (castling rights, en passant, etc.)
     * 7. Check for game-ending conditions
     * 8. Switch turns
     *
     * @param from source position
     * @param to target position
     * @return true if move was successful, false otherwise
     */
    public boolean makeMove(Position from, Position to) {
        // Basic validation
        if (from.equals(to)) {
            System.out.println("Invalid move: from and to are the same");
            return false;
        }

        if (gameStatus != GameStatus.ACTIVE) {
            System.out.println("Game is not active");
            return false;
        }

        // Get the piece
        Piece piece = board.getPieceAt(from);
        if (piece == null) {
            System.out.println("No piece at source position");
            return false;
        }

        // Validate piece belongs to current player
        if (piece.getColor() != currentPlayer.getColor()) {
            System.out.println("Piece does not belong to current player");
            return false;
        }

        // Validate move is in piece's valid moves
        Set<Position> validMoves = piece.getValidMoves(board);
        if (!validMoves.contains(to)) {
            System.out.println("Move is not in piece's valid moves");
            return false;
        }

        // Validate move is legal (doesn't leave king in check)
        if (!GameRules.isMoveLegal(from, to, board, currentPlayer.getColor())) {
            System.out.println("Move leaves king in check");
            return false;
        }

        // Handle special moves
        Piece capturedPiece = board.getPieceAt(to);
        MoveType moveType = determineMoveType(from, to, piece, capturedPiece);

        // Execute the move
        executeMove(piece, from, to, capturedPiece, moveType);

        // Create and store move
        Move move = new Move.Builder(from, to, piece)
                .capturedPiece(capturedPiece)
                .moveType(moveType)
                .build();
        moveHistory.push(move);

        // Update game state
        updateFiftyMoveCounter(piece, capturedPiece);
        updateCastlingRights(piece);

        // Switch players
        currentPlayer = currentPlayer == whitePlayer ? blackPlayer : whitePlayer;

        // Check for game-ending conditions
        checkAndUpdateGameStatus();

        return true;
    }

    /**
     * Executes the move on the board, handling special move types.
     */
    private void executeMove(Piece piece, Position from, Position to,
                            Piece capturedPiece, MoveType moveType) {
        switch (moveType) {
            case CASTLING:
                executeCastling(piece, from, to);
                break;

            case EN_PASSANT:
                board.removePiece(to); // Remove captured pawn
                board.placePiece(piece, to);
                break;

            case PROMOTION:
                // For now, promote to queen (can be extended)
                board.placePiece(piece, to);
                // TODO: Implement promotion logic properly
                break;

            case NORMAL:
            default:
                if (capturedPiece != null) {
                    board.removePiece(to);
                }
                board.placePiece(piece, to);
                break;
        }

        // Update moved flags for pawns and rooks
        if (piece instanceof Pawn) {
            ((Pawn) piece).setHasMoved(true);
        } else if (piece instanceof Rook) {
            ((Rook) piece).setHasMoved(true);
        } else if (piece instanceof King) {
            ((King) piece).setHasMoved(true);
        }
    }

    /**
     * Executes a castling move.
     */
    private void executeCastling(Piece king, Position kingFrom, Position kingTo) {
        int kingRow = kingFrom.getRow();
        int rookCol = kingTo.getCol() > kingFrom.getCol() ? 7 : 0; // 7 for kingside, 0 for queenside
        Position rookFrom = new Position(kingRow, rookCol);
        Position rookTo = new Position(kingRow, kingTo.getCol() > kingFrom.getCol() ? 5 : 3);

        // Move king
        board.placePiece(king, kingTo);
        ((King) king).setHasMoved(true);

        // Move rook
        Piece rook = board.getPieceAt(rookFrom);
        if (rook instanceof Rook) {
            board.placePiece(rook, rookTo);
            ((Rook) rook).setHasMoved(true);
        }
    }

    /**
     * Determines the type of move.
     */
    private MoveType determineMoveType(Position from, Position to, Piece piece, Piece capturedPiece) {
        if (piece.getType() == ChessPieceType.KING && Math.abs(to.getCol() - from.getCol()) == 2) {
            return MoveType.CASTLING;
        }

        if (piece.getType() == ChessPieceType.PAWN) {
            // Check for promotion
            if ((piece.getColor() == Color.WHITE && to.getRow() == 0) ||
                (piece.getColor() == Color.BLACK && to.getRow() == 7)) {
                return MoveType.PROMOTION;
            }

            // Check for en passant
            if (capturedPiece == null && from.getCol() != to.getCol()) {
                return MoveType.EN_PASSANT;
            }
        }

        return MoveType.NORMAL;
    }

    /**
     * Updates the fifty-move counter (for draw by fifty-move rule).
     */
    private void updateFiftyMoveCounter(Piece piece, Piece capturedPiece) {
        if (piece.getType() == ChessPieceType.PAWN || capturedPiece != null) {
            fiftyMoveCounter = 0;
        } else {
            fiftyMoveCounter++;
        }
    }

    /**
     * Updates castling rights after a move.
     */
    private void updateCastlingRights(Piece piece) {
        if (piece.getType() == ChessPieceType.KING) {
            board.getCastlingRights(piece.getColor()).disableBothSides();
        } else if (piece.getType() == ChessPieceType.ROOK) {
            Board.CastlingRights rights = board.getCastlingRights(piece.getColor());
            if (piece.getPosition().getCol() == 0) {
                rights.disableQueenside();
            } else if (piece.getPosition().getCol() == 7) {
                rights.disableKingside();
            }
        }
    }

    /**
     * Checks for game-ending conditions and updates game status.
     */
    private void checkAndUpdateGameStatus() {
        Color nextPlayerColor = currentPlayer.getColor();

        if (GameRules.isCheckmate(nextPlayerColor, board)) {
            gameStatus = GameStatus.CHECKMATE;
            System.out.println(nextPlayerColor + " is in checkmate. " +
                             (nextPlayerColor == Color.WHITE ? blackPlayer.getName() : whitePlayer.getName()) +
                             " wins!");
        } else if (GameRules.isStalemate(nextPlayerColor, board)) {
            gameStatus = GameStatus.STALEMATE;
            System.out.println("Stalemate! Game is a draw.");
        } else if (fiftyMoveCounter >= 50) {
            gameStatus = GameStatus.DRAW_BY_50_MOVE_RULE;
            System.out.println("Draw by fifty-move rule.");
        }
    }

    /**
     * Returns all legal moves for a piece at the given position.
     */
    public Set<Position> getValidMoves(Position position) {
        Piece piece = board.getPieceAt(position);
        if (piece == null || piece.getColor() != currentPlayer.getColor()) {
            return new HashSet<>();
        }

        Set<Position> validMoves = piece.getValidMoves(board);
        // Filter out moves that leave king in check
        validMoves.removeIf(to -> !GameRules.isMoveLegal(position, to, board, piece.getColor()));

        return validMoves;
    }

    /**
     * Checks if the current player is in check.
     */
    public boolean isCurrentPlayerInCheck() {
        return GameRules.isInCheck(currentPlayer.getColor(), board);
    }

    /**
     * Current player resigns, opponent wins.
     */
    public void resign() {
        gameStatus = GameStatus.RESIGNED;
        Player winner = currentPlayer == whitePlayer ? blackPlayer : whitePlayer;
        System.out.println(currentPlayer.getName() + " resigned. " + winner.getName() + " wins!");
    }

    /**
     * Undo the last move.
     */
    public void undo() {
        if (moveHistory.isEmpty()) {
            System.out.println("No moves to undo");
            return;
        }

        // TODO: Implement undo logic
        // This would require either:
        // 1. Storing board snapshots with each move
        // 2. Replaying game from start without this move
        // 3. Storing piece positions before each move

        System.out.println("Undo not yet implemented");
    }

    /**
     * Resets the game to initial state.
     */
    public void reset() {
        board.initializeStandardPosition();
        currentPlayer = whitePlayer;
        gameStatus = GameStatus.ACTIVE;
        moveHistory.clear();
        fiftyMoveCounter = 0;
        lastEnPassantTarget = null;
        initializeBoard();
    }

    @Override
    public String toString() {
        return "Game{" +
                "status=" + gameStatus +
                ", currentPlayer=" + currentPlayer.getColor() +
                ", moveCount=" + moveHistory.size() +
                '}';
    }
}
