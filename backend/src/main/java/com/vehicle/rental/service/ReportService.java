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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 10 admin reports: read-only aggregations over the existing tables.
 * No schema, status-semantic or business-logic change — every figure derives
 * from persisted rows only. Revenue comes strictly from SUCCESS payments,
 * never rentals.total_amount. Fixed response shapes: statuses missing from
 * the grouped queries stay at 0 and NULL aggregates map to 0 / 0.00.
 */
@Service
public class ReportService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalRepository rentalRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceRepository maintenanceRepository;

    public ReportService(UserRepository userRepository,
                         VehicleRepository vehicleRepository,
                         RentalRepository rentalRepository,
                         PaymentRepository paymentRepository,
                         MaintenanceRepository maintenanceRepository) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.rentalRepository = rentalRepository;
        this.paymentRepository = paymentRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @Transactional(readOnly = true)
    public SummaryReportResponse getSummaryReport() {
        SummaryReportResponse response = new SummaryReportResponse();
        response.setTotalCustomers(userRepository.countByRole(UserRole.CUSTOMER));
        response.setTotalVehicles(vehicleRepository.count());
        response.setTotalRentals(rentalRepository.count());

        long totalPayments = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Object[] row : paymentRepository.findPaymentStatusCountsAndSums()) {
            totalPayments += (Long) row[1];
            if (row[0] == PaymentStatus.SUCCESS) {
                totalRevenue = (BigDecimal) row[2];
            }
        }
        response.setTotalPayments(totalPayments);
        response.setTotalRevenue(money(totalRevenue));

        BigDecimal maintenanceCost = BigDecimal.ZERO;
        for (Object[] row : maintenanceRepository.findMaintenanceStatusCountsAndSums()) {
            maintenanceCost = maintenanceCost.add(orZero((BigDecimal) row[2]));
        }
        response.setTotalMaintenanceCost(money(maintenanceCost));
        return response;
    }

    @Transactional(readOnly = true)
    public RevenueReportResponse getRevenueReport() {
        RevenueReportResponse response = new RevenueReportResponse();
        response.setTotalRevenue(money(BigDecimal.ZERO));
        for (Object[] row : paymentRepository.findPaymentStatusCountsAndSums()) {
            long count = (Long) row[1];
            response.setTotalTransactions(response.getTotalTransactions() + count);
            switch ((PaymentStatus) row[0]) {
                case SUCCESS -> {
                    response.setSuccessCount(count);
                    response.setTotalRevenue(money((BigDecimal) row[2]));
                }
                case PENDING -> response.setPendingCount(count);
                case FAILED -> response.setFailedCount(count);
                case CANCELLED -> response.setCancelledCount(count);
                case REFUNDED -> response.setRefundedCount(count);
            }
        }
        return response;
    }

    @Transactional(readOnly = true)
    public RentalReportResponse getRentalsReport() {
        RentalReportResponse response = new RentalReportResponse();
        long total = 0;
        for (Object[] row : rentalRepository.findRentalStatusCounts()) {
            long count = (Long) row[1];
            total += count;
            switch ((RentalStatus) row[0]) {
                case PENDING -> response.setPendingCount(count);
                case CONFIRMED -> response.setConfirmedCount(count);
                case ACTIVE -> response.setActiveCount(count);
                case COMPLETED -> response.setCompletedCount(count);
                case CANCELLED -> response.setCancelledCount(count);
            }
        }
        response.setTotalRentals(total);
        return response;
    }

    @Transactional(readOnly = true)
    public VehicleReportResponse getVehiclesReport() {
        VehicleReportResponse response = new VehicleReportResponse();
        long total = 0;
        for (Object[] row : vehicleRepository.findVehicleStatusCounts()) {
            long count = (Long) row[1];
            total += count;
            switch ((VehicleStatus) row[0]) {
                case AVAILABLE -> response.setAvailableCount(count);
                case RENTED -> response.setRentedCount(count);
                case MAINTENANCE -> response.setMaintenanceCount(count);
                case INACTIVE -> response.setInactiveCount(count);
            }
        }
        response.setTotalVehicles(total);

        // Query already filters CANCELLED out, drops zero-rental vehicles
        // (inner join) and orders rentalCount DESC, vehicleId ASC.
        List<PopularVehicleResponse> popular = new ArrayList<>();
        for (Object[] row : vehicleRepository.findPopularVehicles()) {
            PopularVehicleResponse item = new PopularVehicleResponse();
            item.setVehicleId((Long) row[0]);
            item.setBrand((String) row[1]);
            item.setModel((String) row[2]);
            item.setVehicleType(((VehicleType) row[3]).name());
            item.setRentalCount((Long) row[4]);
            popular.add(item);
        }
        response.setPopularVehicles(popular);
        return response;
    }

    @Transactional(readOnly = true)
    public MaintenanceReportResponse getMaintenanceReport() {
        MaintenanceReportResponse response = new MaintenanceReportResponse();
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalRecords = 0;
        for (Object[] row : maintenanceRepository.findMaintenanceStatusCountsAndSums()) {
            long count = (Long) row[1];
            totalRecords += count;
            totalCost = totalCost.add(orZero((BigDecimal) row[2]));
            switch ((MaintenanceStatus) row[0]) {
                case SCHEDULED -> response.setScheduledCount(count);
                case IN_PROGRESS -> response.setInProgressCount(count);
                case COMPLETED -> response.setCompletedCount(count);
                case CANCELLED -> response.setCancelledCount(count);
            }
        }
        response.setTotalRecords(totalRecords);
        response.setTotalCost(money(totalCost));
        response.setAverageCost(totalRecords == 0
                ? money(BigDecimal.ZERO)
                : money(totalCost.divide(BigDecimal.valueOf(totalRecords), 2, RoundingMode.HALF_UP)));
        return response;
    }

    /** NULL aggregates map to 0.00; persisted DECIMAL(10,2) sums keep their scale. */
    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
