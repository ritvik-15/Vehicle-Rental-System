package com.vehicle.rental.service;

import com.vehicle.rental.dto.MaintenanceReportResponse;
import com.vehicle.rental.dto.PopularVehicleResponse;
import com.vehicle.rental.dto.RentalReportResponse;
import com.vehicle.rental.dto.RevenueReportResponse;
import com.vehicle.rental.dto.SummaryReportResponse;
import com.vehicle.rental.dto.VehicleReportResponse;
import com.vehicle.rental.enums.MaintenanceStatus;
import com.vehicle.rental.enums.PaymentStatus;
import com.vehicle.rental.enums.RentalStatus;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.repository.MaintenanceRepository;
import com.vehicle.rental.repository.PaymentRepository;
import com.vehicle.rental.repository.RentalRepository;
import com.vehicle.rental.repository.UserRepository;
import com.vehicle.rental.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Phase 10 unit tests: fixed zero-default shapes for missing status groups,
 * SUCCESS-only revenue (never rental totals), CUSTOMER-only customer count,
 * popularity calculation/exclusion/ordering, and NULL aggregates mapping to
 * 0 / 0.00 including zero-cost maintenance records.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RentalRepository rentalRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MaintenanceRepository maintenanceRepository;

    @InjectMocks
    private ReportService reportService;

    private Object[] paymentRow(PaymentStatus status, long count, String sum) {
        return new Object[]{status, count, new BigDecimal(sum)};
    }

    private Object[] rentalRow(RentalStatus status, long count) {
        return new Object[]{status, count};
    }

    private Object[] vehicleRow(VehicleStatus status, long count) {
        return new Object[]{status, count};
    }

    private Object[] maintenanceRow(MaintenanceStatus status, long count, String sum) {
        return new Object[]{status, count, new BigDecimal(sum)};
    }

    // ------------------------------------------------------------------
    // Summary
    // ------------------------------------------------------------------

    @Test
    void summaryCountsOnlyCustomersAndDerivesRevenueFromSuccessPayments() {
        when(userRepository.countByRole(UserRole.CUSTOMER)).thenReturn(2L);
        when(vehicleRepository.count()).thenReturn(4L);
        when(rentalRepository.count()).thenReturn(7L);
        when(paymentRepository.findPaymentStatusCountsAndSums()).thenReturn(List.of(
                paymentRow(PaymentStatus.SUCCESS, 3, "10000.00"),
                paymentRow(PaymentStatus.PENDING, 2, "1500.00"),
                paymentRow(PaymentStatus.FAILED, 1, "500.00")));
        when(maintenanceRepository.findMaintenanceStatusCountsAndSums()).thenReturn(List.of(
                maintenanceRow(MaintenanceStatus.COMPLETED, 2, "250.00"),
                maintenanceRow(MaintenanceStatus.SCHEDULED, 1, "50.00")));

        SummaryReportResponse report = reportService.getSummaryReport();

        assertEquals(2, report.getTotalCustomers());
        assertEquals(4, report.getTotalVehicles());
        assertEquals(7, report.getTotalRentals());
        assertEquals(6, report.getTotalPayments());
        // Revenue equals the SUCCESS sum only — rental count (7) must not leak in.
        assertEquals(new BigDecimal("10000.00"), report.getTotalRevenue());
        assertEquals(new BigDecimal("300.00"), report.getTotalMaintenanceCost());
    }

    @Test
    void summaryWithNoDataReturnsZeroCountsAndZeroMoney() {
        when(userRepository.countByRole(UserRole.CUSTOMER)).thenReturn(0L);
        when(vehicleRepository.count()).thenReturn(0L);
        when(rentalRepository.count()).thenReturn(0L);
        when(paymentRepository.findPaymentStatusCountsAndSums()).thenReturn(List.of());
        when(maintenanceRepository.findMaintenanceStatusCountsAndSums()).thenReturn(List.of());

        SummaryReportResponse report = reportService.getSummaryReport();

        assertEquals(0, report.getTotalCustomers());
        assertEquals(0, report.getTotalVehicles());
        assertEquals(0, report.getTotalRentals());
        assertEquals(0, report.getTotalPayments());
        assertEquals(new BigDecimal("0.00"), report.getTotalRevenue());
        assertEquals(new BigDecimal("0.00"), report.getTotalMaintenanceCost());
    }

    // ------------------------------------------------------------------
    // Revenue
    // ------------------------------------------------------------------

    @Test
    void revenueSumsSuccessAmountOnlyAcrossAllPaymentStatuses() {
        when(paymentRepository.findPaymentStatusCountsAndSums()).thenReturn(List.of(
                paymentRow(PaymentStatus.SUCCESS, 2, "10000.00"),
                paymentRow(PaymentStatus.PENDING, 3, "4500.00"),
                paymentRow(PaymentStatus.FAILED, 1, "500.00"),
                paymentRow(PaymentStatus.CANCELLED, 1, "700.00"),
                paymentRow(PaymentStatus.REFUNDED, 1, "300.00")));

        RevenueReportResponse report = reportService.getRevenueReport();

        assertEquals(new BigDecimal("10000.00"), report.getTotalRevenue());
        assertEquals(2, report.getSuccessCount());
        assertEquals(3, report.getPendingCount());
        assertEquals(1, report.getFailedCount());
        assertEquals(1, report.getCancelledCount());
        assertEquals(1, report.getRefundedCount());
        assertEquals(8, report.getTotalTransactions());
    }

    @Test
    void revenueWithNoPaymentsReturnsAllZeroShape() {
        when(paymentRepository.findPaymentStatusCountsAndSums()).thenReturn(List.of());

        RevenueReportResponse report = reportService.getRevenueReport();

        assertEquals(new BigDecimal("0.00"), report.getTotalRevenue());
        assertEquals(0, report.getSuccessCount());
        assertEquals(0, report.getPendingCount());
        assertEquals(0, report.getFailedCount());
        assertEquals(0, report.getCancelledCount());
        assertEquals(0, report.getRefundedCount());
        assertEquals(0, report.getTotalTransactions());
    }

    // ------------------------------------------------------------------
    // Rentals
    // ------------------------------------------------------------------

    @Test
    void rentalsReportZeroDefaultsForStatusesWithoutRows() {
        when(rentalRepository.findRentalStatusCounts()).thenReturn(List.of(
                rentalRow(RentalStatus.PENDING, 3),
                rentalRow(RentalStatus.COMPLETED, 2)));

        RentalReportResponse report = reportService.getRentalsReport();

        assertEquals(5, report.getTotalRentals());
        assertEquals(3, report.getPendingCount());
        assertEquals(2, report.getCompletedCount());
        assertEquals(0, report.getConfirmedCount());
        assertEquals(0, report.getActiveCount());
        assertEquals(0, report.getCancelledCount());
    }

    @Test
    void rentalsReportWithNoRentalsReturnsZeroShape() {
        when(rentalRepository.findRentalStatusCounts()).thenReturn(List.of());

        RentalReportResponse report = reportService.getRentalsReport();

        assertEquals(0, report.getTotalRentals());
        assertEquals(0, report.getPendingCount());
        assertEquals(0, report.getConfirmedCount());
        assertEquals(0, report.getActiveCount());
        assertEquals(0, report.getCompletedCount());
        assertEquals(0, report.getCancelledCount());
    }

    // ------------------------------------------------------------------
    // Vehicles
    // ------------------------------------------------------------------

    @Test
    void vehiclesReportMapsStatusSnapshotAndPopularOrderPreserved() {
        when(vehicleRepository.findVehicleStatusCounts()).thenReturn(List.of(
                vehicleRow(VehicleStatus.AVAILABLE, 2),
                vehicleRow(VehicleStatus.INACTIVE, 1)));
        when(vehicleRepository.findPopularVehicles()).thenReturn(List.of(
                new Object[]{10L, "Toyota", "Etios", VehicleType.CAR, 5L},
                new Object[]{20L, "Honda", "Activa", VehicleType.BIKE, 2L}));

        VehicleReportResponse report = reportService.getVehiclesReport();

        assertEquals(3, report.getTotalVehicles());
        assertEquals(2, report.getAvailableCount());
        assertEquals(1, report.getInactiveCount());
        assertEquals(0, report.getRentedCount());
        assertEquals(0, report.getMaintenanceCount());
        assertEquals(2, report.getPopularVehicles().size());
        assertEquals(10L, report.getPopularVehicles().get(0).getVehicleId());
        assertEquals("Toyota", report.getPopularVehicles().get(0).getBrand());
        assertEquals("CAR", report.getPopularVehicles().get(0).getVehicleType());
        assertEquals(5, report.getPopularVehicles().get(0).getRentalCount());
        assertEquals(20L, report.getPopularVehicles().get(1).getVehicleId());
        assertEquals("BIKE", report.getPopularVehicles().get(1).getVehicleType());
        assertEquals(2, report.getPopularVehicles().get(1).getRentalCount());
    }

    @Test
    void vehiclesReportWithNoVehiclesReturnsZeroShapeAndEmptyPopularList() {
        when(vehicleRepository.findVehicleStatusCounts()).thenReturn(List.of());
        when(vehicleRepository.findPopularVehicles()).thenReturn(List.of());

        VehicleReportResponse report = reportService.getVehiclesReport();

        assertEquals(0, report.getTotalVehicles());
        assertEquals(0, report.getAvailableCount());
        assertEquals(0, report.getRentedCount());
        assertEquals(0, report.getMaintenanceCount());
        assertEquals(0, report.getInactiveCount());
        assertNotNull(report.getPopularVehicles());
        assertTrue(report.getPopularVehicles().isEmpty());
    }

    // ------------------------------------------------------------------
    // Maintenance
    // ------------------------------------------------------------------

    @Test
    void maintenanceReportTotalsAverageAndZeroCostRecordCounted() {
        when(maintenanceRepository.findMaintenanceStatusCountsAndSums()).thenReturn(List.of(
                maintenanceRow(MaintenanceStatus.COMPLETED, 2, "300.00"),
                maintenanceRow(MaintenanceStatus.SCHEDULED, 1, "0.00")));

        MaintenanceReportResponse report = reportService.getMaintenanceReport();

        assertEquals(3, report.getTotalRecords());
        assertEquals(2, report.getCompletedCount());
        assertEquals(1, report.getScheduledCount());
        assertEquals(0, report.getInProgressCount());
        assertEquals(0, report.getCancelledCount());
        assertEquals(new BigDecimal("300.00"), report.getTotalCost());
        assertEquals(new BigDecimal("100.00"), report.getAverageCost());
    }

    @Test
    void maintenanceReportWithNoRecordsReturnsZeroTotalsAndAverage() {
        when(maintenanceRepository.findMaintenanceStatusCountsAndSums()).thenReturn(List.of());

        MaintenanceReportResponse report = reportService.getMaintenanceReport();

        assertEquals(0, report.getTotalRecords());
        assertEquals(0, report.getScheduledCount());
        assertEquals(0, report.getInProgressCount());
        assertEquals(0, report.getCompletedCount());
        assertEquals(0, report.getCancelledCount());
        assertEquals(new BigDecimal("0.00"), report.getTotalCost());
        assertEquals(new BigDecimal("0.00"), report.getAverageCost());
    }
}
