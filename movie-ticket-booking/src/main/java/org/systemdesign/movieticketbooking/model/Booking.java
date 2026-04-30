package org.systemdesign.movieticketbooking.model;

import org.systemdesign.movieticketbooking.model.enums.BookingStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Booking {
    private final String id;
    private final User user;
    private final Show show;
    private final List<ShowSeat> bookedSeats;
    private final Payment payment;
    private BookingStatus status;
    private final LocalDateTime bookingTime;

    public Booking(User user, Show show, List<ShowSeat> bookedSeats, Payment payment) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.show = show;
        this.bookedSeats = bookedSeats;
        this.payment = payment;
        this.status = BookingStatus.CONFIRMED;
        this.bookingTime = LocalDateTime.now();
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public Show getShow() { return show; }
    public List<ShowSeat> getBookedSeats() { return Collections.unmodifiableList(bookedSeats); }
    public Payment getPayment() { return payment; }
    public BookingStatus getStatus() { return status; }
    public LocalDateTime getBookingTime() { return bookingTime; }

    @Override
    public String toString() {
        return "Booking{id='" + id.substring(0, 8) + "...', user=" + user.getName() +
                ", movie='" + show.getMovie().getTitle() + "', seats=" + bookedSeats.size() +
                ", amount=₹" + payment.getAmount() + ", status=" + status + "}";
    }
}

