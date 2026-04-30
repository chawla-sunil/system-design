package org.systemdesign.movieticketbooking.controller;

import org.systemdesign.movieticketbooking.model.*;
import org.systemdesign.movieticketbooking.model.enums.City;
import org.systemdesign.movieticketbooking.observer.BookingObserver;
import org.systemdesign.movieticketbooking.service.*;
import org.systemdesign.movieticketbooking.strategy.PricingStrategy;

import java.util.List;

/**
 * Singleton Facade — single entry point for the entire Movie Ticket Booking system.
 *
 * In production, this would be replaced by REST controllers + Spring DI.
 * Here it serves as the "API layer" for the LLD demo.
 */
public class MovieTicketBookingSystem {

    private static volatile MovieTicketBookingSystem instance;

    private final MovieService movieService;
    private final TheatreService theatreService;
    private final ShowService showService;
    private final BookingService bookingService;
    private final SeatLockService seatLockService;
    private final PaymentService paymentService;

    private MovieTicketBookingSystem(PricingStrategy pricingStrategy) {
        this.movieService = new MovieService();
        this.theatreService = new TheatreService();
        this.showService = new ShowService();
        this.paymentService = new PaymentService();
        this.seatLockService = new SeatLockService(showService);
        this.bookingService = new BookingService(
                showService, seatLockService, paymentService, pricingStrategy
        );
    }

    public static MovieTicketBookingSystem getInstance(PricingStrategy pricingStrategy) {
        if (instance == null) {
            synchronized (MovieTicketBookingSystem.class) {
                if (instance == null) {
                    instance = new MovieTicketBookingSystem(pricingStrategy);
                }
            }
        }
        return instance;
    }

    /**
     * Reset singleton (for testing purposes only).
     */
    public static void resetInstance() {
        synchronized (MovieTicketBookingSystem.class) {
            if (instance != null) {
                instance.seatLockService.shutdown();
                instance = null;
            }
        }
    }

    // ---- Movie Operations ----

    public void addMovie(Movie movie, List<City> cities) {
        movieService.addMovie(movie, cities);
    }

    public List<Movie> getMoviesByCity(City city) {
        return movieService.getMoviesByCity(city);
    }

    // ---- Theatre Operations ----

    public void addTheatre(Theatre theatre) {
        theatreService.addTheatre(theatre);
    }

    public List<Theatre> getTheatresByCity(City city) {
        return theatreService.getTheatresByCity(city);
    }

    // ---- Show Operations ----

    public void addShow(Show show) {
        showService.addShow(show);
    }

    public List<Show> getShowsForMovie(String movieId) {
        return showService.getShowsForMovie(movieId);
    }

    public List<ShowSeat> getAvailableSeats(String showId) {
        return showService.getAvailableSeats(showId);
    }

    // ---- Booking Operations (THE core flow) ----

    /**
     * Step 1: User selects seats → temporarily lock them.
     */
    public List<ShowSeat> selectSeats(String showId, List<String> seatIds, String userId) {
        return bookingService.initiateBooking(showId, seatIds, userId);
    }

    /**
     * Step 2: User pays → finalize booking.
     */
    public Booking confirmBooking(String showId, List<String> seatIds, User user) {
        return bookingService.confirmBooking(showId, seatIds, user);
    }

    /**
     * Cancel an existing booking.
     */
    public void cancelBooking(String bookingId) {
        bookingService.cancelBooking(bookingId);
    }

    // ---- Observer Registration ----

    public void addBookingObserver(BookingObserver observer) {
        bookingService.addObserver(observer);
    }

    // ---- Getters for services (if needed for advanced operations) ----

    public MovieService getMovieService() { return movieService; }
    public TheatreService getTheatreService() { return theatreService; }
    public ShowService getShowService() { return showService; }
    public BookingService getBookingService() { return bookingService; }
    public SeatLockService getSeatLockService() { return seatLockService; }
}

