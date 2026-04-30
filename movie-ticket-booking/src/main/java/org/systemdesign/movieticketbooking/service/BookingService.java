package org.systemdesign.movieticketbooking.service;

import org.systemdesign.movieticketbooking.exception.BookingNotFoundException;
import org.systemdesign.movieticketbooking.exception.PaymentFailedException;
import org.systemdesign.movieticketbooking.exception.SeatNotAvailableException;
import org.systemdesign.movieticketbooking.model.*;
import org.systemdesign.movieticketbooking.model.enums.PaymentStatus;
import org.systemdesign.movieticketbooking.observer.BookingObserver;
import org.systemdesign.movieticketbooking.strategy.PricingStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ★ THE CORE BOOKING SERVICE ★
 *
 * Orchestrates the full booking flow:
 * 1. lockSeats()       — User selects seats → temporarily locked (via SeatLockService)
 * 2. confirmBooking()  — User pays → seats become BOOKED, Booking created
 * 3. cancelBooking()   — User cancels → seats freed, payment refunded
 *
 * Two-phase approach separates seat selection from payment:
 * - Phase 1 (lock): fast, holds seats for the user
 * - Phase 2 (confirm): processes payment, finalizes booking
 *
 * This ensures we DON'T hold the synchronized(show) lock during payment processing
 * (which could take seconds with an external payment gateway).
 */
public class BookingService {

    private final ShowService showService;
    private final SeatLockService seatLockService;
    private final PaymentService paymentService;
    private final PricingStrategy pricingStrategy;

    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();
    private final List<BookingObserver> observers = new ArrayList<>();

    public BookingService(ShowService showService,
                          SeatLockService seatLockService,
                          PaymentService paymentService,
                          PricingStrategy pricingStrategy) {
        this.showService = showService;
        this.seatLockService = seatLockService;
        this.paymentService = paymentService;
        this.pricingStrategy = pricingStrategy;
    }

    // ---- Observer Management ----

    public void addObserver(BookingObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(BookingObserver observer) {
        observers.remove(observer);
    }

    private void notifyBookingConfirmed(Booking booking) {
        observers.forEach(o -> o.onBookingConfirmed(booking));
    }

    private void notifyBookingCancelled(Booking booking) {
        observers.forEach(o -> o.onBookingCancelled(booking));
    }

    // ---- Phase 1: Lock Seats (User selects seats on UI) ----

    /**
     * Step 1: User selects seats → temporarily lock them.
     * Returns the locked ShowSeats so the user can see what they're about to book.
     */
    public List<ShowSeat> initiateBooking(String showId, List<String> seatIds, String userId) {
        return seatLockService.lockSeats(showId, seatIds, userId);
    }

    // ---- Phase 2: Confirm Booking (User clicks "Pay") ----

    /**
     * Step 2: User pays → finalize the booking.
     *
     * Validates that seats are still locked by this user (they might have expired!).
     * Processes payment. If payment succeeds → seats become BOOKED.
     * If payment fails → seats remain TEMPORARILY_LOCKED (will expire naturally).
     */
    public Booking confirmBooking(String showId, List<String> seatIds,
                                  User user) {
        Show show = showService.getShow(showId);
        if (show == null) {
            throw new IllegalArgumentException("Show not found: " + showId);
        }

        List<ShowSeat> seatsToBook;

        // ★ Synchronized on Show: validate locks are still held by this user
        synchronized (show) {
            seatsToBook = new ArrayList<>();
            for (String seatId : seatIds) {
                ShowSeat showSeat = show.getShowSeat(seatId);
                if (showSeat == null) {
                    throw new IllegalArgumentException("Seat not found: " + seatId);
                }

                // Seat MUST be temporarily locked by THIS user
                if (!showSeat.isLockedBy(user.getId())) {
                    throw new SeatNotAvailableException(
                            "Seat " + showSeat.getSeat() + " is no longer held for you. " +
                                    "Your lock may have expired. Please select seats again.");
                }
                seatsToBook.add(showSeat);
            }

            // Calculate price while we still have the lock context
            double totalAmount = pricingStrategy.calculatePrice(show, seatsToBook);

            // ★★★ KEY DESIGN DECISION ★★★
            // We process payment INSIDE the synchronized block in this LLD.
            // In PRODUCTION, you would:
            // 1. Release the lock here
            // 2. Process payment asynchronously
            // 3. Re-acquire the lock and finalize
            // For LLD interview simplicity, we keep it inside.
            Payment payment = paymentService.processPayment(user.getId(), totalAmount);

            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                throw new PaymentFailedException(
                        "Payment failed for user " + user.getName() +
                                ". Seats will be released when the lock expires.");
            }

            // Payment succeeded → book all seats permanently
            for (ShowSeat showSeat : seatsToBook) {
                showSeat.book();
            }

            // Create booking record
            Booking booking = new Booking(user, show, seatsToBook, payment);
            bookings.put(booking.getId(), booking);

            System.out.println("  ✅ BOOKING CONFIRMED: " + booking);

            // Notify observers (email, SMS, etc.)
            notifyBookingConfirmed(booking);

            return booking;
        }
    }

    // ---- Cancel Booking ----

    /**
     * Cancel an existing booking.
     * Seats go back to AVAILABLE. Payment is refunded.
     */
    public void cancelBooking(String bookingId) {
        Booking booking = bookings.get(bookingId);
        if (booking == null) {
            throw new BookingNotFoundException("Booking not found: " + bookingId);
        }

        Show show = booking.getShow();

        synchronized (show) {
            // Release all booked seats
            for (ShowSeat showSeat : booking.getBookedSeats()) {
                showSeat.cancelBooking();
            }

            // Refund payment
            paymentService.refundPayment(booking.getPayment());

            // Mark booking as cancelled
            booking.cancel();
        }

        System.out.println("  ❌ BOOKING CANCELLED: " + booking);

        // Notify observers
        notifyBookingCancelled(booking);
    }

    // ---- Queries ----

    public Booking getBooking(String bookingId) {
        return bookings.get(bookingId);
    }

    public List<Booking> getBookingsForUser(String userId) {
        return bookings.values().stream()
                .filter(b -> b.getUser().getId().equals(userId))
                .collect(Collectors.toList());
    }
}

