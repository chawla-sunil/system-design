package org.systemdesign.parkinglot.billing;

import java.util.Map;
import org.systemdesign.parkinglot.model.Ticket;
import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Flat-rate billing: a fixed charge per day regardless of how many hours.
 * Useful for airport parking lots.
 *
 * Rates per vehicle type:
 *   BIKE  → $5/day
 *   CAR   → $10/day
 *   TRUCK → $20/day
 *
 * Interview note: Demonstrates that switching billing strategy changes zero
 * ParkingService code — pure Strategy pattern benefit.
 */
public class FlatRateBillingStrategy implements BillingStrategy {

    private static final Map<VehicleType, Double> DEFAULT_RATES = Map.of(
            VehicleType.BIKE,  5.00,
            VehicleType.CAR,  10.00,
            VehicleType.TRUCK, 20.00
    );

    private final Map<VehicleType, Double> ratePerDay;

    public FlatRateBillingStrategy() {
        this.ratePerDay = DEFAULT_RATES;
    }

    public FlatRateBillingStrategy(Map<VehicleType, Double> ratePerDay) {
        this.ratePerDay = ratePerDay;
    }

    @Override
    public double calculateFare(Ticket ticket) {
        if (ticket.getExitTime() == null) {
            throw new IllegalStateException("Cannot calculate fare: exit time not set");
        }
        // Minimum 1 day; add 1 day for every started additional day
        long durationMinutes = java.time.temporal.ChronoUnit.MINUTES
                .between(ticket.getEntryTime(), ticket.getExitTime());
        long billableDays = Math.max(1, (long) Math.ceil(durationMinutes / (60.0 * 24)));
        double rate = ratePerDay.getOrDefault(ticket.getVehicle().getVehicleType(), 10.00);
        return billableDays * rate;
    }
}

