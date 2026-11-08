package com.vehicle.rental.domain;

import java.math.BigDecimal;

public class Bus extends Vehicle {

    private boolean hasAC;
    private double luggageSpace;

    public Bus(String brand, String model, BigDecimal dailyRate, int seatingCapacity,
               boolean hasAC, double luggageSpace) {
        super(brand, model, dailyRate, seatingCapacity);
        this.hasAC = hasAC;
        this.luggageSpace = luggageSpace;
    }

    @Override
    public String getVehicleCategory() {
        return "Bus";
    }

    @Override
    public String getRentalPolicy() {
        return "Bus rental - group travel, " + getSeatingCapacity() + " seats";
    }

    public boolean hasAC() { return hasAC; }
    public double getLuggageSpace() { return luggageSpace; }
}
