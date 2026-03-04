package org.systemdesign.parkinglot.billing;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.systemdesign.parkinglot.model.Ticket;
import org.systemdesign.parkinglot.model.enums.VehicleType;

/**
 * Hourly billing: charges per hour (rounded UP), minimum 1 hour.
 *
 * Rate example (per hour):
 *   BIKE  → $1.00
 *   CAR   → $2.00
 *   TRUCK → $4.00
 *
 * Interview note:
 *   - We use Math.ceil on minutes/60 to round UP to full hours.
 *   - Using ChronoUnit.MINUTES handles midnight crossings correctly
 *     (e.g., entry 11 PM, exit 1 AM → 120 minutes → 2 hours billed).
 *   - Rate is injected via Map so adding EV rates needs zero code changes here.
 */
public class HourlyBillingStrategy implements BillingStrategy {

    // Default rates per vehicle type per hour (in dollars)
    private static final Map<VehicleType, Double> DEFAULT_RATES = Map.of(
            VehicleType.BIKE,  1.00,
            VehicleType.CAR,   2.00,
            VehicleType.TRUCK, 4.00
    );

    private final Map<VehicleType, Double> ratePerHour;

    /** Constructor with default rates. */
    public HourlyBillingStrategy() {
        this.ratePerHour = DEFAULT_RATES;
    }

    /** Constructor with custom rates (useful for injection / testing). */
    public HourlyBillingStrategy(Map<VehicleType, Double> ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    @Override
    public double calculateFare(Ticket ticket) {
        if (ticket.getExitTime() == null) {
            throw new IllegalStateException("Cannot calculate fare: exit time not set on ticket " + ticket.getTicketId());
        }

        long totalMinutes = ChronoUnit.MINUTES.between(ticket.getEntryTime(), ticket.getExitTime());

        // Minimum charge: 1 hour. Round UP to next full hour.
        long billableHours = Math.max(1, (long) Math.ceil(totalMinutes / 60.0));

        double rate = ratePerHour.getOrDefault(ticket.getVehicle().getVehicleType(), 2.00);
        return billableHours * rate;
    }
}

