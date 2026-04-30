package org.systemdesign.movieticketbooking;

import org.systemdesign.movieticketbooking.controller.MovieTicketBookingSystem;
import org.systemdesign.movieticketbooking.exception.SeatNotAvailableException;
import org.systemdesign.movieticketbooking.exception.SeatTemporarilyLockedException;
import org.systemdesign.movieticketbooking.model.*;
import org.systemdesign.movieticketbooking.model.enums.City;
import org.systemdesign.movieticketbooking.model.enums.SeatType;
import org.systemdesign.movieticketbooking.observer.EmailNotificationObserver;
import org.systemdesign.movieticketbooking.observer.SMSNotificationObserver;
import org.systemdesign.movieticketbooking.strategy.BasePricingStrategy;
import org.systemdesign.movieticketbooking.strategy.PeakHourPricingStrategy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ★ Movie Ticket Booking System — Full Demo ★
 *
 * Demonstrates:
 * 1. System setup (movies, theatres, screens, shows)
 * 2. Normal booking flow (select → lock → pay → confirm)
 * 3. Cancellation flow
 * 4. ★ Concurrency: two users trying to book the SAME seats simultaneously
 * 5. Observer notifications (email + SMS)
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║       🎬 Movie Ticket Booking System (BookMyShow LLD)       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // ── 1. Initialize System with Pricing Strategy ──
        BasePricingStrategy baseStrategy = new BasePricingStrategy();
        // Weekend pricing: 1.5x multiplier
        PeakHourPricingStrategy weekendPricing = new PeakHourPricingStrategy(baseStrategy, 1.5);

        MovieTicketBookingSystem system = MovieTicketBookingSystem.getInstance(weekendPricing);

        // Register notification observers
        system.addBookingObserver(new EmailNotificationObserver());
        system.addBookingObserver(new SMSNotificationObserver());

        // ── 2. Setup: Create Movies ──
        System.out.println("\n━━━ 🎬 Setting Up Movies ━━━");
        Movie inception = new Movie("Inception", Duration.ofMinutes(148), "Sci-Fi", "English");
        Movie rrr = new Movie("RRR", Duration.ofMinutes(187), "Action", "Telugu");

        system.addMovie(inception, List.of(City.BANGALORE, City.MUMBAI));
        system.addMovie(rrr, List.of(City.BANGALORE, City.HYDERABAD, City.CHENNAI));
        System.out.println("Added: " + inception);
        System.out.println("Added: " + rrr);

        // ── 3. Setup: Create Theatre with Screens and Seats ──
        System.out.println("\n━━━ 🏢 Setting Up Theatres ━━━");
        Theatre pvr = new Theatre("T1", "PVR Orion Mall", City.BANGALORE);

        // Screen 1: 3 rows × 5 cols = 15 seats (5 Silver, 5 Gold, 5 Platinum)
        Screen screen1 = new Screen("S1", "Screen 1 - IMAX");
        int seatCounter = 1;
        for (int row = 1; row <= 3; row++) {
            SeatType type = row == 1 ? SeatType.SILVER : (row == 2 ? SeatType.GOLD : SeatType.PLATINUM);
            for (int col = 1; col <= 5; col++) {
                screen1.addSeat(new Seat("SEAT-" + seatCounter++, row, col, type));
            }
        }
        pvr.addScreen(screen1);
        system.addTheatre(pvr);
        System.out.println("Created: " + pvr);
        System.out.println("  " + screen1 + " — Seats: " + screen1.getSeats());

        // ── 4. Setup: Create Shows ──
        System.out.println("\n━━━ 🕐 Setting Up Shows ━━━");
        Show morningShow = new Show("SHOW-1", inception, screen1,
                LocalDateTime.now().plusHours(2));
        Show eveningShow = new Show("SHOW-2", rrr, screen1,
                LocalDateTime.now().plusHours(6));

        system.addShow(morningShow);
        system.addShow(eveningShow);
        System.out.println("Added: " + morningShow);
        System.out.println("Added: " + eveningShow);

        // ── 5. Create Users ──
        User alice = new User("Alice", "alice@email.com", "+91-9876543210");
        User bob = new User("Bob", "bob@email.com", "+91-9876543211");
        User charlie = new User("Charlie", "charlie@email.com", "+91-9876543212");

        // ═══════════════════════════════════════════════════════════
        // DEMO 1: Normal Booking Flow
        // ═══════════════════════════════════════════════════════════
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  DEMO 1: Normal Booking Flow (Alice books 3 Gold seats)     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // Step 1: Browse available seats
        List<ShowSeat> available = system.getAvailableSeats("SHOW-1");
        System.out.println("\n📋 Available seats for Inception (morning): " + available.size());

        // Step 2: Alice selects 3 Gold seats (SEAT-6, SEAT-7, SEAT-8)
        List<String> aliceSeats = List.of("SEAT-6", "SEAT-7", "SEAT-8");
        System.out.println("\n🟡 Alice selects Gold seats: " + aliceSeats);
        system.selectSeats("SHOW-1", aliceSeats, alice.getId());

        // Check available seats decreased
        available = system.getAvailableSeats("SHOW-1");
        System.out.println("📋 Available seats after Alice's selection: " + available.size());

        // Step 3: Alice confirms booking (pays)
        System.out.println("\n💰 Alice proceeds to payment...");
        Booking aliceBooking = system.confirmBooking("SHOW-1", aliceSeats, alice);

        // Check available seats after booking
        available = system.getAvailableSeats("SHOW-1");
        System.out.println("\n📋 Available seats after Alice's booking: " + available.size());

        // ═══════════════════════════════════════════════════════════
        // DEMO 2: Cancellation Flow
        // ═══════════════════════════════════════════════════════════
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  DEMO 2: Cancellation Flow (Alice cancels her booking)      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        system.cancelBooking(aliceBooking.getId());

        available = system.getAvailableSeats("SHOW-1");
        System.out.println("\n📋 Available seats after cancellation: " + available.size() + " (seats released!)");

        // ═══════════════════════════════════════════════════════════
        // DEMO 3: ★ CONCURRENCY — Two users race for the SAME seats
        // ═══════════════════════════════════════════════════════════
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  DEMO 3: ★ CONCURRENCY — Bob & Charlie race for same seats  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        List<String> contestedSeats = List.of("SEAT-11", "SEAT-12", "SEAT-13"); // Platinum seats
        System.out.println("\n🏁 Both Bob and Charlie want Platinum seats: " + contestedSeats);

        CountDownLatch startGun = new CountDownLatch(1);  // ensure both threads start at same time
        CountDownLatch finish = new CountDownLatch(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Bob tries to select and book
        executor.submit(() -> {
            try {
                startGun.await(); // wait for the "go" signal
                System.out.println("\n  🏃 Bob: Trying to select seats...");
                system.selectSeats("SHOW-1", contestedSeats, bob.getId());
                System.out.println("  🟢 Bob: Seats LOCKED! Proceeding to payment...");

                // Simulate slight delay before payment
                Thread.sleep(100);

                Booking bobBooking = system.confirmBooking("SHOW-1", contestedSeats, bob);
                System.out.println("  🎉 Bob: BOOKING SUCCESSFUL! " + bobBooking);
            } catch (SeatTemporarilyLockedException e) {
                System.out.println("  🔴 Bob: FAILED — " + e.getMessage());
            } catch (SeatNotAvailableException e) {
                System.out.println("  🔴 Bob: FAILED — " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  🔴 Bob: ERROR — " + e.getMessage());
            } finally {
                finish.countDown();
            }
        });

        // Thread 2: Charlie tries to select and book the SAME seats
        executor.submit(() -> {
            try {
                startGun.await(); // wait for the "go" signal
                System.out.println("\n  🏃 Charlie: Trying to select seats...");
                system.selectSeats("SHOW-1", contestedSeats, charlie.getId());
                System.out.println("  🟢 Charlie: Seats LOCKED! Proceeding to payment...");

                // Simulate slight delay before payment
                Thread.sleep(100);

                Booking charlieBooking = system.confirmBooking("SHOW-1", contestedSeats, charlie);
                System.out.println("  🎉 Charlie: BOOKING SUCCESSFUL! " + charlieBooking);
            } catch (SeatTemporarilyLockedException e) {
                System.out.println("  🔴 Charlie: FAILED — " + e.getMessage());
            } catch (SeatNotAvailableException e) {
                System.out.println("  🔴 Charlie: FAILED — " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  🔴 Charlie: ERROR — " + e.getMessage());
            } finally {
                finish.countDown();
            }
        });

        // Fire! Both threads start simultaneously
        startGun.countDown();
        finish.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // ── Final State ──
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  Final Seat Status                                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        Show show = system.getShowService().getShow("SHOW-1");
        System.out.println("\nAll seats for " + show.getMovie().getTitle() + ":");
        for (ShowSeat ss : show.getAllShowSeats()) {
            String icon = ss.isAvailable() ? "🟢" : (ss.isBooked() ? "🔴" : "🟡");
            System.out.println("  " + icon + " " + ss);
        }

        available = system.getAvailableSeats("SHOW-1");
        System.out.println("\n📊 Summary: " + available.size() + " seats available, " +
                (show.getAllShowSeats().size() - available.size()) + " seats booked/locked");

        System.out.println("\n✅ Demo completed successfully!");

        // Cleanup
        MovieTicketBookingSystem.resetInstance();
    }
}