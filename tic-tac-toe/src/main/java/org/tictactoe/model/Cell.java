package org.tictactoe.model;

import org.tictactoe.model.enums.PieceType;

/**
 * Represents a single cell on the board.
 *
 * Interview note: Cell is a simple data holder. It knows its position
 * and what piece (if any) occupies it. Keeping it mutable (piece can be set/cleared)
 * is necessary for undo support.
 */
public class Cell {

    private final int row;
    private final int col;
    private PieceType piece; // null means empty

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
        this.piece = null;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public PieceType getPiece() {
        return piece;
    }

    public void setPiece(PieceType piece) {
        this.piece = piece;
    }

    public boolean isEmpty() {
        return piece == null;
    }

    public void clear() {
        this.piece = null;
    }

    @Override
    public String toString() {
        return piece == null ? " " : piece.symbol();
    }
}

