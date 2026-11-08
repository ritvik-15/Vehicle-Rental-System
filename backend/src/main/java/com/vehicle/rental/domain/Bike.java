package com.vehicle.rental.domain;

import java.math.BigDecimal;

public class Bike extends Vehicle {

    private int engineCC;
    private boolean hasCarrier;
    private int helmetCount;

    public Bike(String brand, String model, BigDecimal dailyRate, int seatingCapacity,
                int engineCC, boolean hasCarrier, int helmetCount) {
        super(brand, model, dailyRate, seatingCapacity);
        this.engineCC = engineCC;
        this.hasCarrier = hasCarrier;
        this.helmetCount = helmetCount;
    }

    @Override
    public String getVehicleCategory() {
        return "Bike";
    }

    @Override
    public String getRentalPolicy() {
        return "Bike rental - helmet required, " + engineCC + "CC engine";
    }

    public int getEngineCC() { return engineCC; }
    public boolean hasCarrier() { return hasCarrier; }
    public int getHelmetCount() { return helmetCount; }
}
