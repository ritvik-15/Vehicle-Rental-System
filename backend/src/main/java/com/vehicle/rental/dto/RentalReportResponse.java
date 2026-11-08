package com.vehicle.rental.dto;

/**
 * Phase 10 rentals report: total plus a count for every existing
 * RentalStatus. The response shape is fixed — statuses with zero rows
 * always appear with count 0.
 */
public class RentalReportResponse {

    private long totalRentals;
    private long pendingCount;
    private long confirmedCount;
    private long activeCount;
    private long completedCount;
    private long cancelledCount;

    public long getTotalRentals() { return totalRentals; }
    public void setTotalRentals(long totalRentals) { this.totalRentals = totalRentals; }
    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }
    public long getConfirmedCount() { return confirmedCount; }
    public void setConfirmedCount(long confirmedCount) { this.confirmedCount = confirmedCount; }
    public long getActiveCount() { return activeCount; }
    public void setActiveCount(long activeCount) { this.activeCount = activeCount; }
    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }
    public long getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(long cancelledCount) { this.cancelledCount = cancelledCount; }
}
