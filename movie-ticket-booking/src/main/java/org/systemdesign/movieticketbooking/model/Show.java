package org.systemdesign.movieticketbooking.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A Show = a Movie playing on a Screen at a specific time.
 *
 * Contains ShowSeats — the per-show seat status map.
 * The Show object itself is used as the synchronization monitor
 * for all seat operations (lock/book/cancel).
 */
public class Show {
    private final String id;
    private final Movie movie;
    private final Screen screen;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    // seatId → ShowSeat — ConcurrentHashMap for safe reads while writes are synchronized(this)
    private final Map<String, ShowSeat> showSeats;

    public Show(String id, Movie movie, Screen screen, LocalDateTime startTime) {
        this.id = id;
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        this.endTime = startTime.plus(movie.getDuration());
        this.showSeats = new ConcurrentHashMap<>();

        // Initialize ShowSeat for every physical seat in the screen
        for (Seat seat : screen.getSeats()) {
            showSeats.put(seat.getId(), new ShowSeat(seat));
        }
    }

    public ShowSeat getShowSeat(String seatId) {
        return showSeats.get(seatId);
    }

    public List<ShowSeat> getAvailableSeats() {
        return showSeats.values().stream()
                .filter(ShowSeat::isAvailable)
                .collect(Collectors.toList());
    }

    public List<ShowSeat> getAllShowSeats() {
        return List.copyOf(showSeats.values());
    }

    public String getId() { return id; }
    public Movie getMovie() { return movie; }
    public Screen getScreen() { return screen; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }

    @Override
    public String toString() {
        long available = showSeats.values().stream().filter(ShowSeat::isAvailable).count();
        return "Show{movie='" + movie.getTitle() + "', screen='" + screen.getName() +
                "', startTime=" + startTime + ", available=" + available + "/" + showSeats.size() + "}";
    }
}

