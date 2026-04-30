package org.systemdesign.movieticketbooking.model;

import org.systemdesign.movieticketbooking.model.enums.SeatStatus;

/**
 * ★ THE most important class for concurrency.
 *
 * Represents a seat's status for a SPECIFIC show.
 * Same physical seat can be BOOKED for the 2pm show but AVAILABLE for the 6pm show.
 *
 * Thread-safety: All mutations happen inside synchronized(show) block in services,
 * so we don't need internal locking here — the Show-level lock provides atomicity
 * for multi-seat operations.
 */
public class ShowSeat {
    private final Seat seat;
    private SeatStatus status;
    private String lockedByUserId; // who temporarily locked this seat

    public ShowSeat(Seat seat) {
        this.seat = seat;
        this.status = SeatStatus.AVAILABLE;
        this.lockedByUserId = null;
    }

    // ---- State Transitions (called within synchronized(show)) ----

    /**
     * Temporarily lock this seat for a user (while they go to payment page).
     */
    public void lock(String userId) {
        this.status = SeatStatus.TEMPORARILY_LOCKED;
        this.lockedByUserId = userId;
    }

    /**
     * Release the temporary lock — seat becomes AVAILABLE again.
     * Only unlocks if still locked by the same user (idempotent safety).
     */
    public void unlock(String userId) {
        if (this.status == SeatStatus.TEMPORARILY_LOCKED &&
                userId.equals(this.lockedByUserId)) {
            this.status = SeatStatus.AVAILABLE;
            this.lockedByUserId = null;
        }
    }

    /**
     * Permanently book this seat.
     */
    public void book() {
        this.status = SeatStatus.BOOKED;
        this.lockedByUserId = null; // no longer "locked" — it's booked
    }

    /**
     * Cancel a booking — seat goes back to AVAILABLE.
     */
    public void cancelBooking() {
        this.status = SeatStatus.AVAILABLE;
        this.lockedByUserId = null;
    }

    // ---- Queries ----

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public boolean isLockedBy(String userId) {
        return status == SeatStatus.TEMPORARILY_LOCKED &&
                userId.equals(this.lockedByUserId);
    }

    public boolean isBooked() {
        return status == SeatStatus.BOOKED;
    }

    public Seat getSeat() { return seat; }
    public SeatStatus getStatus() { return status; }
    public String getLockedByUserId() { return lockedByUserId; }

    @Override
    public String toString() {
        return seat.toString() + "[" + status +
                (lockedByUserId != null ? " by=" + lockedByUserId : "") + "]";
    }
}

