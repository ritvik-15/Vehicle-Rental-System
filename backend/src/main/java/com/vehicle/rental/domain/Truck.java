package com.vehicle.rental.domain;

import java.math.BigDecimal;

public class Truck extends Vehicle {

    private double loadCapacity;
    private int numberOfAxles;
    private String cargoType;

    public Truck(String brand, String model, BigDecimal dailyRate, int seatingCapacity,
                 double loadCapacity, int numberOfAxles, String cargoType) {
        super(brand, model, dailyRate, seatingCapacity);
        this.loadCapacity = loadCapacity;
        this.numberOfAxles = numberOfAxles;
        this.cargoType = cargoType;
    }

    @Override
    public String getVehicleCategory() {
        return "Truck";
    }

    @Override
    public String getRentalPolicy() {
        return "Truck rental - commercial use, " + loadCapacity + " ton capacity";
    }

    public double getLoadCapacity() { return loadCapacity; }
    public int getNumberOfAxles() { return numberOfAxles; }
    public String getCargoType() { return cargoType; }
}
