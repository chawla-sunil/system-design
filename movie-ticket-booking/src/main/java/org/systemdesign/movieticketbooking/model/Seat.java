package org.systemdesign.movieticketbooking.model;

import org.systemdesign.movieticketbooking.model.enums.SeatType;

/**
 * Physical seat in a screen. Immutable — does NOT hold booking status.
 * Booking status is held in ShowSeat (per-show).
 */
public class Seat {
    private final String id;
    private final int row;
    private final int col;
    private final SeatType seatType;

    public Seat(String id, int row, int col, SeatType seatType) {
        this.id = id;
        this.row = row;
        this.col = col;
        this.seatType = seatType;
    }

    public String getId() { return id; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public SeatType getSeatType() { return seatType; }

    @Override
    public String toString() {
        return seatType + "-R" + row + "C" + col;
    }
}

