package com.vehicle.rental.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.CustomerResponse;
import com.vehicle.rental.dto.CustomerStatusRequest;
import com.vehicle.rental.dto.PaymentResponse;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.dto.RentalStatusRequest;
import com.vehicle.rental.entity.Payment;
import com.vehicle.rental.entity.PromoCode;
import com.vehicle.rental.entity.Rental;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.DiscountType;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.PaymentMethod;
import com.vehicle.rental.enums.PaymentStatus;
import com.vehicle.rental.enums.RentalStatus;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.payment.PaymentService;
import com.vehicle.rental.service.CustomerService;
import com.vehicle.rental.service.RentalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9 real-MySQL integration: CUSTOMER-only customer management, the exact
 * admin rental transition matrix (including promo release accounting), /return
 * semantics with actual_return_datetime, admin payment listing, and
 * deterministic createdAt DESC ordering. Everything runs inside a rolled-back
 * transaction — no leftover test data.
 */
@SpringBootTest
@Transactional
class AdminOperationsIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private RentalService rentalService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private User testCustomer;
    private User secondCustomer;
    private User testAdmin;
    private Vehicle testVehicle;
    /** Committed CUSTOMER rows already in the shared DB before this test's fixtures. */
    private int baselineCustomerCount;

    @BeforeEach
    void setUp() {
        // Shared-database isolation: count assertions must be relative to the
        // committed baseline, never an assumed-empty table.
        baselineCustomerCount = customerService.getAllCustomers().size();

        testCustomer = new User();
        testCustomer.setFullName("Phase 9 Customer One");
        testCustomer.setEmail("phase9customer1@example.com");
        testCustomer.setPhone("9812345691");
        testCustomer.setPasswordHash("$2a$10$Phase9SecretHashThatMustNeverLeak000");
        testCustomer.setRole(UserRole.CUSTOMER);
        testCustomer.setStatus(UserStatus.ACTIVE);
        testCustomer = userRepository.saveAndFlush(testCustomer);

        secondCustomer = new User();
        secondCustomer.setFullName("Phase 9 Customer Two");
        secondCustomer.setEmail("phase9customer2@example.com");
        secondCustomer.setPhone("9812345692");
        secondCustomer.setPasswordHash("$2a$10$Phase9SecondSecretHashThatMustNotLeak");
        secondCustomer.setRole(UserRole.CUSTOMER);
        secondCustomer.setStatus(UserStatus.ACTIVE);
        secondCustomer = userRepository.saveAndFlush(secondCustomer);

        testAdmin = new User();
        testAdmin.setFullName("Phase 9 Admin");
        testAdmin.setEmail("phase9admin@example.com");
        testAdmin.setPhone("9812345693");
        testAdmin.setPasswordHash("$2a$10$Phase9AdminHashThatMustNeverLeak00000");
        testAdmin.setRole(UserRole.ADMIN);
        testAdmin.setStatus(UserStatus.ACTIVE);
        testAdmin = userRepository.saveAndFlush(testAdmin);

        testVehicle = new Vehicle();
        testVehicle.setRegistrationNumber("PH9ADMIN");
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
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private Rental buildRental(RentalStatus status, LocalDateTime pickup, LocalDateTime ret) {
        Rental rental = new Rental();
        rental.setUser(testCustomer);
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

    private PromoCode createPromo(String code, int usedCount) {
        PromoCode promo = new PromoCode();
        promo.setCode(code);
        promo.setDiscountType(DiscountType.PERCENTAGE);
        promo.setDiscountValue(new BigDecimal("10.00"));
        promo.setMinimumAmount(new BigDecimal("100.00"));
        promo.setMaximumDiscount(null);
        promo.setStartDate(LocalDate.now().minusDays(1));
        promo.setExpiryDate(LocalDate.now().plusDays(30));
        promo.setUsageLimit(10);
        promo.setUsedCount(usedCount);
        promo.setStatus("ACTIVE");
        return promoCodeRepository.saveAndFlush(promo);
    }

    private RentalStatusRequest statusRequest(String status) {
        RentalStatusRequest request = new RentalStatusRequest();
        request.setStatus(status);
        return request;
    }

    private CustomerStatusRequest customerStatusRequest(String status) {
        CustomerStatusRequest request = new CustomerStatusRequest();
        request.setStatus(status);
        return request;
    }

    private void backdateUserCreatedAt(Long userId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE users SET created_at = ? WHERE user_id = ?",
                Timestamp.valueOf(createdAt), userId);
    }

    private void backdateRentalCreatedAt(Long rentalId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE rentals SET created_at = ? WHERE rental_id = ?",
                Timestamp.valueOf(createdAt), rentalId);
    }

    private void backdatePaymentCreatedAt(Long paymentId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE payment_id = ?",
                Timestamp.valueOf(createdAt), paymentId);
    }

    private String userStatus(Long userId) {
        userRepository.flush();
        return jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE user_id = ?", String.class, userId);
    }

    private String rentalStatus(Long rentalId) {
        rentalRepository.flush();
        return jdbcTemplate.queryForObject(
                "SELECT rental_status FROM rentals WHERE rental_id = ?", String.class, rentalId);
    }

    private Integer promoUsedCount(Long promoId) {
        promoCodeRepository.flush();
        rentalRepository.flush();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT used_count FROM promo_codes WHERE promo_id = ?", Integer.class, promoId);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------
    // Customers
    // ------------------------------------------------------------------

    @Test
    void adminCustomerListReturnsOnlyCustomersNewestFirstWithoutPasswords() throws Exception {
        backdateUserCreatedAt(testCustomer.getUserId(), LocalDateTime.now().minusMinutes(3));
        backdateUserCreatedAt(secondCustomer.getUserId(), LocalDateTime.now().minusMinutes(1));

        List<CustomerResponse> customers = customerService.getAllCustomers();

        assertEquals(baselineCustomerCount + 2, customers.size());
        assertEquals(secondCustomer.getUserId(), customers.get(0).getUserId());
        assertEquals(testCustomer.getUserId(), customers.get(1).getUserId());
        assertTrue(customers.stream().allMatch(c -> "CUSTOMER".equals(c.getRole())));
        assertTrue(customers.stream().noneMatch(c -> testAdmin.getEmail().equals(c.getEmail())));

        String json = objectMapper.writeValueAsString(customers);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("$2a$10$"));
        assertFalse(json.contains("Phase9SecretHash"));
    }

    @Test
    void adminCustomerDetailReturnsWhitelistedFieldsOnly() throws Exception {
        CustomerResponse response = customerService.getCustomerDetails(testCustomer.getUserId());

        assertEquals(testCustomer.getUserId(), response.getUserId());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals("CUSTOMER", response.getRole());

        String json = objectMapper.writeValueAsString(response);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("$2a$10$"));
        assertFalse(json.contains("Phase9SecretHash"));
    }

    @Test
    void adminCustomerStatusUpdatePersistsOnRealDatabase() {
        customerService.updateCustomerStatus(
                testCustomer.getUserId(), customerStatusRequest("INACTIVE"));
        assertEquals("INACTIVE", userStatus(testCustomer.getUserId()));

        customerService.updateCustomerStatus(
                testCustomer.getUserId(), customerStatusRequest("ACTIVE"));
        assertEquals("ACTIVE", userStatus(testCustomer.getUserId()));
    }

    @Test
    void adminAccountIsNeverVisibleOrModifiableThroughCustomerEndpoints() {
        assertThrows(ResourceNotFoundException.class,
                () -> customerService.getCustomerDetails(testAdmin.getUserId()));
        assertThrows(ResourceNotFoundException.class,
                () -> customerService.updateCustomerStatus(
                        testAdmin.getUserId(), customerStatusRequest("INACTIVE")));

        // The admin account remains untouched.
        assertEquals("ACTIVE", userStatus(testAdmin.getUserId()));
        assertEquals("ADMIN", jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE user_id = ?", String.class, testAdmin.getUserId()));
    }

    @Test
    void adminCustomerUnknownIdReturnsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> customerService.getCustomerDetails(999999L));
        assertThrows(ResourceNotFoundException.class,
                () -> customerService.updateCustomerStatus(
                        999999L, customerStatusRequest("INACTIVE")));
    }

    // ------------------------------------------------------------------
    // Rentals: list + transition matrix
    // ------------------------------------------------------------------

    @Test
    void adminRentalListReturnsAllRentalsNewestFirst() {
        int baselineRentalCount = rentalService.getAllRentals().size();
        Rental older = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.PENDING, LocalDateTime.now().plusDays(5),
                        LocalDateTime.now().plusDays(7)));
        Rental newer = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.CONFIRMED, LocalDateTime.now().plusDays(8),
                        LocalDateTime.now().plusDays(10)));
        backdateRentalCreatedAt(older.getRentalId(), LocalDateTime.now().minusMinutes(2));
        backdateRentalCreatedAt(newer.getRentalId(), LocalDateTime.now().minusMinutes(1));

        List<RentalResponse> rentals = rentalService.getAllRentals();

        assertEquals(baselineRentalCount + 2, rentals.size());
        assertEquals(newer.getRentalId(), rentals.get(0).getRentalId());
        assertEquals(older.getRentalId(), rentals.get(1).getRentalId());
    }

    @Test
    void adminPendingToCancelledReleasesPromoExactlyOnceOnRealDb() {
        PromoCode promo = createPromo("PH9CANCEL", 1);
        Rental rental = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.PENDING, LocalDateTime.now().plusDays(5),
                        LocalDateTime.now().plusDays(7)));
        rental.setPromoCode(promo);
        rentalRepository.saveAndFlush(rental);

        RentalResponse response =
                rentalService.updateRentalStatusForAdmin(rental.getRentalId(),
                        statusRequest("CANCELLED"));

        assertEquals("CANCELLED", response.getRentalStatus());
        assertEquals("CANCELLED", rentalStatus(rental.getRentalId()));
        assertEquals(0, promoUsedCount(promo.getPromoId()));

        // A repeat attempt must fail without releasing again.
        assertThrows(com.vehicle.rental.exception.InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(rental.getRentalId(),
                        statusRequest("CANCELLED")));
        assertEquals(0, promoUsedCount(promo.getPromoId()));
    }

    @Test
    void adminConfirmedToCancelledKeepsConsumedPromoUsageOnRealDb() {
        PromoCode promo = createPromo("PH9KEEPPROMO", 1);
        Rental rental = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.CONFIRMED, LocalDateTime.now().plusDays(5),
                        LocalDateTime.now().plusDays(7)));
        rental.setPromoCode(promo);
        rentalRepository.saveAndFlush(rental);

        rentalService.updateRentalStatusForAdmin(rental.getRentalId(), statusRequest("CANCELLED"));

        assertEquals("CANCELLED", rentalStatus(rental.getRentalId()));
        assertEquals(1, promoUsedCount(promo.getPromoId()));
    }

    @Test
    void adminPendingToConfirmedIsRejectedOnRealDb() {
        Rental rental = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.PENDING, LocalDateTime.now().plusDays(5),
                        LocalDateTime.now().plusDays(7)));

        assertThrows(com.vehicle.rental.exception.InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(rental.getRentalId(),
                        statusRequest("CONFIRMED")));

        assertEquals("PENDING", rentalStatus(rental.getRentalId()));
    }

    @Test
    void adminConfirmedActivateThenReturnLifecycleOnRealDb() {
        Rental rental = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.CONFIRMED, LocalDateTime.now().minusHours(2),
                        LocalDateTime.now().plusDays(3)));

        // Pickup reached -> activation allowed.
        RentalResponse activated = rentalService.updateRentalStatusForAdmin(
                rental.getRentalId(), statusRequest("ACTIVE"));
        assertEquals("ACTIVE", activated.getRentalStatus());
        assertEquals("ACTIVE", rentalStatus(rental.getRentalId()));

        // Return: only way to complete, records server time, no repricing.
        RentalResponse completed = rentalService.returnRentalForAdmin(rental.getRentalId());
        assertEquals("COMPLETED", completed.getRentalStatus());
        assertNotNull(completed.getActualReturnDatetime());

        assertEquals("COMPLETED", rentalStatus(rental.getRentalId()));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT actual_return_datetime FROM rentals WHERE rental_id = ?",
                Object.class, rental.getRentalId()));

        assertEquals(0, new BigDecimal(jdbcTemplate.queryForObject(
                "SELECT total_amount FROM rentals WHERE rental_id = ?", String.class,
                rental.getRentalId())).compareTo(new BigDecimal("3312.00")));
        assertEquals(0, new BigDecimal(jdbcTemplate.queryForObject(
                "SELECT discount_amount FROM rentals WHERE rental_id = ?", String.class,
                rental.getRentalId())).compareTo(new BigDecimal("0.00")));
        assertEquals(0, new BigDecimal(jdbcTemplate.queryForObject(
                "SELECT tax_amount FROM rentals WHERE rental_id = ?", String.class,
                rental.getRentalId())).compareTo(new BigDecimal("432.00")));
        assertEquals(0, new BigDecimal(jdbcTemplate.queryForObject(
                "SELECT security_deposit FROM rentals WHERE rental_id = ?", String.class,
                rental.getRentalId())).compareTo(new BigDecimal("480.00")));
    }

    @Test
    void adminReturnOnNonActiveRentalIsRejectedOnRealDb() {
        Rental rental = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.CONFIRMED, LocalDateTime.now().plusDays(5),
                        LocalDateTime.now().plusDays(7)));

        assertThrows(com.vehicle.rental.exception.InvalidRequestException.class,
                () -> rentalService.returnRentalForAdmin(rental.getRentalId()));

        assertEquals("CONFIRMED", rentalStatus(rental.getRentalId()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT actual_return_datetime FROM rentals WHERE rental_id = ?",
                Object.class, rental.getRentalId()));
    }

    // ------------------------------------------------------------------
    // Payments
    // ------------------------------------------------------------------

    @Test
    void adminPaymentListReturnsAllPaymentsNewestFirstWithoutSecrets() throws Exception {
        // Shared-database isolation: count assertions must be relative to the
        // committed baseline, never an assumed-empty table.
        int baselinePaymentCount = paymentService.getAllPayments().size();
        Rental rental = rentalRepository.saveAndFlush(
                buildRental(RentalStatus.PENDING, LocalDateTime.now().plusDays(5),
                        LocalDateTime.now().plusDays(7)));

        Payment older = new Payment();
        older.setRental(rental);
        older.setUser(testCustomer);
        older.setAmount(rental.getTotalAmount());
        older.setPaymentMethod(PaymentMethod.RAZORPAY);
        older.setRazorpayOrderId("order_ph9_admin_1");
        older.setPaymentStatus(PaymentStatus.SUCCESS);
        older.setPaymentDate(LocalDateTime.now());
        older = paymentRepository.saveAndFlush(older);

        Payment newer = new Payment();
        newer.setRental(rental);
        newer.setUser(testCustomer);
        newer.setAmount(rental.getTotalAmount());
        newer.setPaymentMethod(PaymentMethod.RAZORPAY);
        newer.setRazorpayOrderId("order_ph9_admin_2");
        newer.setPaymentStatus(PaymentStatus.FAILED);
        newer = paymentRepository.saveAndFlush(newer);

        backdatePaymentCreatedAt(older.getPaymentId(), LocalDateTime.now().minusMinutes(2));
        backdatePaymentCreatedAt(newer.getPaymentId(), LocalDateTime.now().minusMinutes(1));

        List<PaymentResponse> payments = paymentService.getAllPayments();

        assertEquals(baselinePaymentCount + 2, payments.size());
        assertEquals(newer.getPaymentId(), payments.get(0).getPaymentId());
        assertEquals(older.getPaymentId(), payments.get(1).getPaymentId());
        assertEquals("FAILED", payments.get(0).getPaymentStatus());
        assertEquals("SUCCESS", payments.get(1).getPaymentStatus());

        String json = objectMapper.writeValueAsString(payments);
        assertFalse(json.contains("keySecret"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("password"));
    }
}
