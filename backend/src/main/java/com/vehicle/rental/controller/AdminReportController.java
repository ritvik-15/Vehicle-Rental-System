package com.vehicle.rental.controller;

import com.vehicle.rental.dto.MaintenanceReportResponse;
import com.vehicle.rental.dto.RentalReportResponse;
import com.vehicle.rental.dto.RevenueReportResponse;
import com.vehicle.rental.dto.SummaryReportResponse;
import com.vehicle.rental.dto.VehicleReportResponse;
import com.vehicle.rental.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 10 admin reports — five read-only GET aggregations over existing
 * tables. No request bodies, parameters, pagination or filters. Secured by
 * the existing /api/admin/** -> hasRole("ADMIN") rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public SummaryReportResponse getSummary() {
        return reportService.getSummaryReport();
    }

    @GetMapping("/revenue")
    public RevenueReportResponse getRevenue() {
        return reportService.getRevenueReport();
    }

    @GetMapping("/rentals")
    public RentalReportResponse getRentals() {
        return reportService.getRentalsReport();
    }

    @GetMapping("/vehicles")
    public VehicleReportResponse getVehicles() {
        return reportService.getVehiclesReport();
    }

    @GetMapping("/maintenance")
    public MaintenanceReportResponse getMaintenance() {
        return reportService.getMaintenanceReport();
    }
}
