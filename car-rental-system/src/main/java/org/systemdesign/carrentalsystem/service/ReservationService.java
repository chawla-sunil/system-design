package org.systemdesign.carrentalsystem.service;

import org.systemdesign.carrentalsystem.enums.ReservationStatus;
import org.systemdesign.carrentalsystem.enums.VehicleStatus;
import org.systemdesign.carrentalsystem.exception.ReservationNotFoundException;
import org.systemdesign.carrentalsystem.exception.VehicleNotAvailableException;
import org.systemdesign.carrentalsystem.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReservationService {

    private final List<Reservation> reservations;
    private final VehicleService vehicleService;
    private final InvoiceService invoiceService;

    public ReservationService(VehicleService vehicleService, InvoiceService invoiceService) {
        this.reservations = new ArrayList<>();
        this.vehicleService = vehicleService;
        this.invoiceService = invoiceService;
    }

    /**
     * Create a new reservation.
     * This is the CORE method — it validates availability, creates the reservation,
     * marks the vehicle as RESERVED, and links everything together.
     */
    public Reservation createReservation(User user, Store store, Vehicle vehicle,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        // Step 1: Validate the vehicle is available for the time range
        if (!vehicleService.isVehicleAvailable(store, vehicle, startTime, endTime)) {
            throw new VehicleNotAvailableException(
                    "Vehicle " + vehicle.getBrand() + " " + vehicle.getModel() +
                            " (" + vehicle.getLicensePlate() + ") is not available from " +
                            startTime + " to " + endTime);
        }

        // Step 2: Validate time range
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }

        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Cannot book for a past time.");
        }

        // Step 3: Create the reservation
        Reservation reservation = new Reservation(user, vehicle, store, startTime, endTime);

        // Step 4: Mark vehicle as RESERVED
        vehicle.setStatus(VehicleStatus.RESERVED);

        // Step 5: Link reservation to store and user
        store.addReservation(reservation);
        user.addReservation(reservation);
        reservations.add(reservation);

        return reservation;
    }

    /**
     * Cancel a reservation.
     * Only SCHEDULED reservations can be cancelled.
     */
    public Invoice cancelReservation(String reservationId) {
        Reservation reservation = findReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "Cannot cancel reservation with status: " + reservation.getStatus());
        }

        // Mark reservation as cancelled
        reservation.setStatus(ReservationStatus.CANCELLED);

        // Release the vehicle
        reservation.getVehicle().setStatus(VehicleStatus.AVAILABLE);

        // Generate cancellation invoice (may have cancellation fee)
        return invoiceService.generateCancellationInvoice(reservation);
    }

    /**
     * Start a trip — marks the reservation as IN_PROGRESS and vehicle as RENTED.
     */
    public Reservation startTrip(String reservationId) {
        Reservation reservation = findReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "Cannot start trip for reservation with status: " + reservation.getStatus());
        }

        reservation.setStatus(ReservationStatus.IN_PROGRESS);
        reservation.getVehicle().setStatus(VehicleStatus.RENTED);

        return reservation;
    }

    /**
     * Complete a reservation (return the car).
     * Generates an invoice based on actual usage.
     */
    public Invoice completeReservation(String reservationId) {
        Reservation reservation = findReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot complete reservation with status: " + reservation.getStatus());
        }

        // Set actual return time
        reservation.setActualReturnTime(LocalDateTime.now());

        // Mark reservation as completed
        reservation.setStatus(ReservationStatus.COMPLETED);

        // Release the vehicle
        reservation.getVehicle().setStatus(VehicleStatus.AVAILABLE);

        // Generate invoice
        return invoiceService.generateInvoice(reservation);
    }

    /**
     * Complete a reservation with a specific return time (for simulation/testing).
     */
    public Invoice completeReservation(String reservationId, LocalDateTime returnTime) {
        Reservation reservation = findReservationById(reservationId);

        if (reservation.getStatus() != ReservationStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot complete reservation with status: " + reservation.getStatus());
        }

        reservation.setActualReturnTime(returnTime);
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.getVehicle().setStatus(VehicleStatus.AVAILABLE);

        return invoiceService.generateInvoice(reservation);
    }

    public Reservation findReservationById(String reservationId) {
        return reservations.stream()
                .filter(r -> r.getId().equals(reservationId))
                .findFirst()
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found: " + reservationId));
    }

    public List<Reservation> getReservationsByUser(User user) {
        return reservations.stream()
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .toList();
    }

    public List<Reservation> getReservationsByStore(Store store) {
        return reservations.stream()
                .filter(r -> r.getStore().getId().equals(store.getId()))
                .toList();
    }

    public List<Reservation> getAllReservations() {
        return new ArrayList<>(reservations);
    }
}

