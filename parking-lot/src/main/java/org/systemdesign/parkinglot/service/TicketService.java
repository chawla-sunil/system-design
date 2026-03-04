package org.systemdesign.parkinglot.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.systemdesign.parkinglot.exception.InvalidTicketException;
import org.systemdesign.parkinglot.model.ParkingSpot;
import org.systemdesign.parkinglot.model.Ticket;
import org.systemdesign.parkinglot.model.Vehicle;

/**
 * Manages ticket lifecycle: generation, lookup, and closing.
 *
 * Interview note: We store tickets in a ConcurrentHashMap keyed by ticketId
 * so multiple gate threads can issue/validate tickets concurrently without contention.
 */
public class TicketService {

    // Active tickets: ticketId → Ticket
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();

    /**
     * Issues a new ticket for a vehicle assigned to a specific spot.
     */
    public Ticket generateTicket(Vehicle vehicle, ParkingSpot spot) {
        Ticket ticket = new Ticket(vehicle, spot);
        activeTickets.put(ticket.getTicketId(), ticket);
        System.out.println("  [TicketService] Issued: " + ticket);
        return ticket;
    }

    /**
     * Validates that a ticket exists and is still active (vehicle hasn't exited yet).
     * Throws InvalidTicketException if the ticket is unknown or already closed.
     */
    public Ticket validateAndGet(String ticketId) {
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            throw new InvalidTicketException("No ticket found with ID: " + ticketId);
        }
        if (!ticket.isActive()) {
            throw new InvalidTicketException("Ticket already used (vehicle already exited): " + ticketId);
        }
        return ticket;
    }

    /**
     * Closes a ticket by setting its exit time.
     * After this the ticket is no longer "active".
     */
    public void closeTicket(Ticket ticket) {
        ticket.setExitTime(LocalDateTime.now());
        activeTickets.remove(ticket.getTicketId());
        System.out.println("  [TicketService] Closed: " + ticket);
    }

    public int getActiveTicketCount() {
        return activeTickets.size();
    }
}

