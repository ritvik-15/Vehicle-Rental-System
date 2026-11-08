package com.vehicle.rental.repository;

import com.vehicle.rental.dto.PaymentCreateOrderRequest;
import com.vehicle.rental.dto.PaymentCreateOrderResponse;
import com.vehicle.rental.dto.PaymentSimulateRequest;
import com.vehicle.rental.dto.PaymentVerifyRequest;
import com.vehicle.rental.dto.RentalCreateRequest;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.entity.PromoCode;
import com.vehicle.rental.entity.Rental;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.DiscountType;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.PaymentStatus;
import com.vehicle.rental.enums.RentalStatus;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.InvalidRequestException;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Phase 12B concurrency integration tests (real MySQL required).
 *
 * <p>REQUIRES THE ISOLATED TEST DATABASE (e.g. vehicle_rental_test).
 * Never run against vehicle_rental_db. Both tests run with committed
 * transactions (Propagation.NOT_SUPPORTED) so the pessimistic-lock
 * semantics under test are real, and clean up only the rows they
 * created (ownership-scoped, FK order, failures never swallowed).</p>
 *
 * <ul>
 *   <li>D1: customer cancellation serializes with payment verification
 *       via the rental-row lock, so a stale PENDING read can never
 *       overwrite a concurrently confirmed payment and release promo
 *       usage a paid booking consumed.</li>
 *   <li>P1: concurrent demo simulations of different rentals receive
 *       distinct demo order/payment identifiers (both columns are
 *       UNIQUE in the schema).</li>
 * </ul>
 */
@SpringBootTest
class CancelPaymentConcurrencyIntegrationTest {

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

    /** Unique per test-method instance, so fixtures never collide. */
    private final String unique = String.valueOf(Math.abs(System.nanoTime() % 100000000L));

    private User testUser;
    private Vehicle testVehicle;
    private Vehicle secondVehicle;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setFullName("Phase 12B Concurrency Test");
        testUser.setEmail("phase12b" + unique + "@example.com");
        testUser.setPhone("98" + unique);
        testUser.setPasswordHash("$2a$10$Phase12BConcurrencyTestHash1");
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.saveAndFlush(testUser);

        testVehicle = new Vehicle();
        testVehicle.setRegistrationNumber("PVPH12B" + unique);
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

        secondVehicle = new Vehicle();
        secondVehicle.setRegistrationNumber("PVPH12B2" + unique);
        secondVehicle.setBrand("Toyota");
        secondVehicle.setModel("Etios");
        secondVehicle.setVehicleType(VehicleType.CAR);
        secondVehicle.setDailyRate(new BigDecimal("1000.00"));
        secondVehicle.setSeatingCapacity(4);
        secondVehicle.setFuelType(FuelType.PETROL);
        secondVehicle.setTransmission(TransmissionType.MANUAL);
        secondVehicle.setLocation("Pune");
        secondVehicle.setStatus(VehicleStatus.AVAILABLE);
        secondVehicle = vehicleRepository.saveAndFlush(secondVehicle);

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

    private PromoCode createPromo(String code, Integer usageLimit, int usedCount) {
        PromoCode promo = new PromoCode();
        promo.setCode(code);
        promo.setDiscountType(DiscountType.PERCENTAGE);
        promo.setDiscountValue(new BigDecimal("10.00"));
        promo.setMinimumAmount(new BigDecimal("100.00"));
        promo.setMaximumDiscount(null);
        promo.setStartDate(java.time.LocalDate.now().minusDays(1));
        promo.setExpiryDate(java.time.LocalDate.now().plusDays(30));
        promo.setUsageLimit(usageLimit);
        promo.setUsedCount(usedCount);
        promo.setStatus("ACTIVE");
        return promoCodeRepository.saveAndFlush(promo);
    }

    private Rental buildPendingRental(Vehicle vehicle, PromoCode promo) {
        Rental rental = new Rental();
        rental.setUser(testUser);
        rental.setVehicle(vehicle);
        rental.setPickupDatetime(LocalDateTime.now().plusDays(5));
        rental.setReturnDatetime(LocalDateTime.now().plusDays(7));
        rental.setNumberOfDays(2);
        rental.setDailyRate(new BigDecimal("1200.00"));
        rental.setSubtotal(new BigDecimal("2400.00"));
        rental.setDiscountAmount(new BigDecimal("0.00"));
        rental.setTaxAmount(new BigDecimal("432.00"));
        rental.setSecurityDeposit(new BigDecimal("480.00"));
        rental.setTotalAmount(new BigDecimal("3312.00"));
        rental.setRentalStatus(RentalStatus.PENDING);
        rental.setPromoCode(promo);
        return rental;
    }

    /** Reads committed state: flush pending changes, then query. */
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

    private String paymentStatus(Long paymentId) {
        paymentRepository.flush();
        return jdbcTemplate.queryForObject(
                "SELECT payment_status FROM payments WHERE payment_id = ?",
                String.class, paymentId);
    }

