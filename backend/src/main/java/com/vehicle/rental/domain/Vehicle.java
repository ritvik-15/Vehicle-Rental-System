package com.vehicle.rental.domain;

import java.math.BigDecimal;

public abstract class Vehicle {

    private String brand;
    private String model;
    private BigDecimal dailyRate;
    private int seatingCapacity;

    public Vehicle(String brand, String model, BigDecimal dailyRate, int seatingCapacity) {
        this.brand = brand;
        this.model = model;
        this.dailyRate = dailyRate;
        this.seatingCapacity = seatingCapacity;
    }

    public abstract String getVehicleCategory();

    public abstract String getRentalPolicy();

    public BigDecimal calculateRentalCost(int days) {
        return dailyRate.multiply(BigDecimal.valueOf(days));
    }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    public int getSeatingCapacity() { return seatingCapacity; }
    public void setSeatingCapacity(int seatingCapacity) { this.seatingCapacity = seatingCapacity; }
}
