package org.systemdesign.carrentalsystem.service;

import org.systemdesign.carrentalsystem.enums.VehicleCategory;
import org.systemdesign.carrentalsystem.enums.VehicleStatus;
import org.systemdesign.carrentalsystem.model.Reservation;
import org.systemdesign.carrentalsystem.model.Store;
import org.systemdesign.carrentalsystem.model.Vehicle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class VehicleService {

    /**
     * Get available vehicles at a given store for the specified time range.
     * Optionally filter by vehicle category.
     *
     * KEY ALGORITHM: Checks both vehicle status AND overlapping reservations
     * to prevent double-booking.
     */
    public List<Vehicle> getAvailableVehicles(Store store, LocalDateTime startTime,
                                               LocalDateTime endTime, VehicleCategory category) {
        return store.getVehicles().stream()
                // 1. Filter by category if specified
                .filter(v -> category == null || v.getVehicleType().getCategory() == category)
                // 2. Vehicle must be in AVAILABLE status
                .filter(v -> v.getStatus() == VehicleStatus.AVAILABLE)
                // 3. No overlapping reservations for this vehicle in the time range
                .filter(v -> !hasOverlappingReservation(store, v, startTime, endTime))
                .collect(Collectors.toList());
    }

    /**
     * Check if a vehicle has any overlapping active reservation in the given time range.
     */
    private boolean hasOverlappingReservation(Store store, Vehicle vehicle,
                                              LocalDateTime startTime, LocalDateTime endTime) {
        return store.getReservations().stream()
                .filter(r -> r.getVehicle().getId().equals(vehicle.getId()))
                .anyMatch(r -> r.overlaps(startTime, endTime));
    }

    /**
     * Check if a specific vehicle is available for the given time range at a store.
     */
    public boolean isVehicleAvailable(Store store, Vehicle vehicle,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            return false;
        }
        return !hasOverlappingReservation(store, vehicle, startTime, endTime);
    }

    /**
     * Get all vehicles at a store (regardless of availability).
     */
    public List<Vehicle> getAllVehicles(Store store) {
        return store.getVehicles();
    }

    /**
     * Get vehicles by status at a store.
     */
    public List<Vehicle> getVehiclesByStatus(Store store, VehicleStatus status) {
        return store.getVehicles().stream()
                .filter(v -> v.getStatus() == status)
                .collect(Collectors.toList());
    }
}