    private int paymentCountForRental(Long rentalId) {
        paymentRepository.flush();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE rental_id = ?",
                Integer.class, rentalId);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------
    // D1: cancel vs payment verification
    // ------------------------------------------------------------------

    /**
     * A customer cancellation and a payment verification racing on the
     * same PENDING rental must serialize on the rental-row lock. The
     * corrupt outcome the lock prevents — a SUCCESS payment with a
     * CANCELLED rental whose promo usage was released — must never
     * occur, and promo usage must stay consistent with the payment.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void customerCancelAndPaymentVerificationCannotCorruptRentalOrPromoState() throws Throwable {
        PromoCode promo = createPromo("PH12BC" + unique, 5, 0);

        // Booking through the service so the promo usage is reserved
        // exactly as in production (usedCount 0 -> 1).
        RentalCreateRequest booking = new RentalCreateRequest();
        booking.setVehicleId(testVehicle.getVehicleId());
        booking.setPickupDateTime(LocalDateTime.now().plusDays(5));
        booking.setReturnDateTime(LocalDateTime.now().plusDays(7));
        booking.setPromoCode(promo.getCode());
        authenticateAs(testUser);
        RentalResponse created = rentalService.createRental(booking);
        Rental rental = rentalRepository.findById(created.getRentalId()).orElseThrow();

        int usedCountAfterBooking = promoUsedCount(promo.getPromoId());
        assertEquals(1, usedCountAfterBooking);

        // A PENDING payment attempt for the rental.
        when(paymentGateway.createOrder(any(), anyString())).thenReturn("order_ph12b_1");
        PaymentCreateOrderResponse order = paymentService.createOrderForRental(
                new PaymentCreateOrderRequest(rental.getRentalId()));

        // The gateway reports the payment as captured at Razorpay.
        when(paymentGateway.verifySignature(eq("order_ph12b_1"), anyString(), anyString()))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_ph12b_1"))
                .thenReturn(new RazorpayPaymentInfo("order_ph12b_1", "captured",
                        PaymentGateway.toPaise(rental.getTotalAmount()), "INR"));

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<String> cancelOutcome = new AtomicReference<>();
        AtomicReference<String> verifyOutcome = new AtomicReference<>();
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        Thread cancelThread = new Thread(() -> {
            try {
                authenticateAs(testUser);
                startLatch.await();
                rentalService.cancelRental(rental.getRentalId());
                cancelOutcome.set("CANCELLED");
            } catch (InvalidRequestException | DuplicateResourceException e) {
                cancelOutcome.set("REJECTED: " + e.getMessage());
            } catch (Exception e) {
                errors.add(e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        Thread verifyThread = new Thread(() -> {
            try {
                authenticateAs(testUser);
                startLatch.await();
                PaymentVerifyRequest request = new PaymentVerifyRequest();
                request.setRazorpayOrderId("order_ph12b_1");
                request.setRazorpayPaymentId("pay_ph12b_1");
                request.setRazorpaySignature("signature_ph12b_1");
                paymentService.verifyPayment(request);
                verifyOutcome.set("SUCCESS");
            } catch (InvalidRequestException | DuplicateResourceException e) {
                // PaymentFailedException extends InvalidRequestException,
                // so it is covered here as well.
                verifyOutcome.set("REJECTED: " + e.getMessage());
            } catch (Exception e) {
                errors.add(e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        cancelThread.start();
        verifyThread.start();
        startLatch.countDown();
        cancelThread.join(30000);
        verifyThread.join(30000);

        Throwable primaryFailure = null;
        try {
            assertTrue(errors.isEmpty(), "Unexpected thread errors: " + errors);
            assertNotNull(cancelOutcome.get(), "Cancel thread produced no outcome");
            assertNotNull(verifyOutcome.get(), "Verify thread produced no outcome");

            String finalRentalStatus = rentalStatus(rental.getRentalId());
            String finalPaymentStatus = paymentStatus(order.getPaymentId());
            int finalUsedCount = promoUsedCount(promo.getPromoId());
            int payments = paymentCountForRental(rental.getRentalId());

            // Exactly one payment attempt ever existed for this rental.
            assertEquals(1, payments);

            // Case A (cancel won the lock first): the rental is
            // CANCELLED, the payment attempt stays PENDING, and the
            // promo usage reserved by the unpaid booking is released.
            // Case B (verify won the lock first): the payment is
            // SUCCESS; the customer's cancellation of the now-paid
            // booking is legitimate, but the promo usage a paid
            // booking consumed is retained.
            // The corrupt combination — SUCCESS payment with released
            // promo usage, or a PENDING payment with a CONFIRMED
            // rental — must never occur.
            assertEquals("CANCELLED", finalRentalStatus);
            if ("SUCCESS".equals(finalPaymentStatus)) {
                assertEquals(usedCountAfterBooking, finalUsedCount,
                        "Promo usage released despite a successful payment");
            } else {
                assertEquals("PENDING", finalPaymentStatus);
                assertEquals(0, finalUsedCount,
                        "Unpaid cancelled booking must release the promo usage");
            }
        } catch (Throwable failure) {
            primaryFailure = failure;
        }

        // Ownership-scoped cleanup: only rows this test created, FK
        // order: payments -> rentals -> promo -> vehicles -> users.
        Throwable cleanupFailure = null;
        try {
            paymentRepository.deleteById(order.getPaymentId());
            rentalRepository.deleteById(rental.getRentalId());
            promoCodeRepository.delete(promo);
            vehicleRepository.delete(testVehicle);
            vehicleRepository.delete(secondVehicle);
            userRepository.delete(testUser);
        } catch (Throwable failure) {
            cleanupFailure = failure;
        }

        if (primaryFailure != null) {
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
    // P1: concurrent demo simulations of different rentals
    // ------------------------------------------------------------------

    /**
     * Two demo simulations for two different rentals started at the
     * same instant must both succeed with distinct demo order and
     * payment identifiers — the UNIQUE columns can never collide,
     * because the identifiers embed the rental ID and a random
     * suffix (before the fix they used only the millisecond clock).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDemoSimulationsOfDifferentRentalsReceiveDistinctIdentifiers() throws Throwable {
        Rental first = rentalRepository.saveAndFlush(
                buildPendingRental(testVehicle, null));
        Rental second = rentalRepository.saveAndFlush(
                buildPendingRental(secondVehicle, null));

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<String> firstStatus = new AtomicReference<>();
        AtomicReference<String> secondStatus = new AtomicReference<>();
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        Thread firstThread = new Thread(() -> {
            try {
                authenticateAs(testUser);
                startLatch.await();
                paymentService.simulatePayment(
                        new PaymentSimulateRequest(first.getRentalId()));
                firstStatus.set("SUCCESS");
            } catch (Exception e) {
                errors.add(e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        Thread secondThread = new Thread(() -> {
            try {
                authenticateAs(testUser);
                startLatch.await();
                paymentService.simulatePayment(
                        new PaymentSimulateRequest(second.getRentalId()));
                secondStatus.set("SUCCESS");
            } catch (Exception e) {
                errors.add(e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        firstThread.start();
        secondThread.start();
        startLatch.countDown();
        firstThread.join(30000);
        secondThread.join(30000);

        Throwable primaryFailure = null;
        try {
            assertTrue(errors.isEmpty(), "Unexpected thread errors: " + errors);
            assertEquals("SUCCESS", firstStatus.get());
            assertEquals("SUCCESS", secondStatus.get());

            assertEquals("CONFIRMED", rentalStatus(first.getRentalId()));
            assertEquals("CONFIRMED", rentalStatus(second.getRentalId()));

            List<String> firstOrderIds = jdbcTemplate.queryForList(
                    "SELECT razorpay_order_id FROM payments WHERE rental_id = ?",
                    String.class, first.getRentalId());
            List<String> secondOrderIds = jdbcTemplate.queryForList(
                    "SELECT razorpay_order_id FROM payments WHERE rental_id = ?",
                    String.class, second.getRentalId());
            List<String> firstPaymentIds = jdbcTemplate.queryForList(
                    "SELECT razorpay_payment_id FROM payments WHERE rental_id = ?",
                    String.class, first.getRentalId());
            List<String> secondPaymentIds = jdbcTemplate.queryForList(
                    "SELECT razorpay_payment_id FROM payments WHERE rental_id = ?",
                    String.class, second.getRentalId());

            assertEquals(1, firstOrderIds.size());
            assertEquals(1, secondOrderIds.size());
            assertNotEquals(firstOrderIds.get(0), secondOrderIds.get(0),
                    "Demo order identifiers collided across rentals");
            assertNotEquals(firstPaymentIds.get(0), secondPaymentIds.get(0),
                    "Demo payment identifiers collided across rentals");
            assertTrue(firstOrderIds.get(0).startsWith("DEMO-ORDER-" + first.getRentalId() + "-"));
            assertTrue(secondOrderIds.get(0).startsWith("DEMO-ORDER-" + second.getRentalId() + "-"));
        } catch (Throwable failure) {
            primaryFailure = failure;
        }

        // Ownership-scoped cleanup: only rows this test created
        // (two rentals and their demo payments), FK order:
        // payments -> rentals -> vehicles -> users.
        Throwable cleanupFailure = null;
        try {
            paymentRepository.deleteAll(paymentRepository
                    .findByRentalRentalIdAndPaymentStatus(
                            first.getRentalId(), PaymentStatus.SUCCESS));
            paymentRepository.deleteAll(paymentRepository
                    .findByRentalRentalIdAndPaymentStatus(
                            second.getRentalId(), PaymentStatus.SUCCESS));
            rentalRepository.deleteById(first.getRentalId());
            rentalRepository.deleteById(second.getRentalId());
            vehicleRepository.delete(testVehicle);
            vehicleRepository.delete(secondVehicle);
            userRepository.delete(testUser);
        } catch (Throwable failure) {
            cleanupFailure = failure;
        }

        if (primaryFailure != null) {
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
