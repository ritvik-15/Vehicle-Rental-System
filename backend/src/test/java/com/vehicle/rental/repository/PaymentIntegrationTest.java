package com.vehicle.rental.repository;

import com.vehicle.rental.dto.PaymentCreateOrderRequest;
import com.vehicle.rental.dto.PaymentCreateOrderResponse;
import com.vehicle.rental.dto.PaymentSimulateRequest;
import com.vehicle.rental.dto.PaymentSimulateResponse;
import com.vehicle.rental.dto.PaymentVerifyRequest;
import com.vehicle.rental.dto.PaymentVerifyResponse;
import com.vehicle.rental.dto.RentalCreateRequest;
import com.vehicle.rental.dto.RentalResponse;
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
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.PaymentFailedException;
import com.vehicle.rental.payment.PaymentGateway;
import com.vehicle.rental.payment.PaymentService;
import com.vehicle.rental.payment.RazorpayPaymentInfo;
import com.vehicle.rental.service.RentalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 8 real-MySQL integration: payment rows and relationships, multiple
 * attempts, atomic success transitions, rollback on mismatch, authoritative
 * failure marks, ownership, and promo usage reconciliation (§11).
 */
@SpringBootTest
@Transactional
class PaymentIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RentalRepository rentalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RentalService rentalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PaymentGateway paymentGateway;

    private User testUser;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setFullName("Phase 8 Integration Test");
        testUser.setEmail("phase8integration@example.com");
        testUser.setPhone("9812345682");
        testUser.setPasswordHash("$2a$10$Phase8IntegrationTestPasswordHash1");
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.saveAndFlush(testUser);

        testVehicle = new Vehicle();
        testVehicle.setRegistrationNumber("PVTPHASE8");
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

        when(paymentGateway.getKeyId()).thenReturn("rzp_test_public");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(user.getUserId()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
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

    private Rental pendingRental() {
        return rentalRepository.saveAndFlush(buildRental(
                RentalStatus.PENDING,
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(7)));
    }

    private Payment pendingPayment(Rental rental, String orderId) {
        Payment payment = new Payment();
        payment.setRental(rental);
        payment.setUser(rental.getUser());
        payment.setAmount(rental.getTotalAmount());
        payment.setPaymentMethod(PaymentMethod.RAZORPAY);
        payment.setRazorpayOrderId(orderId);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        return paymentRepository.saveAndFlush(payment);
    }

    private PaymentCreateOrderRequest createOrderRequest(Long rentalId) {
        return new PaymentCreateOrderRequest(rentalId);
    }

    private PaymentVerifyRequest verifyRequest(String orderId, String paymentId) {
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setRazorpayOrderId(orderId);
        request.setRazorpayPaymentId(paymentId);
        request.setRazorpaySignature("signature_for_" + paymentId);
        return request;
    }

    private void stubCaptured(String orderId, String paymentId, BigDecimal amount) {
        when(paymentGateway.verifySignature(eq(orderId), anyString(), anyString()))
                .thenReturn(true);
        when(paymentGateway.fetchPayment(paymentId))
                .thenReturn(new RazorpayPaymentInfo(orderId, "captured",
                        PaymentGateway.toPaise(amount), "INR"));
    }

    private PromoCode createPromo(String code, Integer usageLimit, int usedCount) {
        PromoCode promo = new PromoCode();
        promo.setCode(code);
        promo.setDiscountType(DiscountType.PERCENTAGE);
        promo.setDiscountValue(new BigDecimal("10.00"));
        promo.setMinimumAmount(new BigDecimal("100.00"));
        promo.setMaximumDiscount(null);
        promo.setStartDate(LocalDate.now().minusDays(1));
        promo.setExpiryDate(LocalDate.now().plusDays(30));
        promo.setUsageLimit(usageLimit);
        promo.setUsedCount(usedCount);
        promo.setStatus("ACTIVE");
        return promoCodeRepository.saveAndFlush(promo);
    }

    private RentalCreateRequest bookingRequest(LocalDateTime pickup, LocalDateTime ret) {
        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(testVehicle.getVehicleId());
        request.setPickupDateTime(pickup);
        request.setReturnDateTime(ret);
        return request;
    }

    /** Reads committed-after-flush state: flush pending changes, then query. */
    private int promoUsedCount(Long promoId) {
        promoCodeRepository.flush();
        rentalRepository.flush();
        paymentRepository.flush();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT used_count FROM promo_codes WHERE promo_id = ?",
                Integer.class, promoId);
        return count == null ? 0 : count;
    }

    private String rentalStatus(Long rentalId) {
        rentalRepository.flush();
        return jdbcTemplate.queryForObject(
                "SELECT rental_status FROM rentals WHERE rental_id = ?",
                String.class, rentalId);
    }

    /** Payment rows attached to one rental: isolation-safe (no global count). */
    private int paymentCountForRental(Long rentalId) {
        paymentRepository.flush();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE rental_id = ?",
                Integer.class, rentalId);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------
    // create-order
    // ------------------------------------------------------------------

    @Test
    void createOrderPersistsPaymentRowWithRelationships() {
        Rental rental = pendingRental();
        authenticateAs(testUser);
        when(paymentGateway.createOrder(any(), anyString())).thenReturn("order_int_1");

        PaymentCreateOrderResponse response =
                paymentService.createOrderForRental(createOrderRequest(rental.getRentalId()));

        Payment saved = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertEquals(rental.getRentalId(), saved.getRental().getRentalId());
        assertEquals(testUser.getUserId(), saved.getUser().getUserId());
        assertEquals(0, rental.getTotalAmount().compareTo(saved.getAmount()));
        assertEquals(PaymentStatus.PENDING, saved.getPaymentStatus());
        assertEquals(PaymentMethod.RAZORPAY, saved.getPaymentMethod());
        assertEquals("order_int_1", saved.getRazorpayOrderId());
        assertNull(saved.getRazorpayPaymentId());
        assertNull(saved.getPaymentDate());
        assertEquals(PaymentGateway.toPaise(rental.getTotalAmount()), response.getAmountPaise());
        assertEquals("INR", response.getCurrency());
        assertEquals("rzp_test_public", response.getKeyId());

        assertEquals("PENDING", rentalStatus(rental.getRentalId()));
    }

    @Test
    void createOrderSupportsMultipleAttemptsWithoutCreatingNewRental() {
        Rental rental = pendingRental();
        authenticateAs(testUser);
        when(paymentGateway.createOrder(any(), anyString()))
                .thenReturn("order_int_a", "order_int_b");

        PaymentCreateOrderResponse first =
                paymentService.createOrderForRental(createOrderRequest(rental.getRentalId()));
        PaymentCreateOrderResponse second =
                paymentService.createOrderForRental(createOrderRequest(rental.getRentalId()));

        assertNotEquals(first.getRazorpayOrderId(), second.getRazorpayOrderId());
        assertNotEquals(first.getPaymentId(), second.getPaymentId());
        assertEquals(2, paymentRepository.count());
        assertEquals(1, rentalRepository
                .findByUserUserIdOrderByPickupDatetimeDesc(testUser.getUserId()).size());
        assertEquals("PENDING", rentalStatus(rental.getRentalId()));
    }

    @Test
    void createOrderRejectedWhenRentalAlreadyHasSuccessPayment() {
        Rental rental = pendingRental();
        Payment paid = pendingPayment(rental, "order_done");
        paid.setPaymentStatus(PaymentStatus.SUCCESS);
        paid.setRazorpayPaymentId("pay_done");
        paid.setPaymentDate(LocalDateTime.now());
        paymentRepository.saveAndFlush(paid);
        authenticateAs(testUser);

        assertThrows(DuplicateResourceException.class,
                () -> paymentService.createOrderForRental(createOrderRequest(rental.getRentalId())));

        verify(paymentGateway, never()).createOrder(any(), anyString());
        assertEquals(1, paymentRepository.count());
    }

    // ------------------------------------------------------------------
    // verify
    // ------------------------------------------------------------------

    @Test
    void verifySuccessConfirmsRentalAndKeepsPricingIntact() {
        Rental rental = pendingRental();
        BigDecimal subtotalBefore = rental.getSubtotal();
        BigDecimal discountBefore = rental.getDiscountAmount();
        BigDecimal taxBefore = rental.getTaxAmount();
        BigDecimal depositBefore = rental.getSecurityDeposit();
        BigDecimal totalBefore = rental.getTotalAmount();

        authenticateAs(testUser);
        when(paymentGateway.createOrder(any(), anyString())).thenReturn("order_int_1");
        PaymentCreateOrderResponse order =
                paymentService.createOrderForRental(createOrderRequest(rental.getRentalId()));
        stubCaptured(order.getRazorpayOrderId(), "pay_ok", totalBefore);

        PaymentVerifyResponse response = paymentService.verifyPayment(
                verifyRequest(order.getRazorpayOrderId(), "pay_ok"));

        assertEquals("SUCCESS", response.getPaymentStatus());
        assertEquals("CONFIRMED", response.getRentalStatus());

        Payment saved = paymentRepository.findById(order.getPaymentId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, saved.getPaymentStatus());
        assertEquals("pay_ok", saved.getRazorpayPaymentId());
        assertNotNull(saved.getPaymentDate());
        assertEquals("order_int_1", saved.getRazorpayOrderId());

        Rental reloaded = rentalRepository.findById(rental.getRentalId()).orElseThrow();
        assertEquals(RentalStatus.CONFIRMED, reloaded.getRentalStatus());
        assertEquals(0, subtotalBefore.compareTo(reloaded.getSubtotal()));
        assertEquals(0, discountBefore.compareTo(reloaded.getDiscountAmount()));
        assertEquals(0, taxBefore.compareTo(reloaded.getTaxAmount()));
        assertEquals(0, depositBefore.compareTo(reloaded.getSecurityDeposit()));
        assertEquals(0, totalBefore.compareTo(reloaded.getTotalAmount()));
        assertNull(reloaded.getPromoCode());
    }

    @Test
    void duplicateVerifyRejectedWithoutChangingState() {
        Rental rental = pendingRental();
        authenticateAs(testUser);
        when(paymentGateway.createOrder(any(), anyString())).thenReturn("order_int_1");
        PaymentCreateOrderResponse order =
                paymentService.createOrderForRental(createOrderRequest(rental.getRentalId()));
        stubCaptured(order.getRazorpayOrderId(), "pay_ok", rental.getTotalAmount());
        paymentService.verifyPayment(verifyRequest(order.getRazorpayOrderId(), "pay_ok"));

        assertThrows(DuplicateResourceException.class,
                () -> paymentService.verifyPayment(
                        verifyRequest(order.getRazorpayOrderId(), "pay_ok")));

        paymentRepository.flush();
        rentalRepository.flush();
        assertEquals(1, paymentRepository.count());
        Integer successRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE rental_id = ? AND payment_status = 'SUCCESS'",
                Integer.class, rental.getRentalId());
        assertEquals(1, successRows);
        assertEquals("CONFIRMED", rentalStatus(rental.getRentalId()));
        verify(paymentGateway, times(1)).fetchPayment(anyString());
    }

    @Test
    void amountMismatchRollsBackWithNoStateChange() {
        Rental rental = pendingRental();
        authenticateAs(testUser);
        when(paymentGateway.createOrder(any(), anyString())).thenReturn("order_int_1");
        PaymentCreateOrderResponse order =
                paymentService.createOrderForRental(createOrderRequest(rental.getRentalId()));
        when(paymentGateway.verifySignature(eq(order.getRazorpayOrderId()), anyString(), anyString()))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_wrong_amount"))
                .thenReturn(new RazorpayPaymentInfo(order.getRazorpayOrderId(), "captured",
                        PaymentGateway.toPaise(rental.getTotalAmount()) + 1, "INR"));

        assertThrows(InvalidRequestException.class, () -> paymentService.verifyPayment(
                verifyRequest(order.getRazorpayOrderId(), "pay_wrong_amount")));

        String paymentStatus = jdbcTemplate.queryForObject(
                "SELECT payment_status FROM payments WHERE payment_id = ?",
                String.class, order.getPaymentId());
        Object paymentIdAtGateway = jdbcTemplate.queryForObject(
                "SELECT razorpay_payment_id FROM payments WHERE payment_id = ?",
                Object.class, order.getPaymentId());
        assertEquals("PENDING", paymentStatus);
        assertNull(paymentIdAtGateway);
        assertEquals("PENDING", rentalStatus(rental.getRentalId()));
    }

    @Test
    void verifyOwnershipEnforcedAcrossCustomers() {
        Rental rental = pendingRental();
        Payment payment = pendingPayment(rental, "order_owned");

        User stranger = new User();
        stranger.setFullName("Phase 8 Stranger");
        stranger.setEmail("phase8stranger@example.com");
        stranger.setPhone("9812345683");
        stranger.setPasswordHash("$2a$10$Phase8StrangerPasswordHashValue1");
        stranger.setRole(UserRole.CUSTOMER);
        stranger.setStatus(UserStatus.ACTIVE);
        stranger = userRepository.saveAndFlush(stranger);
        authenticateAs(stranger);

        assertThrows(ForbiddenException.class,
                () -> paymentService.verifyPayment(verifyRequest("order_owned", "pay_x")));

        verify(paymentGateway, never()).verifySignature(any(), any(), any());
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT payment_status FROM payments WHERE payment_id = ?",
                String.class, payment.getPaymentId()));
    }

    // ------------------------------------------------------------------
    // promo accounting (§11)
    // ------------------------------------------------------------------

    @Test
    void expiredUnpaidRentalReleasesPromoUsageOnAnyNextBooking() {
        PromoCode promo = createPromo("EXPIRE8", null, 1);
        Rental expired = pendingRental();
        expired.setPromoCode(promo);
        expired = rentalRepository.saveAndFlush(expired);
        jdbcTemplate.update(
                "UPDATE rentals SET created_at = ? WHERE rental_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(20)),
                expired.getRentalId());
        authenticateAs(testUser);

        // Global sweep: any next booking processes the expired rental.
        rentalService.createRental(bookingRequest(
                LocalDateTime.now().plusDays(20), LocalDateTime.now().plusDays(22)));

        assertEquals("CANCELLED", rentalStatus(expired.getRentalId()));
        assertEquals(0, promoUsedCount(promo.getPromoId()));
    }

    @Test
    void expirySweepReleasesPromoUsageOnlyOnce() {
        PromoCode promo = createPromo("ONCE8", null, 1);
        Rental expired = pendingRental();
        expired.setPromoCode(promo);
        expired = rentalRepository.saveAndFlush(expired);
        jdbcTemplate.update(
                "UPDATE rentals SET created_at = ? WHERE rental_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(20)),
                expired.getRentalId());
        authenticateAs(testUser);

        rentalService.createRental(bookingRequest(
                LocalDateTime.now().plusDays(20), LocalDateTime.now().plusDays(22)));
        // Second booking processes nothing new for that rental (already CANCELLED).
        rentalService.createRental(bookingRequest(
                LocalDateTime.now().plusDays(25), LocalDateTime.now().plusDays(27)));

        assertEquals(0, promoUsedCount(promo.getPromoId()));
    }

    @Test
    void customerCancellationOfUnpaidRentalReleasesPromoUsage() {
        PromoCode promo = createPromo("CANCEL8", null, 0);
        authenticateAs(testUser);
        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        request.setPromoCode("CANCEL8");
        RentalResponse booking = rentalService.createRental(request);
        assertEquals(1, promoUsedCount(promo.getPromoId()));

        rentalService.cancelRental(booking.getRentalId());

        assertEquals("CANCELLED", rentalStatus(booking.getRentalId()));
        assertEquals(0, promoUsedCount(promo.getPromoId()));
    }

    @Test
    void cancelledPaidConfirmedRentalKeepsConsumedPromoUsage() {
        PromoCode promo = createPromo("KEEP8", null, 0);
        authenticateAs(testUser);
        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        request.setPromoCode("KEEP8");
        RentalResponse booking = rentalService.createRental(request);
        assertEquals(1, promoUsedCount(promo.getPromoId()));

        Rental rental = rentalRepository.findById(booking.getRentalId()).orElseThrow();
        rental.setRentalStatus(RentalStatus.CONFIRMED); // simulated successful payment
        rentalRepository.saveAndFlush(rental);

        rentalService.cancelRental(booking.getRentalId());

        assertEquals("CANCELLED", rentalStatus(booking.getRentalId()));
        assertEquals(1, promoUsedCount(promo.getPromoId()));
    }

    @Test
    void successfulPaymentAndDuplicateVerifyKeepPromoUsageAtExactlyOne() {
        PromoCode promo = createPromo("PAY8", null, 0);
        authenticateAs(testUser);
        RentalCreateRequest request = bookingRequest(
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        request.setPromoCode("PAY8");
        RentalResponse booking = rentalService.createRental(request);
        assertEquals(1, promoUsedCount(promo.getPromoId()));

        when(paymentGateway.createOrder(any(), anyString())).thenReturn("order_int_1");
        PaymentCreateOrderResponse order =
                paymentService.createOrderForRental(createOrderRequest(booking.getRentalId()));
        // retry attempt must not consume again
        when(paymentGateway.createOrder(any(), anyString())).thenReturn("order_int_2");
        paymentService.createOrderForRental(createOrderRequest(booking.getRentalId()));
        assertEquals(1, promoUsedCount(promo.getPromoId()));

        stubCaptured(order.getRazorpayOrderId(), "pay_ok", booking.getTotalAmount());
        paymentService.verifyPayment(verifyRequest(order.getRazorpayOrderId(), "pay_ok"));
        assertEquals(1, promoUsedCount(promo.getPromoId()));

        assertThrows(DuplicateResourceException.class, () -> paymentService.verifyPayment(
                verifyRequest(order.getRazorpayOrderId(), "pay_ok")));
        assertEquals(1, promoUsedCount(promo.getPromoId()));

        Rental rental = rentalRepository.findById(booking.getRentalId()).orElseThrow();
        assertEquals(RentalStatus.CONFIRMED, rental.getRentalStatus());
        assertEquals(0, booking.getTotalAmount().compareTo(rental.getTotalAmount()));
    }

    // ------------------------------------------------------------------
    // authoritative failure commits (real transaction semantics)
    // ------------------------------------------------------------------

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void authoritativeFailureCommitsFailedMarkWithoutConfirmingRental() throws Throwable {
        Rental rental = rentalRepository.save(buildRental(
                RentalStatus.PENDING,
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(7)));
        Payment payment = pendingPayment(rental, "order_fail_1");
        authenticateAs(testUser);
        when(paymentGateway.verifySignature(anyString(), anyString(), anyString())).thenReturn(true);
        when(paymentGateway.fetchPayment("pay_failed"))
                .thenReturn(new RazorpayPaymentInfo("order_fail_1", "failed",
                        PaymentGateway.toPaise(rental.getTotalAmount()), "INR"));

        Throwable primaryFailure = null;
        try {
            assertThrows(PaymentFailedException.class,
                    () -> paymentService.verifyPayment(
                            verifyRequest("order_fail_1", "pay_failed")));

            // Committed outside any test transaction: the FAILED mark survived
            // (noRollbackFor) while the rental was never confirmed.
            Payment reloaded = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
            assertEquals(PaymentStatus.FAILED, reloaded.getPaymentStatus());
            Rental rentalReloaded = rentalRepository.findById(rental.getRentalId()).orElseThrow();
            assertEquals(RentalStatus.PENDING, rentalReloaded.getRentalStatus());
        } catch (Throwable failure) {
            primaryFailure = failure;
        } finally {
            SecurityContextHolder.clearContext();
        }

        // Ownership-scoped cleanup: only rows this test created (its rental and
        // its payment, both by local-ID) plus the setUp() fixtures for this test
        // method. Never a table-wide scan. FK order: payments -> rentals ->
        // vehicles -> users. Failures surface below instead of being swallowed.
        Throwable cleanupFailure = null;
        try {
            paymentRepository.deleteById(payment.getPaymentId());
            rentalRepository.deleteById(rental.getRentalId());
            vehicleRepository.delete(testVehicle);
            userRepository.delete(testUser);
        } catch (Throwable failure) {
            cleanupFailure = failure;
        }

        if (primaryFailure != null) {
            // The test's own failure stays primary; a cleanup failure rides
            // along as a suppressed exception instead of replacing it.
            if (cleanupFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
            throw primaryFailure;
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    // ------------------------------------------------------------------
    // promo usage limit stays concurrency-safe (§11.9)
    // ------------------------------------------------------------------

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void promoUsageLimitEnforcedUnderConcurrentBookings() throws Throwable {
        Vehicle secondVehicle = new Vehicle();
        secondVehicle.setRegistrationNumber("PVTCONC8");
        secondVehicle.setBrand("Toyota");
        secondVehicle.setModel("Etios");
        secondVehicle.setVehicleType(VehicleType.CAR);
        secondVehicle.setDailyRate(new BigDecimal("1000.00"));
        secondVehicle.setSeatingCapacity(4);
        secondVehicle.setFuelType(FuelType.PETROL);
        secondVehicle.setTransmission(TransmissionType.MANUAL);
        secondVehicle.setLocation("Pune");
        secondVehicle.setStatus(VehicleStatus.AVAILABLE);
        secondVehicle = vehicleRepository.save(secondVehicle);

        PromoCode promo = createPromo("CONC8", 1, 0);

        RentalCreateRequest requestA = bookingRequest(
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));
        requestA.setPromoCode("CONC8");
        RentalCreateRequest requestB = new RentalCreateRequest();
        requestB.setVehicleId(secondVehicle.getVehicleId());
        requestB.setPickupDateTime(LocalDateTime.now().plusDays(5));
        requestB.setReturnDateTime(LocalDateTime.now().plusDays(7));
        requestB.setPromoCode("CONC8");

        int threadCount = 2;
        java.util.concurrent.CountDownLatch startLatch =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successes =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger rejected =
                new java.util.concurrent.atomic.AtomicInteger();
        List<Exception> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        List<Thread> threads = new java.util.ArrayList<>();

        for (RentalCreateRequest request : List.of(requestA, requestB)) {
            Thread thread = new Thread(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    String.valueOf(testUser.getUserId()),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
                    startLatch.await();
                    rentalService.createRental(request);
                    successes.incrementAndGet();
                } catch (InvalidRequestException e) {
                    rejected.incrementAndGet();
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

        Throwable primaryFailure = null;
        try {
            assertEquals(1, successes.get());
            assertEquals(1, rejected.get());
            assertTrue(errors.isEmpty(), "Unexpected thread errors: " + errors);
            assertEquals(1, promoUsedCount(promo.getPromoId()));
        } catch (Throwable failure) {
            primaryFailure = failure;
        }

        // Ownership-scoped cleanup: secondVehicle is created by this test and
        // testVehicle by this test's setUp(), so under sequential test execution
        // every rental found under either vehicle was created during this test.
        // The promo and both fixtures are likewise this test's own rows. Child
        // rows first: rentals -> promo -> vehicles -> users.
        Throwable cleanupFailure = null;
        try {
            rentalRepository.deleteAll(rentalRepository
                    .findByVehicleVehicleIdOrderByPickupDatetimeAsc(secondVehicle.getVehicleId()));
            rentalRepository.deleteAll(rentalRepository
                    .findByVehicleVehicleIdOrderByPickupDatetimeAsc(testVehicle.getVehicleId()));
            promoCodeRepository.delete(promo);
            vehicleRepository.delete(secondVehicle);
            vehicleRepository.delete(testVehicle);
            userRepository.delete(testUser);
        } catch (Throwable failure) {
            cleanupFailure = failure;
        }

        if (primaryFailure != null) {
            // The test's own failure stays primary; a cleanup failure rides
            // along as a suppressed exception instead of replacing it.
            if (cleanupFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
            throw primaryFailure;
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    // ------------------------------------------------------------------
    // demo (simulated) payment — backend-controlled, zero gateway calls
    // ------------------------------------------------------------------

    @Test
    void simulatePaymentRecordsDemoSuccessAndConfirmsRentalWithoutGatewayCalls() {
        Rental rental = pendingRental();
        authenticateAs(testUser);

        PaymentSimulateResponse response =
                paymentService.simulatePayment(new PaymentSimulateRequest(rental.getRentalId()));

        assertEquals("SUCCESS", response.getPaymentStatus());
        assertEquals("CONFIRMED", response.getRentalStatus());
        assertTrue(response.getDemoReference().startsWith("DEMO-"));
        assertEquals(0, rental.getTotalAmount().compareTo(response.getAmount()));

        Payment saved = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, saved.getPaymentStatus());
        assertTrue(saved.getRazorpayOrderId().startsWith("DEMO-ORDER-"),
                "Demo order IDs must be clearly identifiable, got: " + saved.getRazorpayOrderId());
        assertTrue(saved.getRazorpayPaymentId().startsWith("DEMO-PAY-"),
                "Demo payment IDs must be clearly identifiable, got: " + saved.getRazorpayPaymentId());
        assertEquals(0, rental.getTotalAmount().compareTo(saved.getAmount()));
        assertNotNull(saved.getPaymentDate());
        assertEquals("CONFIRMED", rentalStatus(rental.getRentalId()));

        // The demo flow must never create real gateway orders or process payments.
        verify(paymentGateway, never()).createOrder(any(), anyString());
        verify(paymentGateway, never()).fetchPayment(anyString());
        verify(paymentGateway, never()).fetchOrder(anyString());
        verify(paymentGateway, never()).verifySignature(any(), any(), any());
    }

    @Test
    void simulatePaymentRejectedForExpiredRentalLeavesBothRowsUntouched() {
        Rental rental = pendingRental();
        // created_at is @Column(updatable=false): set the database row with SQL
        // and align the managed persistence-context copy with the setter, since
        // the service's locking read resolves to that managed instance.
        jdbcTemplate.update("UPDATE rentals SET created_at = ? WHERE rental_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(20)), rental.getRentalId());
        rental.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        authenticateAs(testUser);

        assertThrows(InvalidRequestException.class, () -> paymentService.simulatePayment(
                new PaymentSimulateRequest(rental.getRentalId())));

        assertEquals(0, paymentCountForRental(rental.getRentalId()));
        assertEquals("PENDING", rentalStatus(rental.getRentalId()));
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    @Test
    void simulatePaymentRejectedForNonCustomerCaller() {
        Rental rental = pendingRental();
        User admin = new User();
        admin.setUserId(9999L);
        admin.setRole(UserRole.ADMIN);
        authenticateAs(admin);

        assertThrows(ForbiddenException.class, () -> paymentService.simulatePayment(
                new PaymentSimulateRequest(rental.getRentalId())));

        assertEquals(0, paymentCountForRental(rental.getRentalId()));
        assertEquals("PENDING", rentalStatus(rental.getRentalId()));
    }

    @Test
    void simulatePaymentRejectedForRentalOwnedByAnotherCustomer() {
        Rental rental = pendingRental();
        User stranger = new User();
        stranger.setFullName("Phase 8 Simulate Stranger");
        stranger.setEmail("phase8simulatestranger@example.com");
        stranger.setPhone("9812345685");
        stranger.setPasswordHash("$2a$10$Phase8SimulateStrangerPassword1");
        stranger.setRole(UserRole.CUSTOMER);
        stranger.setStatus(UserStatus.ACTIVE);
        stranger = userRepository.saveAndFlush(stranger);
        authenticateAs(stranger);

        assertThrows(ForbiddenException.class, () -> paymentService.simulatePayment(
                new PaymentSimulateRequest(rental.getRentalId())));

        assertEquals(0, paymentCountForRental(rental.getRentalId()));
        assertEquals("PENDING", rentalStatus(rental.getRentalId()));
        // stranger row rolls back with this test's transaction
    }

    @Test
    void simulatePaymentRejectedWhenRentalAlreadyHasSuccessPayment() {
        Rental rental = pendingRental();
        Payment paid = pendingPayment(rental, "order_done");
        paid.setPaymentStatus(PaymentStatus.SUCCESS);
        paid.setRazorpayPaymentId("pay_done");
        paid.setPaymentDate(LocalDateTime.now());
        paymentRepository.saveAndFlush(paid);
        authenticateAs(testUser);

        assertThrows(DuplicateResourceException.class, () -> paymentService.simulatePayment(
                new PaymentSimulateRequest(rental.getRentalId())));

        assertEquals(1, paymentCountForRental(rental.getRentalId()));
        verify(paymentGateway, never()).fetchOrder(anyString());
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    // ------------------------------------------------------------------
    // duplicate demo payments under real concurrency (two live threads)
    // ------------------------------------------------------------------

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSimulatePaymentsCreateExactlyOneSuccessPayment() throws Throwable {
        Rental rental = rentalRepository.save(buildRental(
                RentalStatus.PENDING,
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(7)));

        java.util.concurrent.CountDownLatch startLatch =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successes =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger rejected =
                new java.util.concurrent.atomic.AtomicInteger();
        List<Exception> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        List<Thread> threads = new java.util.ArrayList<>();

        for (int i = 0; i < 2; i++) {
            Thread thread = new Thread(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    String.valueOf(testUser.getUserId()),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
                    startLatch.await();
                    paymentService.simulatePayment(
                            new PaymentSimulateRequest(rental.getRentalId()));
                    successes.incrementAndGet();
                } catch (DuplicateResourceException e) {
                    rejected.incrementAndGet();
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

        Throwable primaryFailure = null;
        try {
            assertEquals(1, successes.get());
            assertEquals(1, rejected.get());
            assertTrue(errors.isEmpty(), "Unexpected thread errors: " + errors);
            assertEquals(1, paymentCountForRental(rental.getRentalId()));
            Long successCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE rental_id = ? AND payment_status = 'SUCCESS'",
                    Long.class, rental.getRentalId());
            assertEquals(1L, successCount);
            assertEquals("CONFIRMED", rentalStatus(rental.getRentalId()));
            verify(paymentGateway, never()).createOrder(any(), anyString());
        } catch (Throwable failure) {
            primaryFailure = failure;
        }

        // Ownership-scoped cleanup: the rental was created by this test, every
        // payment row attached to that rental was created by this test's worker
        // threads, and the user/vehicle fixtures come from this test's setUp().
        // Child rows first: payments -> rentals -> vehicles -> users.
        Throwable cleanupFailure = null;
        try {
            jdbcTemplate.update("DELETE FROM payments WHERE rental_id = ?", rental.getRentalId());
            rentalRepository.deleteById(rental.getRentalId());
            vehicleRepository.delete(testVehicle);
            userRepository.delete(testUser);
        } catch (Throwable failure) {
            cleanupFailure = failure;
        }

        if (primaryFailure != null) {
            // The test's own failure stays primary; a cleanup failure rides
            // along as a suppressed exception instead of replacing it.
            if (cleanupFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
            throw primaryFailure;
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }
}
