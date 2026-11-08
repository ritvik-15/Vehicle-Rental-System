package com.vehicle.rental.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MaintenanceCreateRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @jakarta.validation.constraints.NotBlank(message = "Maintenance type is required")
    @Size(max = 100, message = "Maintenance type must not exceed 100 characters")
    private String maintenanceType;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Maintenance date is required")
    private LocalDate maintenanceDate;

    private LocalDate completionDate;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0.00", message = "Cost must be 0.00 or greater")
    private BigDecimal cost;

    @Size(max = 150, message = "Service provider must not exceed 150 characters")
    private String serviceProvider;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getMaintenanceType() { return maintenanceType; }
    public void setMaintenanceType(String maintenanceType) { this.maintenanceType = maintenanceType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getMaintenanceDate() { return maintenanceDate; }
    public void setMaintenanceDate(LocalDate maintenanceDate) { this.maintenanceDate = maintenanceDate; }
    public LocalDate getCompletionDate() { return completionDate; }
    public void setCompletionDate(LocalDate completionDate) { this.completionDate = completionDate; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public String getServiceProvider() { return serviceProvider; }
    public void setServiceProvider(String serviceProvider) { this.serviceProvider = serviceProvider; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
