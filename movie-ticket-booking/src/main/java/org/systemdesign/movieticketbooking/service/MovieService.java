package org.systemdesign.movieticketbooking.service;

import org.systemdesign.movieticketbooking.model.Movie;
import org.systemdesign.movieticketbooking.model.enums.City;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages movies in the system.
 * In production, this would be backed by a database.
 */
public class MovieService {
    private final Map<String, Movie> movies = new ConcurrentHashMap<>();
    // movieId → list of cities where it's playing
    private final Map<String, List<City>> movieCityMapping = new ConcurrentHashMap<>();

    public void addMovie(Movie movie, List<City> cities) {
        movies.put(movie.getId(), movie);
        movieCityMapping.put(movie.getId(), new ArrayList<>(cities));
    }

    public Movie getMovie(String movieId) {
        return movies.get(movieId);
    }

    public List<Movie> getMoviesByCity(City city) {
        return movies.values().stream()
                .filter(m -> {
                    List<City> cities = movieCityMapping.get(m.getId());
                    return cities != null && cities.contains(city);
                })
                .collect(Collectors.toList());
    }

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }
}

