package org.systemdesign.movieticketbooking.service;

import org.systemdesign.movieticketbooking.model.Movie;
import org.systemdesign.movieticketbooking.model.Show;
import org.systemdesign.movieticketbooking.model.ShowSeat;
import org.systemdesign.movieticketbooking.model.enums.City;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ShowService {
    private final Map<String, Show> shows = new ConcurrentHashMap<>();

    public void addShow(Show show) {
        shows.put(show.getId(), show);
    }

    public Show getShow(String showId) {
        return shows.get(showId);
    }

    /**
     * Get all shows for a given movie (across all theatres).
     */
    public List<Show> getShowsForMovie(String movieId) {
        return shows.values().stream()
                .filter(s -> s.getMovie().getId().equals(movieId))
                .collect(Collectors.toList());
    }

    /**
     * Get all shows for a given movie in a specific city.
     */
    public List<Show> getShowsForMovieInCity(String movieId, City city, TheatreService theatreService) {
        return shows.values().stream()
                .filter(s -> s.getMovie().getId().equals(movieId))
                .filter(s -> {
                    // Check if the show's screen belongs to a theatre in the given city
                    return theatreService.getTheatresByCity(city).stream()
                            .anyMatch(t -> t.getScreens().stream()
                                    .anyMatch(screen -> screen.getId().equals(s.getScreen().getId())));
                })
                .collect(Collectors.toList());
    }

    /**
     * Get available seats for a show — safe for concurrent reads.
     */
    public List<ShowSeat> getAvailableSeats(String showId) {
        Show show = shows.get(showId);
        if (show == null) {
            throw new IllegalArgumentException("Show not found: " + showId);
        }
        return show.getAvailableSeats();
    }

    public List<Show> getAllShows() {
        return new ArrayList<>(shows.values());
    }
}

