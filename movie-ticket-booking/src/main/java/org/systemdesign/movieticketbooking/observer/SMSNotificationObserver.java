package org.systemdesign.movieticketbooking.observer;

import org.systemdesign.movieticketbooking.model.Booking;

public class SMSNotificationObserver implements BookingObserver {

    @Override
    public void onBookingConfirmed(Booking booking) {
        System.out.println("  📱 SMS → Booking confirmed! Movie: " +
                booking.getShow().getMovie().getTitle() +
                " | Sent to: " + booking.getUser().getPhone());
    }

    @Override
    public void onBookingCancelled(Booking booking) {
        System.out.println("  📱 SMS → Booking cancelled. Refund initiated." +
                " | Sent to: " + booking.getUser().getPhone());
    }
}

