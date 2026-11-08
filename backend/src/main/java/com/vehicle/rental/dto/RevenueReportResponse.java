package com.vehicle.rental.dto;

import java.math.BigDecimal;

/**
 * Phase 10 revenue report: payment-derived income and per-status payment
 * counts. REFUNDED is reported as the existing stored status only — no
 * refund capability exists or is implied. NULL sums map to 0.00.
 */
public class RevenueReportResponse {

    private BigDecimal totalRevenue;
    private long successCount;
    private long pendingCount;
    private long failedCount;
    private long cancelledCount;
    private long refundedCount;
    private long totalTransactions;

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }
    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }
    public long getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(long cancelledCount) { this.cancelledCount = cancelledCount; }
    public long getRefundedCount() { return refundedCount; }
    public void setRefundedCount(long refundedCount) { this.refundedCount = refundedCount; }
    public long getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(long totalTransactions) { this.totalTransactions = totalTransactions; }
}
