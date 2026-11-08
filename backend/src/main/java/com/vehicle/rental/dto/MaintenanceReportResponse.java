package com.vehicle.rental.dto;

import java.math.BigDecimal;

/**
 * Phase 10 maintenance report: total plus a count for every existing
 * MaintenanceStatus, with cost totals. NULL aggregates map to 0 / 0.00;
 * existing zero-cost records are included in totalRecords/totalCost/averageCost.
 */
public class MaintenanceReportResponse {

    private long totalRecords;
    private long scheduledCount;
    private long inProgressCount;
    private long completedCount;
    private long cancelledCount;
    private BigDecimal totalCost;
    private BigDecimal averageCost;

    public long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }
    public long getScheduledCount() { return scheduledCount; }
    public void setScheduledCount(long scheduledCount) { this.scheduledCount = scheduledCount; }
    public long getInProgressCount() { return inProgressCount; }
    public void setInProgressCount(long inProgressCount) { this.inProgressCount = inProgressCount; }
    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }
    public long getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(long cancelledCount) { this.cancelledCount = cancelledCount; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getAverageCost() { return averageCost; }
    public void setAverageCost(BigDecimal averageCost) { this.averageCost = averageCost; }
}
