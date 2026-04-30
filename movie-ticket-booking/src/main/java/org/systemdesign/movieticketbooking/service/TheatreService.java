package org.systemdesign.movieticketbooking.service;

import org.systemdesign.movieticketbooking.model.Theatre;
import org.systemdesign.movieticketbooking.model.enums.City;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TheatreService {
    private final Map<String, Theatre> theatres = new ConcurrentHashMap<>();

    public void addTheatre(Theatre theatre) {
        theatres.put(theatre.getId(), theatre);
    }

    public Theatre getTheatre(String theatreId) {
        return theatres.get(theatreId);
    }

    public List<Theatre> getTheatresByCity(City city) {
        return theatres.values().stream()
                .filter(t -> t.getCity() == city)
                .collect(Collectors.toList());
    }

    public List<Theatre> getAllTheatres() {
        return new ArrayList<>(theatres.values());
    }
}

