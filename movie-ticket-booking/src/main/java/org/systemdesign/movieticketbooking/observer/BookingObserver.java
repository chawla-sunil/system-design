package org.systemdesign.movieticketbooking.observer;

import org.systemdesign.movieticketbooking.model.Booking;

/**
 * Observer interface for booking lifecycle events.
 * Implementations: EmailNotification, SMSNotification, PushNotification, etc.
 */
public interface BookingObserver {
    void onBookingConfirmed(Booking booking);
    void onBookingCancelled(Booking booking);
}

