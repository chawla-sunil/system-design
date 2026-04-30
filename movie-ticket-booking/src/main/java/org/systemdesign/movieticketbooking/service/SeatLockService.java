package org.systemdesign.movieticketbooking.service;

import org.systemdesign.movieticketbooking.exception.SeatNotAvailableException;
import org.systemdesign.movieticketbooking.exception.SeatTemporarilyLockedException;
import org.systemdesign.movieticketbooking.model.Show;
import org.systemdesign.movieticketbooking.model.ShowSeat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ★★★ THE CORE CONCURRENCY SERVICE ★★★
 *
 * Handles temporary seat locking mechanism:
 * 1. User selects seats → seats are TEMPORARILY_LOCKED for N minutes
 * 2. If user pays within N minutes → seats are BOOKED (handled by BookingService)
 * 3. If user doesn't pay → ScheduledExecutorService expires the lock → seats become AVAILABLE
 *
 * Thread-safety: synchronized on the Show object to ensure atomicity of multi-seat operations.
 *
 * WHY Show-level lock instead of per-seat lock?
 * - A user selects MULTIPLE seats at once
 * - We need ALL-or-NOTHING atomicity (either all seats are locked or none)
 * - Per-seat locks would risk deadlocks when two users want overlapping seats
 * - Show-level lock is simple, correct, and performant (different shows don't block each other)
 */
public class SeatLockService {

    private static final int DEFAULT_LOCK_DURATION_MINUTES = 5;

    // Daemon thread pool to expire locks — won't prevent JVM shutdown
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "seat-lock-expiry");
                t.setDaemon(true);
                return t;
            });

    private final ShowService showService;

    public SeatLockService(ShowService showService) {
        this.showService = showService;
    }

    /**
     * Temporarily lock seats for a user.
     *
     * Atomicity: ALL seats must be available, or NONE are locked.
     * This prevents the scenario where User A gets seats [1,2] locked
     * but seat [3] fails — now User A has a partial lock.
     *
     * @return list of locked ShowSeats
     * @throws SeatNotAvailableException if any seat is already BOOKED
     * @throws SeatTemporarilyLockedException if any seat is locked by another user
     */
    public List<ShowSeat> lockSeats(String showId, List<String> seatIds, String userId) {
        return lockSeats(showId, seatIds, userId, DEFAULT_LOCK_DURATION_MINUTES);
    }

    public List<ShowSeat> lockSeats(String showId, List<String> seatIds, String userId,
                                    int lockDurationMinutes) {
        Show show = showService.getShow(showId);
        if (show == null) {
            throw new IllegalArgumentException("Show not found: " + showId);
        }

        List<ShowSeat> seatsToLock = new ArrayList<>();

        // ★ Synchronize on the Show object — atomic check-and-lock for ALL seats
        synchronized (show) {
            // Phase 1: VALIDATE — check ALL seats are available
            for (String seatId : seatIds) {
                ShowSeat showSeat = show.getShowSeat(seatId);
                if (showSeat == null) {
                    throw new IllegalArgumentException("Seat not found: " + seatId);
                }

                if (showSeat.isBooked()) {
                    throw new SeatNotAvailableException(
                            "Seat " + showSeat.getSeat() + " is already booked for this show");
                }

                if (showSeat.isLockedBy(userId)) {
                    // Idempotent — already locked by the same user, skip
                    seatsToLock.add(showSeat);
                    continue;
                }

                if (!showSeat.isAvailable()) {
                    throw new SeatTemporarilyLockedException(
                            "Seat " + showSeat.getSeat() + " is temporarily held by another user. " +
                                    "Please try again in a few minutes.");
                }

                seatsToLock.add(showSeat);
            }

            // Phase 2: LOCK — all seats passed validation, lock them all
            for (ShowSeat showSeat : seatsToLock) {
                if (!showSeat.isLockedBy(userId)) { // don't re-lock if already locked by same user
                    showSeat.lock(userId);
                }
            }
        }

        // Phase 3: SCHEDULE EXPIRY — outside the synchronized block (non-blocking)
        scheduler.schedule(
                () -> unlockExpiredSeats(show, seatIds, userId),
                lockDurationMinutes,
                TimeUnit.MINUTES
        );

        System.out.println("  🔒 Seats LOCKED for user " + userId + " → " + seatsToLock +
                " (expires in " + lockDurationMinutes + " min)");

        return seatsToLock;
    }

    /**
     * Called by the scheduler when the lock expires.
     * Only unlocks if the seat is still locked by the SAME user
     * (the user might have already paid → seat is now BOOKED → don't touch it!).
     */
    private void unlockExpiredSeats(Show show, List<String> seatIds, String userId) {
        synchronized (show) {
            boolean anyUnlocked = false;
            for (String seatId : seatIds) {
                ShowSeat showSeat = show.getShowSeat(seatId);
                if (showSeat != null && showSeat.isLockedBy(userId)) {
                    showSeat.unlock(userId);
                    anyUnlocked = true;
                }
            }
            if (anyUnlocked) {
                System.out.println("  ⏰ Lock EXPIRED for user " + userId +
                        " → seats released back to AVAILABLE");
            }
        }
    }

    /**
     * Explicitly unlock seats (e.g., user cancels seat selection before paying).
     */
    public void unlockSeats(String showId, List<String> seatIds, String userId) {
        Show show = showService.getShow(showId);
        if (show == null) return;

        synchronized (show) {
            for (String seatId : seatIds) {
                ShowSeat showSeat = show.getShowSeat(seatId);
                if (showSeat != null) {
                    showSeat.unlock(userId);
                }
            }
        }
        System.out.println("  🔓 Seats UNLOCKED by user " + userId);
    }

    /**
     * Shutdown the scheduler gracefully.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}

