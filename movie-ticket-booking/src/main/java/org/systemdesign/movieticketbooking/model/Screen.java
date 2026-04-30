package org.systemdesign.movieticketbooking.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a screen/auditorium in a theatre.
 * Has a fixed set of physical seats.
 */
public class Screen {
    private final String id;
    private final String name;
    private final List<Seat> seats;

    public Screen(String id, String name) {
        this.id = id;
        this.name = name;
        this.seats = new ArrayList<>();
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<Seat> getSeats() { return Collections.unmodifiableList(seats); }

    @Override
    public String toString() {
        return "Screen{name='" + name + "', totalSeats=" + seats.size() + "}";
    }
}

