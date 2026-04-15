package org.systemdesign.carrentalsystem.model;

import org.systemdesign.carrentalsystem.enums.ReservationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class Reservation {
    private final String id;
    private final User user;
    private final Vehicle vehicle;
    private final Store store;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private LocalDateTime actualReturnTime;
    private ReservationStatus status;
    private Invoice invoice;

    public Reservation(User user, Vehicle vehicle, Store store,
                       LocalDateTime startTime, LocalDateTime endTime) {
        this.id = "RES-" + UUID.randomUUID().toString().substring(0, 8);
        this.user = user;
        this.vehicle = vehicle;
        this.store = store;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = ReservationStatus.SCHEDULED;
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public Vehicle getVehicle() { return vehicle; }
    public Store getStore() { return store; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public LocalDateTime getActualReturnTime() { return actualReturnTime; }
    public void setActualReturnTime(LocalDateTime actualReturnTime) { this.actualReturnTime = actualReturnTime; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }

    /**
     * Check if this reservation overlaps with the given time range.
     * Used to prevent double-booking.
     */
    public boolean overlaps(LocalDateTime start, LocalDateTime end) {
        if (this.status == ReservationStatus.CANCELLED ||
            this.status == ReservationStatus.COMPLETED) {
            return false;
        }
        return this.startTime.isBefore(end) && this.endTime.isAfter(start);
    }

    @Override
    public String toString() {
        return "Reservation{id='" + id + "', user=" + user.getName() +
                ", vehicle=" + vehicle.getBrand() + " " + vehicle.getModel() +
                ", store=" + store.getName() +
                ", from=" + startTime + ", to=" + endTime +
                ", status=" + status + "}";
    }
}

