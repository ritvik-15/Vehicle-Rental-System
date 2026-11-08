package com.vehicle.rental.repository;

import com.vehicle.rental.dto.MaintenanceCreateRequest;
import com.vehicle.rental.dto.MaintenanceResponse;
import com.vehicle.rental.dto.MaintenanceStatusRequest;
import com.vehicle.rental.dto.MaintenanceUpdateRequest;
import com.vehicle.rental.dto.PromoValidateRequest;
import com.vehicle.rental.dto.PromoValidationResponse;
import com.vehicle.rental.dto.RentalCreateRequest;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.entity.PromoCode;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.DiscountType;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.MaintenanceStatus;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.service.MaintenanceService;
import com.vehicle.rental.service.PromoService;
import com.vehicle.rental.service.RentalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MaintenancePromoIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private MaintenanceRepository maintenanceRepository;

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    @Autowired
    private MaintenanceService maintenanceService;

    @Autowired
    private PromoService promoService;

    @Autowired
    private RentalService rentalService;

    private User testUser;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setFullName("Phase 7 Integration Test");
        testUser.setEmail("phase7integration@example.com");
        testUser.setPhone("9812345681");
        testUser.setPasswordHash("$2a$10$Phase7IntegrationTestPasswordHash1");
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.saveAndFlush(testUser);

        testVehicle = new Vehicle();
        testVehicle.setRegistrationNumber("PVTPHASE7");
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

    private void authenticateAs(Long userId, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private MaintenanceResponse createMaintenanceViaService(MaintenanceStatus status,
                                                                   LocalDate date,
                                                                   LocalDate completion) {
        MaintenanceCreateRequest request = new MaintenanceCreateRequest();
        request.setVehicleId(testVehicle.getVehicleId());
        request.setMaintenanceType("Integration Service");
        request.setDescription("Scheduled maintenance");
        request.setMaintenanceDate(date);
        request.setCompletionDate(completion);
        request.setCost(new BigDecimal("500.00"));
        request.setServiceProvider("Service Center");
        MaintenanceResponse response = maintenanceService.createMaintenance(request);
        if (status != MaintenanceStatus.SCHEDULED) {
            MaintenanceStatusRequest statusRequest = new MaintenanceStatusRequest();
            statusRequest.setStatus(status.name());
            response = maintenanceService.updateMaintenanceStatus(
                    response.getMaintenanceId(), statusRequest);
        }
        return response;
    }

    private PromoCode createPromo(String code, DiscountType type, BigDecimal value,
                                         BigDecimal minimum, BigDecimal maximum,
                                         LocalDate start, LocalDate expiry,
                                         Integer usageLimit, int usedCount, String status) {
        PromoCode promo = new PromoCode();
        promo.setCode(code);
        promo.setDiscountType(type);
        promo.setDiscountValue(value);
        promo.setMinimumAmount(minimum);
        promo.setMaximumDiscount(maximum);
        promo.setStartDate(start);
        promo.setExpiryDate(expiry);
        promo.setUsageLimit(usageLimit);
        promo.setUsedCount(usedCount);
        promo.setStatus(status);
        return promoCodeRepository.saveAndFlush(promo);
    }

    private RentalCreateRequest bookingRequest(LocalDateTime pickup, LocalDateTime ret) {
        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(testVehicle.getVehicleId());
        request.setPickupDateTime(pickup);
        request.setReturnDateTime(ret);
        return request;
    }

    // ---------- maintenance CRUD ----------

    @Test
    void createMaintenanceDefaultsToScheduled() {
        LocalDate date = LocalDate.now().plusDays(5);
        LocalDate completion = LocalDate.now().plusDays(6);

        MaintenanceResponse response = createMaintenanceViaService(
                MaintenanceStatus.SCHEDULED, date, completion);

        assertNotNull(response.getMaintenanceId());
        assertEquals("SCHEDULED", response.getStatus());
        assertEquals(testVehicle.getVehicleId(), response.getVehicleId());
        assertEquals("PVTPHASE7", response.getVehicleRegistrationNumber());
        assertEquals("Hyundai", response.getVehicleBrand());
        assertEquals("i20", response.getVehicleModel());
        assertEquals("Integration Service", response.getMaintenanceType());
        assertEquals(date, response.getMaintenanceDate());
        assertEquals(completion, response.getCompletionDate());
        assertEquals(new BigDecimal("500.00"), response.getCost());
        assertEquals("Service Center", response.getServiceProvider());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    void updateMaintenanceUpdatesFields() {
        MaintenanceResponse created = createMaintenanceViaService(
                MaintenanceStatus.SCHEDULED, LocalDate.now().plusDays(5), null);

        MaintenanceUpdateRequest request = new MaintenanceUpdateRequest();
        request.setVehicleId(testVehicle.getVehicleId());
        request.setMaintenanceType("Major Service");
        request.setDescription("Full service");
        request.setMaintenanceDate(LocalDate.now().plusDays(10));
        request.setCompletionDate(LocalDate.now().plusDays(12));
        request.setCost(new BigDecimal("1500.00"));
        request.setServiceProvider("Premium Center");
        request.setNotes("Updated notes");

        MaintenanceResponse updated = maintenanceService.updateMaintenance(
                created.getMaintenanceId(), request);

        assertEquals("Major Service", updated.getMaintenanceType());
        assertEquals("Full service", updated.getDescription());
        assertEquals(LocalDate.now().plusDays(10), updated.getMaintenanceDate());
        assertEquals(LocalDate.now().plusDays(12), updated.getCompletionDate());
        assertEquals(new BigDecimal("1500.00"), updated.getCost());
        assertEquals("Premium Center", updated.getServiceProvider());
        assertEquals("Updated notes", updated.getNotes());
    }

    @Test
    void updateMaintenanceStatusTransitions() {
        MaintenanceResponse created = createMaintenanceViaService(
                MaintenanceStatus.SCHEDULED, LocalDate.now().plusDays(5), null);

        MaintenanceStatusRequest statusRequest = new MaintenanceStatusRequest();
        statusRequest.setStatus("IN_PROGRESS");
        MaintenanceResponse updated = maintenanceService.updateMaintenanceStatus(
                created.getMaintenanceId(), statusRequest);
        assertEquals("IN_PROGRESS", updated.getStatus());

        statusRequest.setStatus("COMPLETED");
        updated = maintenanceService.updateMaintenanceStatus(
                created.getMaintenanceId(), statusRequest);
        assertEquals("COMPLETED", updated.getStatus());
    }

    @Test
    void getAllMaintenanceReturnsRecords() {
        createMaintenanceViaService(MaintenanceStatus.SCHEDULED,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));

        List<MaintenanceResponse> list = maintenanceService.getAllMaintenance();

        assertFalse(list.isEmpty());
        assertTrue(list.stream()
                .anyMatch(m -> m.getVehicleId().equals(testVehicle.getVehicleId())));
    }

    @Test
    void getMaintenanceDetailsReturnsRecord() {
        MaintenanceResponse created = createMaintenanceViaService(
                MaintenanceStatus.SCHEDULED, LocalDate.now().plusDays(5), null);

        MaintenanceResponse details = maintenanceService.getMaintenanceDetails(
                created.getMaintenanceId());

        assertEquals(created.getMaintenanceId(), details.getMaintenanceId());
        assertEquals(testVehicle.getVehicleId(), details.getVehicleId());
    }

    // ---------- maintenance availability rules ----------

    @Test
    void scheduledMaintenanceBlocksRentalBooking() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        LocalDate start = LocalDate.now().plusDays(6);
        createMaintenanceViaService(MaintenanceStatus.SCHEDULED, start, start.plusDays(2));

        RentalCreateRequest request = bookingRequest(
                start.plusDays(1).atTime(10, 0), start.plusDays(2).atTime(10, 0));

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> rentalService.createRental(request));
        assertTrue(exception.getMessage().contains("maintenance"));
    }

    @Test
    void scheduledMaintenanceRangeBoundaries() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        LocalDate start = LocalDate.now().plusDays(16);
        LocalDate end = start.plusDays(2);
        createMaintenanceViaService(MaintenanceStatus.SCHEDULED, start, end);

        // booking ending the day before maintenance starts: allowed
        RentalCreateRequest before = bookingRequest(
                start.minusDays(2).atTime(10, 0), start.minusDays(1).atTime(10, 0));
        RentalResponse beforeResponse = rentalService.createRental(before);
        assertNotNull(beforeResponse.getRentalId());

        // booking on the completion day: still blocked
        RentalCreateRequest onCompletion = bookingRequest(
                end.atTime(10, 0), end.plusDays(1).atTime(10, 0));
        assertThrows(DuplicateResourceException.class,
                () -> rentalService.createRental(onCompletion));

        // booking the day after completion: allowed
        RentalCreateRequest after = bookingRequest(
                end.plusDays(1).atTime(10, 0), end.plusDays(2).atTime(10, 0));
        RentalResponse afterResponse = rentalService.createRental(after);
        assertNotNull(afterResponse.getRentalId());
    }

    @Test
    void inProgressMaintenanceWithoutCompletionBlocksFutureBookings() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        createMaintenanceViaService(MaintenanceStatus.IN_PROGRESS,
                LocalDate.now().minusDays(1), null);

        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(32));

        assertThrows(DuplicateResourceException.class,
                () -> rentalService.createRental(request));
    }

    @Test
    void completedMaintenanceDoesNotBlockBookings() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        LocalDate start = LocalDate.now().plusDays(6);
        createMaintenanceViaService(MaintenanceStatus.COMPLETED, start, start.plusDays(2));

        RentalCreateRequest request = bookingRequest(
                start.atTime(10, 0), start.plusDays(1).atTime(10, 0));

        RentalResponse response = rentalService.createRental(request);
        assertNotNull(response.getRentalId());
        assertEquals("PENDING", response.getRentalStatus());
    }

    @Test
    void cancelledMaintenanceDoesNotBlockBookings() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        LocalDate start = LocalDate.now().plusDays(6);
        createMaintenanceViaService(MaintenanceStatus.CANCELLED, start, start.plusDays(2));

        RentalCreateRequest request = bookingRequest(
                start.atTime(10, 0), start.plusDays(1).atTime(10, 0));

        RentalResponse response = rentalService.createRental(request);
        assertNotNull(response.getRentalId());
    }

    @Test
    void nonConflictingRentalSucceedsAlongsideMaintenance() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        LocalDate start = LocalDate.now().plusDays(6);
        createMaintenanceViaService(MaintenanceStatus.SCHEDULED, start, start.plusDays(2));

        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(20), LocalDateTime.now().plusDays(22));

        RentalResponse response = rentalService.createRental(request);
        assertNotNull(response.getRentalId());
        assertEquals("PENDING", response.getRentalStatus());
    }

    // ---------- promo validation ----------

    @Test
    void validPercentagePromoValidation() {
        createPromo("PCT10", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                new BigDecimal("100.00"), null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                null, 0, "ACTIVE");

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("PCT10");
        request.setRentalAmount(new BigDecimal("2000.00"));

        PromoValidationResponse response = promoService.validatePromo(request);

        assertEquals("PCT10", response.getCode());
        assertEquals("PERCENTAGE", response.getDiscountType());
        assertEquals(new BigDecimal("200.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("1800.00"), response.getFinalAmount());
        assertEquals("Promo code applied successfully", response.getMessage());
    }

    @Test
    void validFixedPromoValidation() {
        createPromo("FIXD150", DiscountType.FIXED, new BigDecimal("150.00"),
                new BigDecimal("100.00"), null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                null, 0, "ACTIVE");

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("FIXD150");
        request.setRentalAmount(new BigDecimal("2000.00"));

        PromoValidationResponse response = promoService.validatePromo(request);

        assertEquals(new BigDecimal("150.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("1850.00"), response.getFinalAmount());
        assertEquals("FIXED", response.getDiscountType());
    }

    @Test
    void inactivePromoValidationRejected() {
        createPromo("INACT1", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                BigDecimal.ZERO, null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                null, 0, "INACTIVE");

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("INACT1");
        request.setRentalAmount(new BigDecimal("2000.00"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(request));
        assertTrue(exception.getMessage().contains("not active"));
    }

    @Test
    void expiredPromoValidationRejected() {
        createPromo("EXPIRED1", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                BigDecimal.ZERO, null,
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(1),
                null, 0, "ACTIVE");

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("EXPIRED1");
        request.setRentalAmount(new BigDecimal("2000.00"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(request));
        assertTrue(exception.getMessage().contains("expired"));
    }

    @Test
    void notYetStartedPromoValidationRejected() {
        createPromo("FUTURE1", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                BigDecimal.ZERO, null,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(10),
                null, 0, "ACTIVE");

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("FUTURE1");
        request.setRentalAmount(new BigDecimal("2000.00"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(request));
        assertTrue(exception.getMessage().contains("not yet valid"));
    }

    @Test
    void minimumAmountValidationFailure() {
        createPromo("MINAMT1", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                new BigDecimal("1000.00"), null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                null, 0, "ACTIVE");

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("MINAMT1");
        request.setRentalAmount(new BigDecimal("500.00"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(request));
        assertTrue(exception.getMessage().contains("minimum amount"));
    }

    @Test
    void usageLimitValidationRejected() {
        createPromo("USAGELIM", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                BigDecimal.ZERO, null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                5, 5, "ACTIVE");

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("USAGELIM");
        request.setRentalAmount(new BigDecimal("2000.00"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(request));
        assertTrue(exception.getMessage().contains("usage limit"));
    }

    @Test
    void maximumDiscountCapValidation() {
        createPromo("CAPMAX1", DiscountType.PERCENTAGE, new BigDecimal("50.00"),
                BigDecimal.ZERO, new BigDecimal("300.00"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                null, 0, "ACTIVE");

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("CAPMAX1");
        request.setRentalAmount(new BigDecimal("2000.00"));

        PromoValidationResponse response = promoService.validatePromo(request);

        // 50% of 2000.00 = 1000.00, capped at 300.00
        assertEquals(new BigDecimal("300.00"), response.getDiscountAmount());
    }

    // ---------- rental + promo integration ----------

    @Test
    void bookingWithPercentagePromoCalculatesDiscountServerSide() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        createPromo("BOOKPCT", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                new BigDecimal("100.00"), null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                null, 0, "ACTIVE");

        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        request.setPromoCode("BOOKPCT");

        RentalResponse response = rentalService.createRental(request);

        // subtotal = 1200.00 x 2 days = 2400.00
        assertEquals(new BigDecimal("2400.00"), response.getSubtotal());
        // discount = 10% of 2400.00 = 240.00
        assertEquals(new BigDecimal("240.00"), response.getDiscountAmount());
        // tax = 18% of (2400.00 - 240.00) = 388.80
        assertEquals(new BigDecimal("388.80"), response.getTaxAmount());
        // deposit = 20% of 2400.00 = 480.00
        assertEquals(new BigDecimal("480.00"), response.getSecurityDeposit());
        // total = 2160.00 + 388.80 + 480.00 = 3028.80
        assertEquals(new BigDecimal("3028.80"), response.getTotalAmount());
        assertEquals("BOOKPCT", response.getPromoCode());

        PromoCode updated = promoCodeRepository.findByCode("BOOKPCT").orElseThrow();
        assertEquals(1, updated.getUsedCount());
    }

    @Test
    void bookingWithFixedPromoCalculatesDiscountServerSide() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        createPromo("BOOKFIX", DiscountType.FIXED, new BigDecimal("150.00"),
                new BigDecimal("100.00"), null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                null, 0, "ACTIVE");

        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        request.setPromoCode("BOOKFIX");

        RentalResponse response = rentalService.createRental(request);

        assertEquals(new BigDecimal("2400.00"), response.getSubtotal());
        assertEquals(new BigDecimal("150.00"), response.getDiscountAmount());
        // tax = 18% of 2250.00 = 405.00
        assertEquals(new BigDecimal("405.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("480.00"), response.getSecurityDeposit());
        // total = 2250.00 + 405.00 + 480.00 = 3135.00
        assertEquals(new BigDecimal("3135.00"), response.getTotalAmount());
        assertEquals("BOOKFIX", response.getPromoCode());

        PromoCode updated = promoCodeRepository.findByCode("BOOKFIX").orElseThrow();
        assertEquals(1, updated.getUsedCount());
    }

    @Test
    void failedBookingDoesNotConsumePromo() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        createPromo("FAILPCT", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                BigDecimal.ZERO, null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                null, 0, "ACTIVE");

        LocalDateTime pickup = LocalDateTime.now().plusDays(8);
        LocalDateTime ret = pickup.plusDays(2);

        RentalCreateRequest first = bookingRequest(pickup, ret);
        first.setPromoCode("FAILPCT");
        rentalService.createRental(first);
        assertEquals(1, promoCodeRepository.findByCode("FAILPCT").orElseThrow().getUsedCount());

        // overlapping booking with the same promo must be rejected
        RentalCreateRequest overlapping = bookingRequest(
                pickup.plusHours(1), ret.minusHours(1));
        overlapping.setPromoCode("FAILPCT");
        assertThrows(DuplicateResourceException.class,
                () -> rentalService.createRental(overlapping));

        // the failed booking did not consume the promo
        assertEquals(1, promoCodeRepository.findByCode("FAILPCT").orElseThrow().getUsedCount());
    }

    @Test
    void usageLimitedPromoEnforcedDuringBooking() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);
        createPromo("LIMITFIX", DiscountType.FIXED, new BigDecimal("100.00"),
                BigDecimal.ZERO, null,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10),
                1, 0, "ACTIVE");

        RentalCreateRequest first = bookingRequest(
                LocalDateTime.now().plusDays(12), LocalDateTime.now().plusDays(14));
        first.setPromoCode("LIMITFIX");
        RentalResponse firstResponse = rentalService.createRental(first);
        assertEquals(new BigDecimal("100.00"), firstResponse.getDiscountAmount());
        assertEquals(1, promoCodeRepository.findByCode("LIMITFIX").orElseThrow().getUsedCount());

        // different period, but the usage limit (1) is now exhausted
        RentalCreateRequest second = bookingRequest(
                LocalDateTime.now().plusDays(20), LocalDateTime.now().plusDays(22));
        second.setPromoCode("LIMITFIX");
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.createRental(second));
        assertTrue(exception.getMessage().contains("usage limit"));
    }

    @Test
    void bookingWithoutPromoKeepsZeroDiscount() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);

        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));

        RentalResponse response = rentalService.createRental(request);

        assertEquals(new BigDecimal("2400.00"), response.getSubtotal());
        assertEquals(new BigDecimal("0.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("432.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("480.00"), response.getSecurityDeposit());
        assertEquals(new BigDecimal("3312.00"), response.getTotalAmount());
        assertNull(response.getPromoCode());
    }

    @Test
    void bookingWithUnknownPromoRejected() {
        authenticateAs(testUser.getUserId(), UserRole.CUSTOMER);

        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        request.setPromoCode("NOEXIST");

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.createRental(request));
        assertTrue(exception.getMessage().contains("Promo code not found"));
    }
}
