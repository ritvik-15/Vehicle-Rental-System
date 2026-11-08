package com.vehicle.rental.repository;

import com.vehicle.rental.dto.MaintenanceReportResponse;
import com.vehicle.rental.dto.PopularVehicleResponse;
import com.vehicle.rental.dto.RentalReportResponse;
import com.vehicle.rental.dto.RevenueReportResponse;
import com.vehicle.rental.dto.SummaryReportResponse;
import com.vehicle.rental.dto.VehicleReportResponse;
import com.vehicle.rental.entity.Maintenance;
import com.vehicle.rental.entity.Payment;
import com.vehicle.rental.entity.Rental;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.MaintenanceStatus;
import com.vehicle.rental.enums.PaymentStatus;
import com.vehicle.rental.enums.RentalStatus;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10 real-MySQL aggregation verification. Baselines are captured
 * before fixtures are inserted and asserted as exact deltas, so the checks
 * hold regardless of committed state. Class-level @Transactional rolls
 * everything back — no leftover test data.
 */
@SpringBootTest
@Transactional
class AdminReportsIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MaintenanceRepository maintenanceRepository;

    private SummaryReportResponse baseSummary;
    private RevenueReportResponse baseRevenue;
    private RentalReportResponse baseRentals;
    private VehicleReportResponse baseVehicles;
    private MaintenanceReportResponse baseMaintenance;

    @BeforeEach
    void captureBaseline() {
        baseSummary = reportService.getSummaryReport();
        baseRevenue = reportService.getRevenueReport();
        baseRentals = reportService.getRentalsReport();
        baseVehicles = reportService.getVehiclesReport();
        baseMaintenance = reportService.getMaintenanceReport();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private User createUser(String role, String key) {
        User user = new User();
        user.setFullName("Phase 10 User " + key);
        user.setEmail("phase10" + key + "@example.com");
        user.setPhone("9810000" + key);
        user.setPasswordHash("$2a$10$Phase10HashThatMustNeverLeak0000000");
        user.setRole("ADMIN".equals(role) ? UserRole.ADMIN : UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Vehicle createVehicle(String key, VehicleStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("PH10" + key);
        vehicle.setBrand("Brand" + key);
        vehicle.setModel("Model" + key);
        vehicle.setVehicleType(VehicleType.CAR);
        vehicle.setDailyRate(new BigDecimal("1000.00"));
        vehicle.setSeatingCapacity(5);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setTransmission(TransmissionType.MANUAL);
        vehicle.setLocation("Mumbai");
        vehicle.setStatus(status);
        return vehicleRepository.saveAndFlush(vehicle);
    }

    private Rental createRental(User user, Vehicle vehicle, RentalStatus status, String key) {
        Rental rental = new Rental();
        rental.setUser(user);
        rental.setVehicle(vehicle);
        rental.setPickupDatetime(LocalDateTime.now().plusDays(1));
        rental.setReturnDatetime(LocalDateTime.now().plusDays(3));
        rental.setNumberOfDays(2);
        rental.setDailyRate(new BigDecimal("1000.00"));
        rental.setSubtotal(new BigDecimal("2000.00"));
        rental.setDiscountAmount(new BigDecimal("0.00"));
        rental.setTaxAmount(new BigDecimal("360.00"));
        rental.setSecurityDeposit(new BigDecimal("400.00"));
        rental.setTotalAmount(new BigDecimal("2760.00"));
        rental.setRentalStatus(status);
        return rentalRepository.saveAndFlush(rental);
    }

    private Payment createPayment(Rental rental, User user, String amount, PaymentStatus status, String key) {
        Payment payment = new Payment();
        payment.setRental(rental);
        payment.setUser(user);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentStatus(status);
        payment.setRazorpayOrderId("ph10-order-" + key);
        payment.setRazorpayPaymentId("ph10-pay-" + key);
        return paymentRepository.saveAndFlush(payment);
    }

    private Maintenance createMaintenance(Vehicle vehicle, MaintenanceStatus status, String cost, String key) {
        Maintenance maintenance = new Maintenance();
        maintenance.setVehicle(vehicle);
        maintenance.setMaintenanceType("Service " + key);
        maintenance.setMaintenanceDate(LocalDate.now());
        maintenance.setCost(new BigDecimal(cost));
        maintenance.setStatus(status);
        return maintenanceRepository.saveAndFlush(maintenance);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void assertMoneyDelta(BigDecimal after, BigDecimal before, String expected) {
        BigDecimal delta = after.subtract(before);
        assertEquals(0, delta.compareTo(new BigDecimal(expected)),
                "expected delta " + expected + " but was " + delta);
    }

    private PopularVehicleResponse popularById(VehicleReportResponse report, Long vehicleId) {
        return report.getPopularVehicles().stream()
                .filter(item -> item.getVehicleId().equals(vehicleId))
                .findFirst()
                .orElse(null);
    }

    private void assertPopularOrderingInvariant(List<PopularVehicleResponse> list) {
        for (int i = 1; i < list.size(); i++) {
            PopularVehicleResponse prev = list.get(i - 1);
            PopularVehicleResponse curr = list.get(i);
            assertTrue(prev.getRentalCount() > curr.getRentalCount()
                            || (prev.getRentalCount() == curr.getRentalCount()
                            && prev.getVehicleId() < curr.getVehicleId()),
                    "popularVehicles must be rentalCount DESC then vehicleId ASC");
        }
    }

    // ------------------------------------------------------------------
    // Summary
    // ------------------------------------------------------------------

    @Test
    void summaryReportsExactDeltasWithCustomerOnlyCount() {
        assertNotNull(baseSummary.getTotalRevenue());
        assertNotNull(baseSummary.getTotalMaintenanceCost());

        createUser("CUSTOMER", "sumcust1");
        createUser("CUSTOMER", "sumcust2");
        createUser("ADMIN", "sumadm");
        Vehicle vehicle1 = createVehicle("SUM1", VehicleStatus.AVAILABLE);
        createVehicle("SUM2", VehicleStatus.AVAILABLE);
        User customer = userRepository.findByEmail("phase10sumcust1@example.com").orElseThrow();
        createRental(customer, vehicle1, RentalStatus.PENDING, "sumr1");
        createRental(customer, vehicle1, RentalStatus.CONFIRMED, "sumr2");
        createRental(customer, vehicle1, RentalStatus.ACTIVE, "sumr3");
        createPayment(createRental(customer, vehicle1, RentalStatus.COMPLETED, "sumr4"),
                customer, "4000.00", PaymentStatus.SUCCESS, "sum1");
        createPayment(createRental(customer, vehicle1, RentalStatus.CANCELLED, "sumr5"),
                customer, "1500.00", PaymentStatus.PENDING, "sum2");
        createMaintenance(vehicle1, MaintenanceStatus.COMPLETED, "250.00", "sum1");
        createMaintenance(vehicle1, MaintenanceStatus.SCHEDULED, "50.00", "sum2");

        SummaryReportResponse report = reportService.getSummaryReport();

        // 3 users created (2 CUSTOMER + 1 ADMIN) but only customers counted.
        assertEquals(baseSummary.getTotalCustomers() + 2, report.getTotalCustomers());
        assertEquals(baseSummary.getTotalVehicles() + 2, report.getTotalVehicles());
        assertEquals(baseSummary.getTotalRentals() + 5, report.getTotalRentals());
        assertEquals(baseSummary.getTotalPayments() + 2, report.getTotalPayments());
        assertMoneyDelta(report.getTotalRevenue(), baseSummary.getTotalRevenue(), "4000.00");
        assertMoneyDelta(report.getTotalMaintenanceCost(), baseSummary.getTotalMaintenanceCost(), "300.00");
    }

    // ------------------------------------------------------------------
    // Revenue
    // ------------------------------------------------------------------

    @Test
    void revenueCountsEveryPaymentStatusAndSumsSuccessOnly() {
        assertNotNull(baseRevenue.getTotalRevenue());

        User customer = createUser("CUSTOMER", "revcust");
        Vehicle vehicle = createVehicle("REV1", VehicleStatus.AVAILABLE);
        Rental rental = createRental(customer, vehicle, RentalStatus.COMPLETED, "revr1");
        createPayment(rental, customer, "4000.00", PaymentStatus.SUCCESS, "rev1");
        createPayment(rental, customer, "6000.00", PaymentStatus.SUCCESS, "rev2");
        createPayment(rental, customer, "1500.00", PaymentStatus.PENDING, "rev3");
        createPayment(rental, customer, "500.00", PaymentStatus.FAILED, "rev4");
        createPayment(rental, customer, "700.00", PaymentStatus.CANCELLED, "rev5");
        createPayment(rental, customer, "300.00", PaymentStatus.REFUNDED, "rev6");

        RevenueReportResponse report = reportService.getRevenueReport();

        assertEquals(baseRevenue.getSuccessCount() + 2, report.getSuccessCount());
        assertEquals(baseRevenue.getPendingCount() + 1, report.getPendingCount());
        assertEquals(baseRevenue.getFailedCount() + 1, report.getFailedCount());
        assertEquals(baseRevenue.getCancelledCount() + 1, report.getCancelledCount());
        assertEquals(baseRevenue.getRefundedCount() + 1, report.getRefundedCount());
        assertEquals(baseRevenue.getTotalTransactions() + 6, report.getTotalTransactions());
        // SUCCESS-only: pending/failed/cancelled/refunded amounts excluded.
        assertMoneyDelta(report.getTotalRevenue(), baseRevenue.getTotalRevenue(), "10000.00");
    }

    // ------------------------------------------------------------------
    // Rentals
    // ------------------------------------------------------------------

    @Test
    void rentalsReportCountsEveryRentalStatusAsExactDeltas() {
        User customer = createUser("CUSTOMER", "rencust");
        Vehicle vehicle = createVehicle("REN1", VehicleStatus.AVAILABLE);

        createRental(customer, vehicle, RentalStatus.PENDING, "r1");
        createRental(customer, vehicle, RentalStatus.CONFIRMED, "r2");
        createRental(customer, vehicle, RentalStatus.ACTIVE, "r3");
        createRental(customer, vehicle, RentalStatus.COMPLETED, "r4");
        createRental(customer, vehicle, RentalStatus.CANCELLED, "r5");

        RentalReportResponse report = reportService.getRentalsReport();

        assertEquals(baseRentals.getTotalRentals() + 5, report.getTotalRentals());
        assertEquals(baseRentals.getPendingCount() + 1, report.getPendingCount());
        assertEquals(baseRentals.getConfirmedCount() + 1, report.getConfirmedCount());
        assertEquals(baseRentals.getActiveCount() + 1, report.getActiveCount());
        assertEquals(baseRentals.getCompletedCount() + 1, report.getCompletedCount());
        assertEquals(baseRentals.getCancelledCount() + 1, report.getCancelledCount());
    }

    // ------------------------------------------------------------------
    // Vehicles (current stored-status snapshot only)
    // ------------------------------------------------------------------

    @Test
    void vehiclesReportCountsCurrentStatusSnapshotAsExactDeltas() {
        createVehicle("STA1", VehicleStatus.AVAILABLE);
        createVehicle("STA2", VehicleStatus.RENTED);
        createVehicle("STA3", VehicleStatus.MAINTENANCE);
        createVehicle("STA4", VehicleStatus.INACTIVE);

        VehicleReportResponse report = reportService.getVehiclesReport();

        assertEquals(baseVehicles.getTotalVehicles() + 4, report.getTotalVehicles());
        assertEquals(baseVehicles.getAvailableCount() + 1, report.getAvailableCount());
        assertEquals(baseVehicles.getRentedCount() + 1, report.getRentedCount());
        assertEquals(baseVehicles.getMaintenanceCount() + 1, report.getMaintenanceCount());
        assertEquals(baseVehicles.getInactiveCount() + 1, report.getInactiveCount());
    }

    // ------------------------------------------------------------------
    // Popularity
    // ------------------------------------------------------------------

    @Test
    void popularVehiclesExcludeCancelledAndZeroRentalVehiclesAndOrderDeterministically() {
        User customer = createUser("CUSTOMER", "popcust");
        Vehicle vA = createVehicle("POPA", VehicleStatus.AVAILABLE);
        Vehicle vB = createVehicle("POPB", VehicleStatus.AVAILABLE);
        Vehicle vC = createVehicle("POPC", VehicleStatus.AVAILABLE);
        Vehicle vD = createVehicle("POPD", VehicleStatus.AVAILABLE);
        Vehicle vE = createVehicle("POPE", VehicleStatus.AVAILABLE);

        // vB: 3 qualifying rentals.
        createRental(customer, vB, RentalStatus.COMPLETED, "pb1");
        createRental(customer, vB, RentalStatus.ACTIVE, "pb2");
        createRental(customer, vB, RentalStatus.CONFIRMED, "pb3");
        // vA: 2 qualifying (CANCELLED rental must not count).
        createRental(customer, vA, RentalStatus.PENDING, "pa1");
        createRental(customer, vA, RentalStatus.COMPLETED, "pa2");
        createRental(customer, vA, RentalStatus.CANCELLED, "pa3");
        // vE: 2 qualifying (tie with vA — vehicleId ASC decides).
        createRental(customer, vE, RentalStatus.PENDING, "pe1");
        createRental(customer, vE, RentalStatus.COMPLETED, "pe2");
        // vC: CANCELLED only — must be absent. vD: no rentals — must be absent.
        createRental(customer, vC, RentalStatus.CANCELLED, "pc1");

        VehicleReportResponse report = reportService.getVehiclesReport();

        PopularVehicleResponse a = popularById(report, vA.getVehicleId());
        PopularVehicleResponse b = popularById(report, vB.getVehicleId());
        PopularVehicleResponse e = popularById(report, vE.getVehicleId());

        assertNotNull(a, "vA (2 qualifying) must appear");
        assertNotNull(b, "vB (3 qualifying) must appear");
        assertNotNull(e, "vE (2 qualifying) must appear");
        assertNull(popularById(report, vC.getVehicleId()), "CANCELLED-only vehicle must be excluded");
        assertNull(popularById(report, vD.getVehicleId()), "zero-rental vehicle must be excluded");

        assertEquals(3, b.getRentalCount());
        assertEquals(2, a.getRentalCount());
        assertEquals(2, e.getRentalCount());
        assertEquals("BrandPOPA", a.getBrand());
        assertEquals("ModelPOPA", a.getModel());
        assertEquals("CAR", a.getVehicleType());

        // Deterministic global ordering: rentalCount DESC, then vehicleId ASC.
        assertPopularOrderingInvariant(report.getPopularVehicles());
        int indexOfA = report.getPopularVehicles().indexOf(a);
        int indexOfE = report.getPopularVehicles().indexOf(e);
        assertTrue(indexOfA < indexOfE, "equal counts must tie-break by vehicleId ASC");
    }

    // ------------------------------------------------------------------
    // Maintenance
    // ------------------------------------------------------------------

    @Test
    void maintenanceReportTotalsIncludeZeroCostRecordsWithExactDeltas() {
        assertNotNull(baseMaintenance.getTotalCost());
        assertNotNull(baseMaintenance.getAverageCost());

        User customer = createUser("CUSTOMER", "montcust");
        Vehicle vehicle = createVehicle("MON1", VehicleStatus.AVAILABLE);
        createMaintenance(vehicle, MaintenanceStatus.COMPLETED, "250.00", "m1");
        createMaintenance(vehicle, MaintenanceStatus.IN_PROGRESS, "150.00", "m2");
        createMaintenance(vehicle, MaintenanceStatus.SCHEDULED, "0.00", "m3");
        createMaintenance(vehicle, MaintenanceStatus.CANCELLED, "50.00", "m4");

        MaintenanceReportResponse report = reportService.getMaintenanceReport();

        assertEquals(baseMaintenance.getTotalRecords() + 4, report.getTotalRecords());
        assertEquals(baseMaintenance.getScheduledCount() + 1, report.getScheduledCount());
        assertEquals(baseMaintenance.getInProgressCount() + 1, report.getInProgressCount());
        assertEquals(baseMaintenance.getCompletedCount() + 1, report.getCompletedCount());
        assertEquals(baseMaintenance.getCancelledCount() + 1, report.getCancelledCount());
        assertMoneyDelta(report.getTotalCost(), baseMaintenance.getTotalCost(), "450.00");

        // averageCost = (baseline cost + 450) / (baseline records + 4), scale 2.
        BigDecimal expectedAverage = baseMaintenance.getTotalCost()
                .add(new BigDecimal("450.00"))
                .divide(BigDecimal.valueOf(baseMaintenance.getTotalRecords() + 4), 2, RoundingMode.HALF_UP);
        assertEquals(0, report.getAverageCost().compareTo(expectedAverage),
                "averageCost must equal AVG over all records including the zero-cost row");
    }
}
