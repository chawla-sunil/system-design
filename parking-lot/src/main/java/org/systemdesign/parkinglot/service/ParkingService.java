package org.systemdesign.parkinglot.service;

import org.systemdesign.parkinglot.exception.ParkingLotFullException;
import org.systemdesign.parkinglot.model.ParkingLot;
import org.systemdesign.parkinglot.model.ParkingSpot;
import org.systemdesign.parkinglot.model.Payment;
import org.systemdesign.parkinglot.model.Ticket;
import org.systemdesign.parkinglot.model.Vehicle;
import org.systemdesign.parkinglot.strategy.ParkingStrategy;

/**
 * Main orchestrator: coordinates spot assignment, ticketing, and payment.
 *
 * Interview note: ParkingService is the "facade" for the entire parking operation.
 * It uses:
 *   - ParkingStrategy  → which spot to assign (pluggable)
 *   - TicketService    → ticket lifecycle
 *   - PaymentService   → fare + payment (pluggable billing)
 *
 * This separation of concerns means each class has one reason to change (SRP).
 */
public class ParkingService {

    private final ParkingLot parkingLot;
    private final ParkingStrategy parkingStrategy;
    private final TicketService ticketService;
    private final PaymentService paymentService;

    public ParkingService(ParkingLot parkingLot,
                          ParkingStrategy parkingStrategy,
                          TicketService ticketService,
                          PaymentService paymentService) {
        this.parkingLot      = parkingLot;
        this.parkingStrategy = parkingStrategy;
        this.ticketService   = ticketService;
        this.paymentService  = paymentService;
    }

    // ──────────────────────────────────────────────
    //  PARK  (Entry gate)
    // ──────────────────────────────────────────────

    /**
     * Parks a vehicle:
     * 1. Find the best available spot (via strategy)
     * 2. Assign the vehicle to that spot
     * 3. Issue a ticket
     *
     * Thread-safety: assignVehicle() uses a per-spot ReentrantLock,
     * so two threads racing for the same spot are handled gracefully —
     * the loser retries via the strategy.
     */
    public Ticket parkVehicle(Vehicle vehicle) {
        System.out.println("\n[ENTRY] Parking: " + vehicle);

        ParkingSpot spot = null;
        int retries = 3;

        // Retry loop handles the rare race condition where two threads
        // find the same spot but only one can claim it.
        while (retries-- > 0) {
            spot = parkingStrategy.findSpot(parkingLot, vehicle.getVehicleType());
            if (spot == null) {
                throw new ParkingLotFullException(
                        "No available spot for vehicle type: " + vehicle.getVehicleType());
            }
            if (spot.assignVehicle(vehicle)) {
                break; // Successfully assigned
            }
            spot = null; // Another thread grabbed it — retry
        }

        if (spot == null) {
            throw new ParkingLotFullException(
                    "Failed to assign spot after retries for: " + vehicle.getVehicleType());
        }

        Ticket ticket = ticketService.generateTicket(vehicle, spot);
        System.out.println("[ENTRY] Success → " + spot);
        return ticket;
    }

    // ──────────────────────────────────────────────
    //  UNPARK  (Exit gate)
    // ──────────────────────────────────────────────

    /**
     * Unparks a vehicle:
     * 1. Validate the ticket
     * 2. Close the ticket (sets exitTime)
     * 3. Calculate fare & process payment
     * 4. Free the parking spot
     *
     * Interview note: The spot is freed ONLY after successful payment.
     * If payment fails, the vehicle cannot exit (spot stays OCCUPIED).
     */
    public Payment unparkVehicle(String ticketId) {
        System.out.println("\n[EXIT] Processing ticket: " + ticketId.substring(0, 8) + "...");

        // Step 1 & 2: Validate + close ticket
        Ticket ticket = ticketService.validateAndGet(ticketId);
        ticketService.closeTicket(ticket);

        // Step 3: Billing
        Payment payment = paymentService.createPayment(ticket);
        paymentService.processPayment(payment);

        // Step 4: Free spot (only after payment)
        ticket.getParkingSpot().removeVehicle();
        System.out.println("[EXIT] Spot freed → " + ticket.getParkingSpot());

        return payment;
    }

    public TicketService getTicketService() { return ticketService; }
}

