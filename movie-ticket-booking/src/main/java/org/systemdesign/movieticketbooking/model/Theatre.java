package org.systemdesign.movieticketbooking.model;

import org.systemdesign.movieticketbooking.model.enums.City;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Theatre {
    private final String id;
    private final String name;
    private final City city;
    private final List<Screen> screens;

    public Theatre(String id, String name, City city) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.screens = new ArrayList<>();
    }

    public void addScreen(Screen screen) {
        screens.add(screen);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public City getCity() { return city; }
    public List<Screen> getScreens() { return Collections.unmodifiableList(screens); }

    @Override
    public String toString() {
        return "Theatre{name='" + name + "', city=" + city + ", screens=" + screens.size() + "}";
    }
}

