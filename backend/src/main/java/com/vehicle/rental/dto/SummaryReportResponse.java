package com.vehicle.rental.dto;

import java.math.BigDecimal;

/**
 * Phase 10 summary report: headline counts across existing tables.
 * totalRevenue comes strictly from SUCCESS payments (never rental totals);
 * NULL aggregates are mapped to 0 / 0.00 by the service.
 */
public class SummaryReportResponse {

    private long totalCustomers;
    private long totalVehicles;
    private long totalRentals;
    private long totalPayments;
    private BigDecimal totalRevenue;
    private BigDecimal totalMaintenanceCost;

    public long getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(long totalCustomers) { this.totalCustomers = totalCustomers; }
    public long getTotalVehicles() { return totalVehicles; }
    public void setTotalVehicles(long totalVehicles) { this.totalVehicles = totalVehicles; }
    public long getTotalRentals() { return totalRentals; }
    public void setTotalRentals(long totalRentals) { this.totalRentals = totalRentals; }
    public long getTotalPayments() { return totalPayments; }
    public void setTotalPayments(long totalPayments) { this.totalPayments = totalPayments; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public BigDecimal getTotalMaintenanceCost() { return totalMaintenanceCost; }
    public void setTotalMaintenanceCost(BigDecimal totalMaintenanceCost) { this.totalMaintenanceCost = totalMaintenanceCost; }
}
