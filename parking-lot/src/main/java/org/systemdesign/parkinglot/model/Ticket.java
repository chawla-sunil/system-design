package org.systemdesign.parkinglot.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Ticket is issued at entry and used at exit for billing.
 *
 * Interview note: ticketId uses UUID for global uniqueness across distributed gates.
 * exitTime is nullable (null = vehicle still parked).
 */
public class Ticket {

    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot parkingSpot;
    private final int floorNumber;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;   // set on exit

    public Ticket(Vehicle vehicle, ParkingSpot parkingSpot) {
        this.ticketId    = UUID.randomUUID().toString();
        this.vehicle     = vehicle;
        this.parkingSpot = parkingSpot;
        this.floorNumber = parkingSpot.getFloorNumber();
        this.entryTime   = LocalDateTime.now();
    }

    // Called by TicketService on exit
    public void setExitTime(LocalDateTime exitTime) {
        if (exitTime.isBefore(this.entryTime)) {
            throw new IllegalArgumentException("Exit time cannot be before entry time");
        }
        this.exitTime = exitTime;
    }

    public boolean isActive()            { return exitTime == null; }

    public String getTicketId()          { return ticketId; }
    public Vehicle getVehicle()          { return vehicle; }
    public ParkingSpot getParkingSpot()  { return parkingSpot; }
    public int getFloorNumber()          { return floorNumber; }
    public LocalDateTime getEntryTime()  { return entryTime; }
    public LocalDateTime getExitTime()   { return exitTime; }

    @Override
    public String toString() {
        return String.format(
            "Ticket[%s | %s | Spot:%s | Floor:%d | Entry:%s]",
            ticketId.substring(0, 8), vehicle, parkingSpot.getSpotId(), floorNumber, entryTime);
    }
}

