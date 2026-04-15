package org.systemdesign.carrentalsystem.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Store {
    private final String id;
    private String name;
    private String address;
    private String city;
    private final List<Vehicle> vehicles;
    private final List<Reservation> reservations;

    public Store(String name, String address, String city) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.address = address;
        this.city = city;
        this.vehicles = new ArrayList<>();
        this.reservations = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public List<Vehicle> getVehicles() { return vehicles; }
    public List<Reservation> getReservations() { return reservations; }

    public void addVehicle(Vehicle vehicle) {
        this.vehicles.add(vehicle);
    }

    public boolean removeVehicle(Vehicle vehicle) {
        return this.vehicles.remove(vehicle);
    }

    public void addReservation(Reservation reservation) {
        this.reservations.add(reservation);
    }

    @Override
    public String toString() {
        return "Store{id='" + id + "', name='" + name + "', city='" + city +
                "', vehicles=" + vehicles.size() + "}";
    }
}

