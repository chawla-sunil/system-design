package org.systemdesign.movieticketbooking.observer;

import org.systemdesign.movieticketbooking.model.Booking;

public class EmailNotificationObserver implements BookingObserver {

    @Override
    public void onBookingConfirmed(Booking booking) {
        System.out.println("  📧 EMAIL → Booking confirmed for " + booking.getUser().getName() +
                " | Movie: " + booking.getShow().getMovie().getTitle() +
                " | Seats: " + booking.getBookedSeats().size() +
                " | Amount: ₹" + booking.getPayment().getAmount() +
                " | Sent to: " + booking.getUser().getEmail());
    }

    @Override
    public void onBookingCancelled(Booking booking) {
        System.out.println("  📧 EMAIL → Booking CANCELLED for " + booking.getUser().getName() +
                " | Refund: ₹" + booking.getPayment().getAmount() +
                " | Sent to: " + booking.getUser().getEmail());
    }
}

