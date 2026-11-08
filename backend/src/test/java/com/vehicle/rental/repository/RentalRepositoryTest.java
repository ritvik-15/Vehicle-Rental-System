package com.vehicle.rental.repository;

import com.vehicle.rental.dto.AvailabilityResponse;
import com.vehicle.rental.dto.RentalCreateRequest;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.entity.Maintenance;
import com.vehicle.rental.entity.Rental;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.MaintenanceStatus;
import com.vehicle.rental.enums.RentalStatus;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.service.RentalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RentalRepositoryTest {

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private MaintenanceRepository maintenanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RentalService rentalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setFullName("Rental Integration Test");
        testUser.setEmail("rentalintegration@example.com");
        testUser.setPhone("9812345680");
        testUser.setPasswordHash("$2a$10$IntegrationTestPasswordHashValueThatIsLongEnough1");
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.saveAndFlush(testUser);

        testVehicle = new Vehicle();
        testVehicle.setRegistrationNumber("PVTINTEG1");
        testVehicle.setBrand("Hyundai");
        testVehicle.setModel("i20");
        testVehicle.setVehicleType(VehicleType.CAR);
        testVehicle.setDailyRate(new BigDecimal("1200.00"));
        testVehicle.setSeatingCapacity(5);
        testVehicle.setFuelType(FuelType.PETROL);
        testVehicle.setTransmission(TransmissionType.AUTOMATIC);
        testVehicle.setLocation("Mumbai");
        testVehicle.setStatus(VehicleStatus.AVAILABLE);
        testVehicle = vehicleRepository.saveAndFlush(testVehicle);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Rental buildRental(RentalStatus status, LocalDateTime pickup, LocalDateTime ret) {
        Rental rental = new Rental();
        rental.setUser(testUser);
        rental.setVehicle(testVehicle);
        rental.setPickupDatetime(pickup);
        rental.setReturnDatetime(ret);
        rental.setNumberOfDays(2);
        rental.setDailyRate(new BigDecimal("1200.00"));
        rental.setSubtotal(new BigDecimal("2400.00"));
        rental.setDiscountAmount(new BigDecimal("0.00"));
        rental.setTaxAmount(new BigDecimal("432.00"));
        rental.setSecurityDeposit(new BigDecimal("480.00"));
        rental.setTotalAmount(new BigDecimal("3312.00"));
        rental.setRentalStatus(status);
        return rental;
    }

    private void insertPendingRentalWithCreatedAt(LocalDateTime pickup, LocalDateTime ret,
                                                    LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO rentals (user_id, vehicle_id, pickup_datetime, return_datetime, "
                        + "number_of_days, daily_rate, subtotal, discount_amount, tax_amount, "
                        + "security_deposit, total_amount, rental_status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)",
                testUser.getUserId(), testVehicle.getVehicleId(), pickup, ret,
                2, new BigDecimal("1200.00"), new BigDecimal("2400.00"),
                new BigDecimal("0.00"), new BigDecimal("432.00"),
                new BigDecimal("480.00"), new BigDecimal("3312.00"),
                createdAt, createdAt);
    }

    private Maintenance buildMaintenance(MaintenanceStatus status, LocalDate maintenanceDate,
                                            LocalDate completionDate) {
        Maintenance maintenance = new Maintenance();
        maintenance.setVehicle(testVehicle);
        maintenance.setMaintenanceType("Routine Service");
        maintenance.setMaintenanceDate(maintenanceDate);
        maintenance.setCompletionDate(completionDate);
        maintenance.setCost(new BigDecimal("500.00"));
        maintenance.setStatus(status);
        return maintenance;
    }

    private void authenticateAs(Long userId, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private User createUser(String email, String phone, UserRole role) {
        User user = new User();
        user.setFullName("Rental Test " + email);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash("$2a$10$RentalTestPasswordHashValueThatIsLongEnough1");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    @Test
    void blockingRentalStatusesPreventOverlap() {
        LocalDateTime pickup = LocalDateTime.now().plusDays(10);
        LocalDateTime ret = pickup.plusDays(2);
        rentalRepository.save(buildRental(RentalStatus.PENDING, pickup, ret));
        rentalRepository.save(buildRental(RentalStatus.CONFIRMED, pickup, ret));
        rentalRepository.save(buildRental(RentalStatus.ACTIVE, pickup, ret));

        long count = rentalRepository.countOverlappingBlockingRentals(
                testVehicle.getVehicleId(),
                pickup.plusHours(2), ret.minusHours(2),
                LocalDateTime.now().minusMinutes(15));

        assertEquals(3, count);
    }

    @Test
    void completedAndCancelledRentalsDoNotBlock() {
        LocalDateTime pickup = LocalDateTime.now().plusDays(10);
        LocalDateTime ret = pickup.plusDays(2);
        rentalRepository.save(buildRental(RentalStatus.COMPLETED, pickup, ret));
        rentalRepository.save(buildRental(RentalStatus.CANCELLED, pickup, ret));

        long count = rentalRepository.countOverlappingBlockingRentals(
                testVehicle.getVehicleId(),
                pickup.plusHours(2), ret.minusHours(2),
                LocalDateTime.now());

        assertEquals(0, count);
    }

    @Test
    void freshPendingRentalBlocksBeforeExpiry() {
        LocalDateTime pickup = LocalDateTime.now().plusDays(10);
        LocalDateTime ret = pickup.plusDays(2);
        rentalRepository.save(buildRental(RentalStatus.PENDING, pickup, ret));

        long count = rentalRepository.countOverlappingBlockingRentals(
                testVehicle.getVehicleId(),
                pickup.plusHours(2), ret.minusHours(2),
                LocalDateTime.now().minusMinutes(15));

        assertEquals(1, count);
    }

    @Test
    void expiredPendingRentalDoesNotBlock() {
        LocalDateTime pickup = LocalDateTime.now().plusDays(10);
        LocalDateTime ret = pickup.plusDays(2);
        insertPendingRentalWithCreatedAt(pickup, ret, LocalDateTime.now().minusMinutes(20));

        long count = rentalRepository.countOverlappingBlockingRentals(
                testVehicle.getVehicleId(),
                pickup.plusHours(2), ret.minusHours(2),
                LocalDateTime.now().minusMinutes(15));

        assertEquals(0, count);
    }

    @Test
    void findExpiredPendingRentalsAndCancelRentalIfPending() {
        LocalDateTime pickup = LocalDateTime.now().plusDays(10);
        LocalDateTime ret = pickup.plusDays(2);
        rentalRepository.save(buildRental(RentalStatus.PENDING, pickup, ret));
        insertPendingRentalWithCreatedAt(pickup.plusDays(5), ret.plusDays(5),
                LocalDateTime.now().minusMinutes(20));

        List<Rental> expired = rentalRepository.findExpiredPendingRentals(
                RentalStatus.PENDING, LocalDateTime.now().minusMinutes(15));

        assertEquals(1, expired.size());
        assertEquals(RentalStatus.PENDING, expired.get(0).getRentalStatus());

        int updated = rentalRepository.cancelRentalIfPending(
                expired.get(0).getRentalId(), RentalStatus.PENDING, RentalStatus.CANCELLED);
        assertEquals(1, updated);

        // exactly-once guard: a second concurrent sweep must be a no-op
        int again = rentalRepository.cancelRentalIfPending(
                expired.get(0).getRentalId(), RentalStatus.PENDING, RentalStatus.CANCELLED);
        assertEquals(0, again);

        // fresh database state (the conditional update bypasses the persistence context)
        List<String> statuses = jdbcTemplate.query(
                "SELECT rental_status FROM rentals WHERE vehicle_id = ? ORDER BY rental_id",
                (rs, rowNum) -> rs.getString(1), testVehicle.getVehicleId());
        assertEquals(List.of("PENDING", "CANCELLED"), statuses);
    }

    @Test
    void scheduledMaintenanceWithCompletionDateBlocksOverlappingPeriod() {
        LocalDate start = LocalDate.now().plusDays(6);
        LocalDate end = start.plusDays(2);
        maintenanceRepository.saveAndFlush(
                buildMaintenance(MaintenanceStatus.SCHEDULED, start, end));

        assertEquals(1, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), start.plusDays(1), start.plusDays(2)));
        assertEquals(0, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), start.minusDays(3), start.minusDays(2)));
        assertEquals(0, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), end.plusDays(1), end.plusDays(2)));
    }

    @Test
    void scheduledSingleDayMaintenanceBlocksSameDayOnly() {
        LocalDate date = LocalDate.now().plusDays(6);
        maintenanceRepository.saveAndFlush(
                buildMaintenance(MaintenanceStatus.SCHEDULED, date, null));

        assertEquals(1, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), date, date));
        assertEquals(0, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), date.plusDays(1), date.plusDays(2)));
    }

    @Test
    void inProgressMaintenanceWithoutCompletionBlocksFutureRentals() {
        LocalDate started = LocalDate.now().minusDays(1);
        maintenanceRepository.saveAndFlush(
                buildMaintenance(MaintenanceStatus.IN_PROGRESS, started, null));

        assertEquals(1, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(),
                LocalDate.now().plusDays(2), LocalDate.now().plusDays(3)));
        assertEquals(0, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(),
                started.minusDays(5), started.minusDays(4)));
    }

    @Test
    void inProgressMaintenanceWithCompletionBlocksWindow() {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(2);
        maintenanceRepository.saveAndFlush(
                buildMaintenance(MaintenanceStatus.IN_PROGRESS, start, end));

        assertEquals(1, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), LocalDate.now(), LocalDate.now().plusDays(1)));
        assertEquals(0, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), end.plusDays(1), end.plusDays(2)));
    }

    @Test
    void completedAndCancelledMaintenanceDoNotBlock() {
        LocalDate start = LocalDate.now().plusDays(6);
        maintenanceRepository.saveAndFlush(
                buildMaintenance(MaintenanceStatus.COMPLETED, start, start.plusDays(2)));
        maintenanceRepository.saveAndFlush(
                buildMaintenance(MaintenanceStatus.CANCELLED, start.plusDays(10), null));

        assertEquals(0, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), start, start.plusDays(2)));
        assertEquals(0, maintenanceRepository.countConflictingMaintenance(
                testVehicle.getVehicleId(), start.plusDays(10), start.plusDays(11)));
    }

    @Test
    void fullBookingFlowPersistsServerSidePricingAndKeepsVehicleAvailable() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);

        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(testVehicle.getVehicleId());
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(LocalDateTime.now().plusDays(7));
        request.setNotes("Integration test rental");

        RentalResponse response = rentalService.createRental(request);

        assertNotNull(response.getRentalId());
        assertEquals("PENDING", response.getRentalStatus());
        assertEquals(2, response.getNumberOfDays());
        assertEquals(new BigDecimal("1200.00"), response.getDailyRate());
        assertEquals(new BigDecimal("2400.00"), response.getSubtotal());
        assertEquals(new BigDecimal("0.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("432.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("480.00"), response.getSecurityDeposit());
        assertEquals(new BigDecimal("3312.00"), response.getTotalAmount());

        // a future booking must never change the vehicle status to RENTED
        Vehicle refreshed = vehicleRepository.findById(testVehicle.getVehicleId()).orElseThrow();
        assertEquals(VehicleStatus.AVAILABLE, refreshed.getStatus());

        // the same period cannot be booked again
        assertThrows(DuplicateResourceException.class,
                () -> rentalService.createRental(request));

        // the booked period is no longer available
        AvailabilityResponse availability = rentalService.checkAvailability(
                testVehicle.getVehicleId(),
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        assertFalse(availability.isAvailable());

        // a different period remains available
        AvailabilityResponse otherPeriod = rentalService.checkAvailability(
                testVehicle.getVehicleId(),
                LocalDateTime.now().plusDays(20), LocalDateTime.now().plusDays(22));
        assertTrue(otherPeriod.isAvailable());
    }

    @Test
    void myRentalsReturnsOnlyAuthenticatedUserRentals() {
        User otherUser = createUser("otherrenter@example.com", "9812345681", UserRole.CUSTOMER);
        Rental otherRental = buildRental(RentalStatus.PENDING,
                LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(32));
        otherRental.setUser(otherUser);
        rentalRepository.saveAndFlush(otherRental);

        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(testVehicle.getVehicleId());
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(LocalDateTime.now().plusDays(7));
        rentalService.createRental(request);

        List<RentalResponse> myRentals = rentalService.getMyRentals();

        assertEquals(1, myRentals.size());
        assertEquals(testUser.getUserId(), myRentals.get(0).getUserId());
    }

    @Test
    void cancellationFreesAvailabilityAndRejectsRepeatCancellation() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);

        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(testVehicle.getVehicleId());
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(LocalDateTime.now().plusDays(7));
        RentalResponse created = rentalService.createRental(request);

        RentalResponse cancelled = rentalService.cancelRental(created.getRentalId());
        assertEquals("CANCELLED", cancelled.getRentalStatus());

        // a cancelled rental no longer blocks the period
        AvailabilityResponse availability = rentalService.checkAvailability(
                testVehicle.getVehicleId(),
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        assertTrue(availability.isAvailable());

        // cancelling again is rejected
        assertThrows(InvalidRequestException.class,
                () -> rentalService.cancelRental(created.getRentalId()));
    }

    @Test
    void customerCannotAccessAnotherUsersRental() {
        User otherUser = createUser("owneruser@example.com", "9812345682", UserRole.CUSTOMER);
        Rental otherRental = buildRental(RentalStatus.PENDING,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        otherRental.setUser(otherUser);
        Rental saved = rentalRepository.saveAndFlush(otherRental);

        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);

        assertThrows(ForbiddenException.class,
                () -> rentalService.getRentalDetails(saved.getRentalId()));
        assertThrows(ForbiddenException.class,
                () -> rentalService.cancelRental(saved.getRentalId()));
    }

    @Test
    void adminCanViewAnyRental() {
        User adminUser = createUser("adminviewer@example.com", "9812345683", UserRole.ADMIN);
        Rental saved = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.PENDING,
                        LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7)));

        authenticateAs(adminUser.getUserId(), UserRole.ADMIN);

        RentalResponse response = rentalService.getRentalDetails(saved.getRentalId());

        assertEquals(saved.getRentalId(), response.getRentalId());
    }

    @Test
    void maintenanceConflictRejectsBooking() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        LocalDate start = LocalDate.now().plusDays(6);
        maintenanceRepository.saveAndFlush(
                buildMaintenance(MaintenanceStatus.SCHEDULED, start, start.plusDays(2)));

        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(testVehicle.getVehicleId());
        request.setPickupDateTime(start.plusDays(1).atTime(10, 0));
        request.setReturnDateTime(start.plusDays(2).atTime(10, 0));

        assertThrows(DuplicateResourceException.class,
                () -> rentalService.createRental(request));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentBookingsOnlyOneSucceeds() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = new User();
        user.setFullName("Concurrency Test");
        user.setEmail("concurrency" + suffix + "@example.com");
        user.setPhone("98" + suffix.substring(suffix.length() - 8));
        user.setPasswordHash("$2a$10$ConcurrencyTestPasswordHashValueLongEnough1");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        User bookingUser = userRepository.save(user);

        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("PVTCONC" + suffix.substring(suffix.length() - 5));
        vehicle.setBrand("Toyota");
        vehicle.setModel("Etios");
        vehicle.setVehicleType(VehicleType.CAR);
        vehicle.setDailyRate(new BigDecimal("1000.00"));
        vehicle.setSeatingCapacity(4);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setTransmission(TransmissionType.MANUAL);
        vehicle.setLocation("Pune");
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        Vehicle bookingVehicle = vehicleRepository.save(vehicle);

        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(bookingVehicle.getVehicleId());
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(LocalDateTime.now().plusDays(7));

        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicReference<Long> successfulRentalId = new AtomicReference<>();
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    String.valueOf(user.getUserId()),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
                    startLatch.await();
                    RentalResponse response = rentalService.createRental(request);
                    successes.incrementAndGet();
                    successfulRentalId.set(response.getRentalId());
                } catch (DuplicateResourceException e) {
                    conflicts.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            threads.add(thread);
            thread.start();
        }

        startLatch.countDown();
        for (Thread thread : threads) {
            thread.join(30000);
        }

        try {
            assertEquals(1, successes.get());
            assertEquals(1, conflicts.get());
            assertTrue(errors.isEmpty(), "Unexpected thread errors: " + errors);
            assertNotNull(successfulRentalId.get());
        } finally {
            try {
                rentalRepository.deleteAll(rentalRepository
                        .findByVehicleVehicleIdOrderByPickupDatetimeAsc(bookingVehicle.getVehicleId()));
                rentalRepository.deleteAll(rentalRepository
                        .findByVehicleVehicleIdOrderByPickupDatetimeAsc(testVehicle.getVehicleId()));
                vehicleRepository.delete(bookingVehicle);
                vehicleRepository.delete(testVehicle);
                userRepository.delete(bookingUser);
                userRepository.delete(testUser);
            } catch (Exception e) {
                // cleanup must not mask the actual test result
            }
        }
    }
}
