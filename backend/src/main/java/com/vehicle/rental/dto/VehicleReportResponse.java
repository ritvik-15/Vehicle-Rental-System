package com.vehicle.rental.dto;

import java.util.List;

/**
 * Phase 10 vehicles report: current stored vehicle-status snapshot (no
 * date-based availability — Phase 6 is untouched) plus popularVehicles
 * ordered by rentalCount DESC then vehicleId ASC.
 */
public class VehicleReportResponse {

    private long totalVehicles;
    private long availableCount;
    private long rentedCount;
    private long maintenanceCount;
    private long inactiveCount;
    private List<PopularVehicleResponse> popularVehicles = List.of();

    public long getTotalVehicles() { return totalVehicles; }
    public void setTotalVehicles(long totalVehicles) { this.totalVehicles = totalVehicles; }
    public long getAvailableCount() { return availableCount; }
    public void setAvailableCount(long availableCount) { this.availableCount = availableCount; }
    public long getRentedCount() { return rentedCount; }
    public void setRentedCount(long rentedCount) { this.rentedCount = rentedCount; }
    public long getMaintenanceCount() { return maintenanceCount; }
    public void setMaintenanceCount(long maintenanceCount) { this.maintenanceCount = maintenanceCount; }
    public long getInactiveCount() { return inactiveCount; }
    public void setInactiveCount(long inactiveCount) { this.inactiveCount = inactiveCount; }
    public List<PopularVehicleResponse> getPopularVehicles() { return popularVehicles; }
    public void setPopularVehicles(List<PopularVehicleResponse> popularVehicles) { this.popularVehicles = popularVehicles; }
}
