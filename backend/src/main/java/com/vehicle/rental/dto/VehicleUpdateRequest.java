package com.vehicle.rental.dto;

import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.VehicleType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class VehicleUpdateRequest {

    @NotBlank(message = "Registration number is required")
    @Size(max = 30, message = "Registration number must not exceed 30 characters")
    private String registrationNumber;

    @NotBlank(message = "Brand is required")
    @Size(max = 80, message = "Brand must not exceed 80 characters")
    private String brand;

    @NotBlank(message = "Model is required")
    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Daily rate is required")
    @DecimalMin(value = "0", message = "Daily rate must be 0 or greater")
    private BigDecimal dailyRate;

    @NotNull(message = "Seating capacity is required")
    @Min(value = 1, message = "Seating capacity must be at least 1")
    private Integer seatingCapacity;

    @NotNull(message = "Fuel type is required")
    private FuelType fuelType;

    @NotNull(message = "Transmission is required")
    private TransmissionType transmission;

    @NotBlank(message = "Location is required")
    @Size(max = 150, message = "Location must not exceed 150 characters")
    private String location;

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    public Integer getSeatingCapacity() { return seatingCapacity; }
    public void setSeatingCapacity(Integer seatingCapacity) { this.seatingCapacity = seatingCapacity; }
    public FuelType getFuelType() { return fuelType; }
    public void setFuelType(FuelType fuelType) { this.fuelType = fuelType; }
    public TransmissionType getTransmission() { return transmission; }
    public void setTransmission(TransmissionType transmission) { this.transmission = transmission; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
