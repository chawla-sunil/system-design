package org.systemdesign.carrentalsystem.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class User {
    private final String id;
    private String name;
    private String email;
    private String phone;
    private String drivingLicenseNumber;
    private final List<Reservation> reservations;

    public User(String name, String email, String phone, String drivingLicenseNumber) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.drivingLicenseNumber = drivingLicenseNumber;
        this.reservations = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDrivingLicenseNumber() { return drivingLicenseNumber; }
    public void setDrivingLicenseNumber(String drivingLicenseNumber) { this.drivingLicenseNumber = drivingLicenseNumber; }
    public List<Reservation> getReservations() { return reservations; }

    public void addReservation(Reservation reservation) {
        this.reservations.add(reservation);
    }

    @Override
    public String toString() {
        return "User{id='" + id + "', name='" + name + "', email='" + email + "'}";
    }
}

