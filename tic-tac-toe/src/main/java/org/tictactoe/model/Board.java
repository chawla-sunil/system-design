package org.tictactoe.model;

import org.tictactoe.model.enums.PieceType;

/**
 * Represents the N×N game board.
 *
 * Interview note: Board is responsible ONLY for holding state (cells)
 * and providing operations to place/clear pieces and display itself.
 * It does NOT contain game logic (win checking, turn management) —
 * that's the job of GameService and WinningStrategy.
 *
 * This separation follows SRP (Single Responsibility Principle).
 */
public class Board {

    private final int size;
    private final Cell[][] cells;

    public Board(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Board size must be at least 1");
        }
        this.size = size;
        this.cells = new Cell[size][size];
        initializeCells();
    }

    private void initializeCells() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                cells[i][j] = new Cell(i, j);
            }
        }
    }

    // ── State queries ────────────────────────────────────────────

    public int getSize() {
        return size;
    }

    public Cell getCell(int row, int col) {
        return cells[row][col];
    }

    public boolean isCellEmpty(int row, int col) {
        return cells[row][col].isEmpty();
    }

    public boolean isWithinBounds(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    // ── State mutations ──────────────────────────────────────────

    public void placePiece(int row, int col, PieceType piece) {
        cells[row][col].setPiece(piece);
    }

    public void clearCell(int row, int col) {
        cells[row][col].clear();
    }

    // ── Display ──────────────────────────────────────────────────

    /**
     * Pretty-prints the board to stdout.
     *
     * Example for 3×3:
     *   0   1   2
     * ┌───┬───┬───┐
     * │ X │ O │   │  0
     * ├───┼───┼───┤
     * │   │ X │   │  1
     * ├───┼───┼───┤
     * │ O │   │ X │  2
     * └───┴───┴───┘
     */
    public void display() {
        // Column headers
        System.out.print("  ");
        for (int col = 0; col < size; col++) {
            System.out.printf("  %d ", col);
        }
        System.out.println();

        // Top border
        System.out.print("  ┌");
        for (int col = 0; col < size; col++) {
            System.out.print("───");
            System.out.print(col < size - 1 ? "┬" : "┐");
        }
        System.out.println();

        for (int row = 0; row < size; row++) {
            // Cell row
            System.out.print("  │");
            for (int col = 0; col < size; col++) {
                System.out.printf(" %s │", cells[row][col]);
            }
            System.out.printf("  %d%n", row);

            // Row separator or bottom border
            if (row < size - 1) {
                System.out.print("  ├");
                for (int col = 0; col < size; col++) {
                    System.out.print("───");
                    System.out.print(col < size - 1 ? "┼" : "┤");
                }
                System.out.println();
            } else {
                System.out.print("  └");
                for (int col = 0; col < size; col++) {
                    System.out.print("───");
                    System.out.print(col < size - 1 ? "┴" : "┘");
                }
                System.out.println();
            }
        }
    }
}

