package org.systemdesign.carrentalsystem;

import org.systemdesign.carrentalsystem.enums.PaymentMode;
import org.systemdesign.carrentalsystem.enums.VehicleCategory;
import org.systemdesign.carrentalsystem.model.*;
import org.systemdesign.carrentalsystem.service.*;
import org.systemdesign.carrentalsystem.strategy.CreditCardPayment;
import org.systemdesign.carrentalsystem.strategy.UpiPayment;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  CAR RENTAL SYSTEM (Zoomcar) — LLD Interview Demo
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * This Main class simulates the complete end-to-end flow:
 *   1. System Initialization (Singleton)
 *   2. Create Stores (Branches)
 *   3. Add Vehicles to Stores
 *   4. Register Users
 *   5. Search Available Vehicles
 *   6. Make a Reservation (Booking)
 *   7. Start a Trip (Pick up the car)
 *   8. Complete the Trip (Return the car)
 *   9. Generate Invoice
 *  10. Process Payment (Strategy Pattern)
 *  11. Cancel a Reservation
 *  12. Double-booking prevention demo
 */
public class Main {

    public static void main(String[] args) {

        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  🚗  CAR RENTAL SYSTEM (Zoomcar) — LLD Demo");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        // ────────────────────────────────────────────────
        // STEP 1: Initialize the system (Singleton)
        // ────────────────────────────────────────────────
        VehicleRentalSystem system = VehicleRentalSystem.getInstance();

        UserService userService = system.getUserService();
        StoreService storeService = system.getStoreService();
        VehicleService vehicleService = system.getVehicleService();
        ReservationService reservationService = system.getReservationService();
        PaymentService paymentService = system.getPaymentService();

        System.out.println("✅ Step 1: VehicleRentalSystem initialized (Singleton)\n");

        // ────────────────────────────────────────────────
        // STEP 2: Create Stores (Branches)
        // ────────────────────────────────────────────────
        Store bangaloreStore = storeService.addStore(
                "Zoomcar Koramangala", "80 Feet Road, Koramangala", "Bangalore");
        Store mumbaiStore = storeService.addStore(
                "Zoomcar Andheri", "Link Road, Andheri West", "Mumbai");

        System.out.println("✅ Step 2: Stores created");
        System.out.println("   " + bangaloreStore);
        System.out.println("   " + mumbaiStore);
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 3: Add Vehicles to Stores
        // ────────────────────────────────────────────────
        VehicleType sedanType = new VehicleType(VehicleCategory.SEDAN,
                Arrays.asList("AC", "Automatic", "Leather Seats", "Panoramic Sunroof", "5-seater"));
        VehicleType suvType = new VehicleType(VehicleCategory.SUV,
                Arrays.asList("AC", "4WD", "Leather Seats", "Air Suspension", "7-seater"));
        VehicleType sportsType = new VehicleType(VehicleCategory.SPORTS,
                Arrays.asList("AC", "Automatic", "Rear-Engine", "Sport Exhaust", "2-seater"));
        VehicleType grandTourerType = new VehicleType(VehicleCategory.GRAND_TOURER,
                Arrays.asList("AC", "Automatic", "Leather Seats", "W12 Engine", "4-seater"));

        // Bangalore store vehicles
        Vehicle porsche911 = new Vehicle("KA-01-AB-1234", "Porsche", "911 Carrera", 2025, sportsType, 5000.0);
        Vehicle mercedesSClass = new Vehicle("KA-01-CD-5678", "Mercedes-Benz", "S-Class", 2025, sedanType, 4500.0);
        Vehicle lamborghiniUrus = new Vehicle("KA-01-EF-9012", "Lamborghini", "Urus", 2025, suvType, 8000.0);
        Vehicle rollsRoyceCullinan = new Vehicle("KA-01-GH-3456", "Rolls-Royce", "Cullinan", 2025, suvType, 12000.0);

        storeService.addVehicleToStore(bangaloreStore, porsche911);
        storeService.addVehicleToStore(bangaloreStore, mercedesSClass);
        storeService.addVehicleToStore(bangaloreStore, lamborghiniUrus);
        storeService.addVehicleToStore(bangaloreStore, rollsRoyceCullinan);

        // Mumbai store vehicles
        Vehicle ferrariRoma = new Vehicle("MH-01-AB-1111", "Ferrari", "Roma", 2025, sportsType, 7500.0);
        Vehicle bentleyContinental = new Vehicle("MH-01-CD-2222", "Bentley", "Continental GT", 2025, grandTourerType, 9000.0);

        storeService.addVehicleToStore(mumbaiStore, ferrariRoma);
        storeService.addVehicleToStore(mumbaiStore, bentleyContinental);

        System.out.println("✅ Step 3: Vehicles added to stores");
        System.out.println("   Bangalore Store (" + bangaloreStore.getVehicles().size() + " vehicles):");
        bangaloreStore.getVehicles().forEach(v -> System.out.println("     • " + v));
        System.out.println("   Mumbai Store (" + mumbaiStore.getVehicles().size() + " vehicles):");
        mumbaiStore.getVehicles().forEach(v -> System.out.println("     • " + v));
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 4: Register Users
        // ─────────���──────────────────────────────────────
        User sunil = userService.registerUser("Sunil Chawla", "sunil@example.com",
                "9876543210", "DL-BLR-2020-12345");
        User priya = userService.registerUser("Test User", "test@example.com",
                "9876543211", "DL-MUM-2021-67890");

        System.out.println("✅ Step 4: Users registered");
        System.out.println("   " + sunil);
        System.out.println("   " + priya);
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 5: Search Available Vehicles
        // ────────────────────────────────────────────────
        LocalDateTime searchStart = LocalDateTime.now().plusHours(1);
        LocalDateTime searchEnd = searchStart.plusHours(6);

        System.out.println("✅ Step 5: Searching available vehicles");
        System.out.println("   📍 Store: " + bangaloreStore.getName());
        System.out.println("   🕐 From: " + searchStart + " To: " + searchEnd);

        // Search all categories
        List<Vehicle> allAvailable = vehicleService.getAvailableVehicles(
                bangaloreStore, searchStart, searchEnd, null);
        System.out.println("   All available (" + allAvailable.size() + "):");
        allAvailable.forEach(v -> System.out.println("     • " + v));

        // Search only SUVs
        List<Vehicle> availableSuvs = vehicleService.getAvailableVehicles(
                bangaloreStore, searchStart, searchEnd, VehicleCategory.SUV);
        System.out.println("   Available SUVs (" + availableSuvs.size() + "):");
        availableSuvs.forEach(v -> System.out.println("     • " + v));

        // Search only Sports Cars
        List<Vehicle> availableSports = vehicleService.getAvailableVehicles(
                bangaloreStore, searchStart, searchEnd, VehicleCategory.SPORTS);
        System.out.println("   Available Sports Cars (" + availableSports.size() + "):");
        availableSports.forEach(v -> System.out.println("     • " + v));
        System.out.println();

        // ───────────────────��────────────────────────────
        // STEP 6: Make a Reservation
        // ────────────────────────────────────────────────
        LocalDateTime bookingStart = LocalDateTime.now().plusHours(1);
        LocalDateTime bookingEnd = bookingStart.plusHours(8);

        Reservation reservation1 = reservationService.createReservation(
                sunil, bangaloreStore, lamborghiniUrus, bookingStart, bookingEnd);

        System.out.println("✅ Step 6: Reservation created");
        System.out.println("   " + reservation1);
        System.out.println("   Vehicle status after booking: " + lamborghiniUrus.getStatus());
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 7: Start the Trip
        // ────────────────────────────────────────────────
        reservationService.startTrip(reservation1.getId());

        System.out.println("✅ Step 7: Trip started");
        System.out.println("   Reservation status: " + reservation1.getStatus());
        System.out.println("   Vehicle status: " + lamborghiniUrus.getStatus());
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 8: Complete the Trip (Return the car)
        // ────────────────────────────────────────────────
        // Simulate returning after 8 hours
        LocalDateTime returnTime = bookingStart.plusHours(8);
        Invoice invoice1 = reservationService.completeReservation(reservation1.getId(), returnTime);

        System.out.println("✅ Step 8: Trip completed — Car returned");
        System.out.println("   Reservation status: " + reservation1.getStatus());
        System.out.println("   Vehicle status: " + lamborghiniUrus.getStatus());
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 9: Invoice Generated
        // ────────────────────────────────────────────────
        System.out.println("✅ Step 9: Invoice generated");
        System.out.println("   " + invoice1);
        System.out.println("   Breakdown:");
        System.out.println("     Base Cost   : ₹" + String.format("%.2f", invoice1.getBill().getBaseCost()));
        System.out.println("     Tax (18%)   : ₹" + String.format("%.2f", invoice1.getBill().getTaxAmount()));
        System.out.println("     Discount    : ₹" + String.format("%.2f", invoice1.getBill().getDiscount()));
        System.out.println("     ─────────────────────");
        System.out.println("     Total       : ₹" + String.format("%.2f", invoice1.getTotalAmount()));
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 10: Process Payment (Strategy Pattern)
        // ────────────────────────────────────────────────
        System.out.println("✅ Step 10: Processing payment (Strategy Pattern)");
        Payment payment1 = paymentService.processPayment(
                invoice1,
                PaymentMode.CREDIT_CARD,
                new CreditCardPayment("4111222233334444", "Sunil Chawla")
        );
        System.out.println("   " + payment1);
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 11: Second User — Book + Cancel Flow
        // ────────────────────────────────────────────────
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  🔄  Demonstrating Cancellation Flow");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        LocalDateTime bookStart2 = LocalDateTime.now().plusHours(5);
        LocalDateTime bookEnd2 = bookStart2.plusHours(4);

        Reservation reservation2 = reservationService.createReservation(
                priya, bangaloreStore, mercedesSClass, bookStart2, bookEnd2);

        System.out.println("   Reservation created: " + reservation2);
        System.out.println("   Vehicle status: " + mercedesSClass.getStatus());

        // Cancel the reservation
        Invoice cancelInvoice = reservationService.cancelReservation(reservation2.getId());

        System.out.println("   ❌ Reservation cancelled!");
        System.out.println("   Reservation status: " + reservation2.getStatus());
        System.out.println("   Vehicle status: " + mercedesSClass.getStatus());
        System.out.println("   Cancellation " + cancelInvoice);
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 12: Double-Booking Prevention Demo
        // ────────────────────────────────────────────────
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  🚫  Demonstrating Double-Booking Prevention");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        // Book the Rolls-Royce Cullinan
        LocalDateTime dbStart = LocalDateTime.now().plusHours(2);
        LocalDateTime dbEnd = dbStart.plusHours(5);

        Reservation res3 = reservationService.createReservation(
                sunil, bangaloreStore, rollsRoyceCullinan, dbStart, dbEnd);
        System.out.println("   ✅ First booking for Rolls-Royce Cullinan: " + res3.getId());

        // Try to book the same Rolls-Royce Cullinan for an overlapping period
        try {
            LocalDateTime overlapStart = dbStart.plusHours(2);
            LocalDateTime overlapEnd = overlapStart.plusHours(4);

            reservationService.createReservation(
                    priya, bangaloreStore, rollsRoyceCullinan, overlapStart, overlapEnd);
            System.out.println("   ❌ ERROR: This should not have succeeded!");
        } catch (Exception e) {
            System.out.println("   🚫 Double-booking prevented: " + e.getMessage());
        }
        System.out.println();

        // ────────────────────────────────────────────────
        // STEP 13: UPI Payment Demo (Strategy Pattern)
        // ────────────────────────────────────────────────
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  📱  Demonstrating UPI Payment (Strategy Pattern)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        // Priya books the Porsche 911, goes on a trip, returns, and pays via UPI
        LocalDateTime upiStart = LocalDateTime.now().plusHours(3);
        LocalDateTime upiEnd = upiStart.plusHours(3);

        Reservation res4 = reservationService.createReservation(
                priya, bangaloreStore, porsche911, upiStart, upiEnd);
        reservationService.startTrip(res4.getId());
        Invoice upiInvoice = reservationService.completeReservation(
                res4.getId(), upiStart.plusHours(3));

        System.out.println("   Invoice: " + upiInvoice);
        Payment upiPayment = paymentService.processPayment(
                upiInvoice,
                PaymentMode.UPI,
                new UpiPayment("priya@upi")
        );
        System.out.println("   " + upiPayment);
        System.out.println();

        // ────────────────────────────────────────────────
        // SUMMARY
        // ────────────────────────────────────────────────
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  📊  System Summary");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        System.out.println("   Total Stores       : " + storeService.getAllStores().size());
        System.out.println("   Total Users        : " + userService.getAllUsers().size());
        System.out.println("   Total Reservations : " + reservationService.getAllReservations().size());
        System.out.println("   Total Payments     : " + paymentService.getAllPayments().size());
        System.out.println();

        System.out.println("   All Reservations:");
        reservationService.getAllReservations().forEach(r ->
                System.out.println("     • [" + r.getStatus() + "] " + r.getId() +
                        " — " + r.getUser().getName() + " → " +
                        r.getVehicle().getBrand() + " " + r.getVehicle().getModel()));

        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("  ✅  LLD Demo Complete!");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }
}