package org.systemdesign.carrentalsystem.model;

import org.systemdesign.carrentalsystem.enums.VehicleStatus;

import java.util.UUID;

public class Vehicle {
    private final String id;
    private String licensePlate;
    private String brand;
    private String model;
    private int year;
    private VehicleType vehicleType;
    private VehicleStatus status;
    private double pricePerHour;

    public Vehicle(String licensePlate, String brand, String model, int year,
                   VehicleType vehicleType, double pricePerHour) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.licensePlate = licensePlate;
        this.brand = brand;
        this.model = model;
        this.year = year;
        this.vehicleType = vehicleType;
        this.status = VehicleStatus.AVAILABLE;
        this.pricePerHour = pricePerHour;
    }

    public String getId() { return id; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public double getPricePerHour() { return pricePerHour; }
    public void setPricePerHour(double pricePerHour) { this.pricePerHour = pricePerHour; }

    @Override
    public String toString() {
        return "Vehicle{id='" + id + "', " + brand + " " + model +
                " (" + licensePlate + "), type=" + vehicleType.getCategory() +
                ", status=" + status + ", ₹" + pricePerHour + "/hr}";
    }
}

