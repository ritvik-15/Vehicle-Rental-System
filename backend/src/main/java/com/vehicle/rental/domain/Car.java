package com.vehicle.rental.domain;

import java.math.BigDecimal;

public class Car extends Vehicle {

    private int numberOfDoors;
    private boolean hasAC;
    private double trunkCapacity;

    public Car(String brand, String model, BigDecimal dailyRate, int seatingCapacity,
               int numberOfDoors, boolean hasAC, double trunkCapacity) {
        super(brand, model, dailyRate, seatingCapacity);
        this.numberOfDoors = numberOfDoors;
        this.hasAC = hasAC;
        this.trunkCapacity = trunkCapacity;
    }

    @Override
    public String getVehicleCategory() {
        return "Car";
    }

    @Override
    public String getRentalPolicy() {
        return "Standard car rental - AC available, " + numberOfDoors + " doors";
    }

    public boolean hasAC() { return hasAC; }
    public int getNumberOfDoors() { return numberOfDoors; }
    public double getTrunkCapacity() { return trunkCapacity; }
}
