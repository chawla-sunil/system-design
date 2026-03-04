package org.systemdesign.parkinglot;

import org.systemdesign.parkinglot.billing.FlatRateBillingStrategy;
import org.systemdesign.parkinglot.billing.HourlyBillingStrategy;
import org.systemdesign.parkinglot.exception.ParkingLotFullException;
import org.systemdesign.parkinglot.factory.VehicleFactory;
import org.systemdesign.parkinglot.model.*;
import org.systemdesign.parkinglot.model.Payment;
import org.systemdesign.parkinglot.model.enums.SpotType;
import org.systemdesign.parkinglot.model.enums.VehicleType;
import org.systemdesign.parkinglot.service.ParkingService;
import org.systemdesign.parkinglot.service.PaymentService;
import org.systemdesign.parkinglot.service.TicketService;
import org.systemdesign.parkinglot.strategy.NearestSpotStrategy;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  PARKING LOT — LLD DEMO
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Layout:
 *    Floor 0 — Ground (near entrance)
 *      S-00, S-01  → SMALL  (bikes)
 *      M-00, M-01  → MEDIUM (cars)
 *      L-00        → LARGE  (trucks)
 *
 *    Floor 1 — Upper
 *      S-10        → SMALL
 *      M-10, M-11  → MEDIUM
 *      L-10        → LARGE
 *
 *  Scenarios demonstrated:
 *    1. Park 3 cars, 1 bike, 1 truck
 *    2. Display lot status
 *    3. Unpark one car → see fare receipt
 *    4. Try to overflow SMALL spots → ParkingLotFullException
 *    5. Switch billing strategy to FlatRate
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {

        // ── 1. Build the parking lot ────────────────────────────────
        ParkingLot.reset(); // reset singleton for demo repeatability
        ParkingLot lot = ParkingLot.getInstance("Downtown Parking", "123 Main St");

        // Floor 0
        ParkingFloor floor0 = new ParkingFloor(0);
        floor0.addSpot(new ParkingSpot("S-00", SpotType.SMALL,  0));
        floor0.addSpot(new ParkingSpot("S-01", SpotType.SMALL,  0));
        floor0.addSpot(new ParkingSpot("M-00", SpotType.MEDIUM, 0));
        floor0.addSpot(new ParkingSpot("M-01", SpotType.MEDIUM, 0));
        floor0.addSpot(new ParkingSpot("L-00", SpotType.LARGE,  0));

        // Floor 1
        ParkingFloor floor1 = new ParkingFloor(1);
        floor1.addSpot(new ParkingSpot("S-10", SpotType.SMALL,  1));
        floor1.addSpot(new ParkingSpot("M-10", SpotType.MEDIUM, 1));
        floor1.addSpot(new ParkingSpot("M-11", SpotType.MEDIUM, 1));
        floor1.addSpot(new ParkingSpot("L-10", SpotType.LARGE,  1));

        lot.addFloor(floor0);
        lot.addFloor(floor1);

        // ── 2. Wire up services (Hourly billing) ────────────────────
        ParkingService service = new ParkingService(
                lot,
                new NearestSpotStrategy(),
                new TicketService(),
                new PaymentService(new HourlyBillingStrategy())
        );

        // ── 3. Initial status ────────────────────────────────────────
        System.out.println("\n╔══════════════════════════════════╗");
        System.out.println("║     PARKING LOT DEMO — START     ║");
        System.out.println("╚══════════════════════════════════╝");
        lot.displayStatus();

        // ── 4. Park vehicles ─────────────────────────────────────────
        Vehicle bike1  = VehicleFactory.create(VehicleType.BIKE,  "BIKE-001");
        Vehicle car1   = VehicleFactory.create(VehicleType.CAR,   "CAR-001");
        Vehicle car2   = VehicleFactory.create(VehicleType.CAR,   "CAR-002");
        Vehicle car3   = VehicleFactory.create(VehicleType.CAR,   "CAR-003");
        Vehicle truck1 = VehicleFactory.create(VehicleType.TRUCK, "TRUCK-001");

        Ticket bikeTicket  = service.parkVehicle(bike1);
        Ticket car1Ticket  = service.parkVehicle(car1);
        Ticket car2Ticket  = service.parkVehicle(car2);
        Ticket car3Ticket  = service.parkVehicle(car3);
        Ticket truckTicket = service.parkVehicle(truck1);

        lot.displayStatus();

        // ── 5. Unpark car1 ───────────────────────────────────────────
        // Simulate some time passing (2 minutes) so billing has duration
        System.out.println("\n⏳ Simulating 2 minutes of parking time...");
        Thread.sleep(100); // just enough for non-zero duration in test

        System.out.println("\n╔══════════════════════════════════╗");
        System.out.println("║          EXIT GATE DEMO          ║");
        System.out.println("╚══════════════════════════════════╝");
        Payment payment1 = service.unparkVehicle(car1Ticket.getTicketId());
        printReceipt(payment1);

        lot.displayStatus();

        // ── 6. Overflow test ─────────────────────────────────────────
        System.out.println("\n╔══════════════════════════════════╗");
        System.out.println("║        OVERFLOW TEST (BIKE)      ║");
        System.out.println("╚══════════════════════════════════╝");
        // 2 SMALL spots exist; BIKE can also use MEDIUM if small is full.
        // Park bikes until the lot truly has no bike-compatible spot.
        parkUntilFull(service, VehicleType.BIKE);

        // ── 7. Flat-rate billing demo ─────────────────────────────────
        System.out.println("\n╔══════════════════════════════════╗");
        System.out.println("║    FLAT-RATE BILLING DEMO        ║");
        System.out.println("╚══════════════════════════════════╝");
        ParkingLot.reset();
        ParkingLot lot2 = ParkingLot.getInstance("Airport Parking", "Terminal 1");
        ParkingFloor af0 = new ParkingFloor(0);
        af0.addSpot(new ParkingSpot("AM-00", SpotType.MEDIUM, 0));
        lot2.addFloor(af0);

        ParkingService airportService = new ParkingService(
                lot2,
                new NearestSpotStrategy(),
                new TicketService(),
                new PaymentService(new FlatRateBillingStrategy())
        );
        Vehicle airportCar = VehicleFactory.create(VehicleType.CAR, "AIR-999");
        Ticket airTicket   = airportService.parkVehicle(airportCar);
        Thread.sleep(50);
        Payment airPayment = airportService.unparkVehicle(airTicket.getTicketId());
        printReceipt(airPayment);

        System.out.println("\n╔══════════════════════════════════╗");
        System.out.println("║         DEMO COMPLETE ✓          ║");
        System.out.println("╚══════════════════════════════════╝\n");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static void printReceipt(Payment payment) {
        System.out.println("\n  ┌─────────────── RECEIPT ───────────────┐");
        System.out.printf ("  │ Ticket  : %-30s│%n", payment.getTicket().getTicketId().substring(0, 8) + "...");
        System.out.printf ("  │ Vehicle : %-30s│%n", payment.getTicket().getVehicle());
        System.out.printf ("  │ Entry   : %-30s│%n", payment.getTicket().getEntryTime());
        System.out.printf ("  │ Exit    : %-30s│%n", payment.getTicket().getExitTime());
        System.out.printf ("  │ Amount  : $%-29.2f│%n", payment.getAmount());
        System.out.printf ("  │ Status  : %-30s│%n", payment.getStatus());
        System.out.println("  └───────────────────────────────────────┘");
    }

    private static void parkUntilFull(ParkingService service, VehicleType type) {
        int count = 0;
        while (true) {
            try {
                Vehicle v = VehicleFactory.create(type, type + "-EXTRA-" + (++count));
                service.parkVehicle(v);
            } catch (ParkingLotFullException e) {
                System.out.println("\n  ✓ ParkingLotFullException caught as expected: " + e.getMessage());
                break;
            }
        }
    }
}