package com.vehicle.rental.dto;

/**
 * Phase 10 popular-vehicle entry: current stored vehicle identity plus a
 * rentalCount over PENDING/CONFIRMED/ACTIVE/COMPLETED rentals only
 * (CANCELLED excluded). Only vehicles with rentalCount &gt; 0 appear.
 */
public class PopularVehicleResponse {

    private Long vehicleId;
    private String brand;
    private String model;
    private String vehicleType;
    private long rentalCount;

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public long getRentalCount() { return rentalCount; }
    public void setRentalCount(long rentalCount) { this.rentalCount = rentalCount; }
}
