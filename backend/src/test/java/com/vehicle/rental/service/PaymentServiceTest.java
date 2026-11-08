package com.vehicle.rental.service;

import com.vehicle.rental.dto.PaymentCreateOrderRequest;
import com.vehicle.rental.dto.PaymentCreateOrderResponse;
import com.vehicle.rental.dto.PaymentSimulateRequest;
import com.vehicle.rental.dto.PaymentSimulateResponse;
import com.vehicle.rental.dto.PaymentVerifyRequest;
import com.vehicle.rental.dto.PaymentVerifyResponse;
import com.vehicle.rental.entity.Payment;
import com.vehicle.rental.entity.Rental;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.PaymentMethod;
import com.vehicle.rental.enums.PaymentStatus;
import com.vehicle.rental.enums.RentalStatus;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.PaymentFailedException;
import com.vehicle.rental.exception.PaymentGatewayException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.payment.PaymentGateway;
import com.vehicle.rental.payment.PaymentService;
import com.vehicle.rental.payment.RazorpayPaymentInfo;
import com.vehicle.rental.repository.PaymentRepository;
import com.vehicle.rental.repository.RentalRepository;
import com.vehicle.rental.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RentalRepository rentalRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentService paymentService;

    private static final BigDecimal TOTAL = new BigDecimal("3312.00");
    private static final long TOTAL_PAISE = 331200L;

    private User customer() {
        User user = new User();
        user.setUserId(1L);
        user.setRole(UserRole.CUSTOMER);
        return user;
    }

    private User otherCustomer() {
        User user = new User();
        user.setUserId(2L);
        user.setRole(UserRole.CUSTOMER);
        return user;
    }

    private User admin() {
        User user = new User();
        user.setUserId(3L);
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private Rental rental() {
        Rental rental = new Rental();
        rental.setRentalId(10L);
        rental.setUser(customer());
        rental.setPickupDatetime(LocalDateTime.now().plusDays(5));
        rental.setReturnDatetime(LocalDateTime.now().plusDays(7));
        rental.setNumberOfDays(2);
        rental.setDailyRate(new BigDecimal("1200.00"));
        rental.setSubtotal(new BigDecimal("2400.00"));
        rental.setDiscountAmount(new BigDecimal("0.00"));
        rental.setTaxAmount(new BigDecimal("432.00"));
        rental.setSecurityDeposit(new BigDecimal("480.00"));
        rental.setTotalAmount(TOTAL);
        rental.setRentalStatus(RentalStatus.PENDING);
        rental.setCreatedAt(LocalDateTime.now());
        return rental;
    }

    private Payment paymentFor(Rental rental) {
        Payment payment = new Payment();
        payment.setPaymentId(99L);
        payment.setRental(rental);
        payment.setUser(rental.getUser());
        payment.setAmount(rental.getTotalAmount());
        payment.setPaymentMethod(PaymentMethod.RAZORPAY);
        payment.setRazorpayOrderId("order_rzp1");
        payment.setPaymentStatus(PaymentStatus.PENDING);
        return payment;
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(user.getUserId()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    private PaymentCreateOrderRequest createRequest() {
        return new PaymentCreateOrderRequest(10L);
    }

    private PaymentVerifyRequest verifyRequest() {
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setRazorpayOrderId("order_rzp1");
        request.setRazorpayPaymentId("pay_1");
        request.setRazorpaySignature("valid_signature");
        return request;
    }

    private void stubCreateOrderHappyPath(Rental rental) {
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                10L, PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentGateway.createOrder(any(BigDecimal.class), anyString()))
                .thenReturn("order_rzp1");
        when(paymentGateway.getKeyId()).thenReturn("rzp_test_public");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentId(99L);
            return payment;
        });
    }

    private void stubVerifyHappyPath(Payment payment, Rental rental) {
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_1"))
                .thenReturn(new RazorpayPaymentInfo("order_rzp1", "captured", TOTAL_PAISE, "INR"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rentalRepository.save(any(Rental.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // create-order
    // ------------------------------------------------------------------

    @Test
    void createOrderReturnsSafeFieldsAndPersistsPendingPayment() {
        Rental rental = rental();
        authenticateAs(customer());
        stubCreateOrderHappyPath(rental);

        PaymentCreateOrderResponse response = paymentService.createOrderForRental(createRequest());

        assertEquals(99L, response.getPaymentId());
        assertEquals(10L, response.getRentalId());
        assertEquals("order_rzp1", response.getRazorpayOrderId());
        assertEquals(TOTAL_PAISE, response.getAmountPaise());
        assertEquals("INR", response.getCurrency());
        assertEquals("rzp_test_public", response.getKeyId());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertEquals(PaymentStatus.PENDING, saved.getPaymentStatus());
        assertEquals(PaymentMethod.RAZORPAY, saved.getPaymentMethod());
        assertEquals("order_rzp1", saved.getRazorpayOrderId());
        assertNull(saved.getRazorpayPaymentId());
        assertNull(saved.getPaymentDate());
        assertEquals(TOTAL, saved.getAmount());
        assertEquals(10L, saved.getRental().getRentalId());
        assertEquals(1L, saved.getUser().getUserId());
    }

    @Test
    void createOrderUsesDatabaseAmountNotAnyClientValue() {
        Rental rental = rental();
        authenticateAs(customer());
        stubCreateOrderHappyPath(rental);

        paymentService.createOrderForRental(createRequest());

        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentGateway).createOrder(amountCaptor.capture(), anyString());
        assertEquals(0, TOTAL.compareTo(amountCaptor.getValue()));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(0, TOTAL.compareTo(paymentCaptor.getValue().getAmount()));
    }

    @Test
    void createOrderWithoutAuthenticationRejected() {
        SecurityContextHolder.clearContext();

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        assertEquals("Authentication required", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrderForNonCustomerForbidden() {
        // Role gate runs from the JWT authorities before any database access.
        authenticateAs(admin());

        assertThrows(ForbiddenException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        verify(rentalRepository, never()).findByIdWithPessimisticLock(any());
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    @Test
    void createOrderRejectsStaleJwtCustomerWhoseDatabaseRoleIsAdmin() {
        // JWT says CUSTOMER (login-time authority) but the database role has
        // since changed: the post-lock database role check must still reject.
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin()));

        assertThrows(ForbiddenException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        verify(paymentGateway, never()).createOrder(any(), anyString());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrderForUnknownRentalNotFound() {
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(777L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.createOrderForRental(new PaymentCreateOrderRequest(777L)));
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    @Test
    void createOrderForAnotherCustomersRentalForbidden() {
        Rental foreign = rental();
        foreign.setUser(otherCustomer());
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(foreign));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));

        assertThrows(ForbiddenException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    @Test
    void createOrderForCancelledRentalRejected() {
        Rental rental = rental();
        rental.setRentalStatus(RentalStatus.CANCELLED);
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        assertEquals("Cancelled rental cannot be paid", ex.getMessage());
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    @Test
    void createOrderForAlreadyPaidRentalRejected() {
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                10L, PaymentStatus.SUCCESS)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        verify(paymentGateway, never()).createOrder(any(), anyString());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrderForConfirmedRentalRejected() {
        Rental rental = rental();
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));

        assertThrows(DuplicateResourceException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    @Test
    void createOrderForExpiredPendingRentalRejected() {
        Rental rental = rental();
        rental.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        assertEquals("Rental payment session has expired", ex.getMessage());
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    @Test
    void createOrderWithNonPositiveAmountRejected() {
        Rental rental = rental();
        rental.setTotalAmount(BigDecimal.ZERO);
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));

        assertThrows(InvalidRequestException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        verify(paymentGateway, never()).createOrder(any(), anyString());
    }

    @Test
    void createOrderGatewayFailurePropagatesAndPersistsNothing() {
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                10L, PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentGateway.createOrder(any(BigDecimal.class), anyString()))
                .thenThrow(new PaymentGatewayException("Payment gateway is not configured"));

        assertThrows(PaymentGatewayException.class,
                () -> paymentService.createOrderForRental(createRequest()));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrderAllowedAgainAfterFailedOrPendedAttempts() {
        Rental rental = rental();
        authenticateAs(customer());
        stubCreateOrderHappyPath(rental);

        // Only a SUCCESS payment blocks a new attempt; FAILED/CANCELLED/PENDING
        // attempts (exists check returns false) must not.
        PaymentCreateOrderResponse response = paymentService.createOrderForRental(createRequest());

        assertEquals("order_rzp1", response.getRazorpayOrderId());
        verify(paymentRepository).save(any(Payment.class));
    }

    // ------------------------------------------------------------------
    // verify
    // ------------------------------------------------------------------

    @Test
    void verifySuccessMarksPaymentConfirmedAndRentalConfirmed() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        stubVerifyHappyPath(payment, rental);

        PaymentVerifyResponse response = paymentService.verifyPayment(verifyRequest());

        assertEquals(99L, response.getPaymentId());
        assertEquals(10L, response.getRentalId());
        assertEquals("order_rzp1", response.getRazorpayOrderId());
        assertEquals("pay_1", response.getRazorpayPaymentId());
        assertEquals(0, TOTAL.compareTo(response.getAmount()));
        assertEquals("SUCCESS", response.getPaymentStatus());
        assertEquals("CONFIRMED", response.getRentalStatus());

        assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
        assertEquals("pay_1", payment.getRazorpayPaymentId());
        assertNotNull(payment.getPaymentDate());
        assertEquals(RentalStatus.CONFIRMED, rental.getRentalStatus());

        verify(paymentRepository).save(payment);
        verify(rentalRepository).save(rental);
    }

    @Test
    void verifyInvalidSignatureRejectedWithoutFetchingOrWriting() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(false);

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        assertEquals("Payment signature verification failed", ex.getMessage());

        verify(paymentGateway, never()).fetchPayment(anyString());
        verify(paymentRepository, never()).save(any());
        verify(rentalRepository, never()).save(any());
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertEquals(RentalStatus.PENDING, rental.getRentalStatus());
    }

    @Test
    void verifyUnknownOrderIdNotFound() {
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_missing"))
                .thenReturn(Optional.empty());

        PaymentVerifyRequest request = verifyRequest();
        request.setRazorpayOrderId("order_missing");

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.verifyPayment(request));
        verify(paymentGateway, never()).verifySignature(any(), any(), any());
    }

    @Test
    void verifyForeignPaymentIdForDifferentOrderRejected() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_1"))
                .thenReturn(new RazorpayPaymentInfo("order_other", "captured", TOTAL_PAISE, "INR"));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        assertEquals("Razorpay payment does not match the order", ex.getMessage());
        verify(paymentRepository, never()).save(any());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void verifyAnotherCustomersPaymentForbidden() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        payment.setUser(otherCustomer());
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        assertThrows(ForbiddenException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        verify(paymentGateway, never()).verifySignature(any(), any(), any());
    }

    @Test
    void verifyByNonCustomerForbidden() {
        // Wrong role is rejected from the JWT authorities before any lookup,
        // even for order IDs that do not exist (403, not 404).
        authenticateAs(admin());

        assertThrows(ForbiddenException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        verify(paymentRepository, never()).findByRazorpayOrderIdWithPessimisticLock(any());
        verify(paymentGateway, never()).verifySignature(any(), any(), any());
    }

    @Test
    void verifyAmountMismatchRejected() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_1"))
                .thenReturn(new RazorpayPaymentInfo("order_rzp1", "captured", TOTAL_PAISE + 1, "INR"));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        assertEquals("Payment amount does not match the rental amount", ex.getMessage());

        verify(paymentRepository, never()).save(any());
        verify(rentalRepository, never()).save(any());
        assertEquals(RentalStatus.PENDING, rental.getRentalStatus());
    }

    @Test
    void verifyCurrencyMismatchRejected() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_1"))
                .thenReturn(new RazorpayPaymentInfo("order_rzp1", "captured", TOTAL_PAISE, "USD"));

        assertThrows(InvalidRequestException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        verify(paymentRepository, never()).save(any());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void verifyNotCapturedPaymentRejectedAndLeftPending() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_1"))
                .thenReturn(new RazorpayPaymentInfo("order_rzp1", "authorized", TOTAL_PAISE, "INR"));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        assertEquals("Payment is not captured", ex.getMessage());

        verify(paymentRepository, never()).save(any());
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertEquals(RentalStatus.PENDING, rental.getRentalStatus());
    }

    @Test
    void verifyAuthoritativeFailureMarksPaymentFailedWithoutConfirmingRental() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_1"))
                .thenReturn(new RazorpayPaymentInfo("order_rzp1", "failed", TOTAL_PAISE, "INR"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(PaymentFailedException.class,
                () -> paymentService.verifyPayment(verifyRequest()));

        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
        verify(paymentRepository).save(payment);
        verify(rentalRepository, never()).save(any());
        assertEquals(RentalStatus.PENDING, rental.getRentalStatus());
    }

    @Test
    void verifyAuthoritativeCancellationMarksPaymentCancelledWithoutConfirmingRental() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_1"))
                .thenReturn(new RazorpayPaymentInfo("order_rzp1", "cancelled", TOTAL_PAISE, "INR"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(PaymentFailedException.class,
                () -> paymentService.verifyPayment(verifyRequest()));

        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());
        verify(rentalRepository, never()).save(any());
        assertEquals(RentalStatus.PENDING, rental.getRentalStatus());
    }

    @Test
    void verifyDuplicateVerificationIsIdempotentAndRejected() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId("pay_1");
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        assertThrows(DuplicateResourceException.class,
                () -> paymentService.verifyPayment(verifyRequest()));

        verify(paymentGateway, never()).verifySignature(any(), any(), any());
        verify(paymentGateway, never()).fetchPayment(anyString());
        verify(paymentRepository, never()).save(any());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void verifyForAlreadyConfirmedRentalRejected() {
        Rental rental = rental();
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        assertThrows(DuplicateResourceException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        verify(paymentGateway, never()).verifySignature(any(), any(), any());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void verifyForCancelledRentalRejected() {
        Rental rental = rental();
        rental.setRentalStatus(RentalStatus.CANCELLED);
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        assertThrows(InvalidRequestException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        verify(paymentGateway, never()).verifySignature(any(), any(), any());
    }

    @Test
    void verifyForExpiredPendingRentalRejected() {
        Rental rental = rental();
        rental.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        assertEquals("Rental payment session has expired", ex.getMessage());
        verify(paymentGateway, never()).verifySignature(any(), any(), any());
    }

    @Test
    void verifyGatewayUnreachablePropagatesWithoutAnyStateChange() {
        Rental rental = rental();
        Payment payment = paymentFor(rental);
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(paymentRepository.findByRazorpayOrderIdWithPessimisticLock("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(paymentGateway.verifySignature("order_rzp1", "pay_1", "valid_signature"))
                .thenReturn(true);
        when(paymentGateway.fetchPayment("pay_1"))
                .thenThrow(new PaymentGatewayException("Payment gateway is unavailable"));

        assertThrows(PaymentGatewayException.class,
                () -> paymentService.verifyPayment(verifyRequest()));

        verify(paymentRepository, never()).save(any());
        verify(rentalRepository, never()).save(any());
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        assertEquals(RentalStatus.PENDING, rental.getRentalStatus());
    }

    @Test
    void verifyRentalNotOwnedByCurrentCustomerRejected() {
        Rental rental = rental();
        rental.setUser(otherCustomer());
        Payment payment = paymentFor(rental);
        payment.setUser(customer());
        authenticateAs(customer());
        when(paymentRepository.findByRazorpayOrderId("order_rzp1"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findRentalIdByRazorpayOrderId("order_rzp1")).thenReturn(10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        assertThrows(ForbiddenException.class,
                () -> paymentService.verifyPayment(verifyRequest()));
        verify(paymentGateway, never()).verifySignature(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // demo payment simulation (Phase 12B: unique demo identifiers)
    // ------------------------------------------------------------------

    private void stubSimulateShared() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                anyLong(), any(PaymentStatus.class))).thenReturn(false);
        when(paymentRepository.findByRentalRentalIdAndPaymentStatus(
                anyLong(), any(PaymentStatus.class))).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rentalRepository.save(any(Rental.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubSimulateRental(Long rentalId) {
        Rental rental = rental();
        rental.setRentalId(rentalId);
        when(rentalRepository.findByIdWithPessimisticLock(rentalId))
                .thenReturn(Optional.of(rental));
    }

    @Test
    void simulatePaymentUsesDistinctDemoIdentifiersForDifferentRentals() {
        authenticateAs(customer());
        stubSimulateShared();
        stubSimulateRental(10L);
        stubSimulateRental(11L);

        PaymentSimulateResponse firstResult =
                paymentService.simulatePayment(new PaymentSimulateRequest(10L));
        PaymentSimulateResponse secondResult =
                paymentService.simulatePayment(new PaymentSimulateRequest(11L));

        assertEquals("SUCCESS", firstResult.getPaymentStatus());
        assertEquals("SUCCESS", secondResult.getPaymentStatus());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        List<Payment> saved = captor.getAllValues();
        assertNotEquals(saved.get(0).getRazorpayOrderId(), saved.get(1).getRazorpayOrderId());
        assertNotEquals(saved.get(0).getRazorpayPaymentId(), saved.get(1).getRazorpayPaymentId());
        // The rental ID is part of the identifier, so concurrent
        // simulations of different rentals can never collide on the
        // UNIQUE columns.
        assertTrue(saved.get(0).getRazorpayOrderId().startsWith("DEMO-ORDER-10-"));
        assertTrue(saved.get(1).getRazorpayOrderId().startsWith("DEMO-ORDER-11-"));
        assertTrue(saved.get(0).getRazorpayPaymentId().startsWith("DEMO-PAY-10-"));
        assertTrue(saved.get(1).getRazorpayPaymentId().startsWith("DEMO-PAY-11-"));
    }

    @Test
    void simulatePaymentDemoIdentifiersStayUniqueAcrossManyRentals() {
        authenticateAs(customer());
        stubSimulateShared();
        int rentalCount = 8;
        for (long id = 100L; id < 100L + rentalCount; id++) {
            stubSimulateRental(id);
        }

        for (long id = 100L; id < 100L + rentalCount; id++) {
            PaymentSimulateResponse response =
                    paymentService.simulatePayment(new PaymentSimulateRequest(id));
            assertEquals("SUCCESS", response.getPaymentStatus());
        }

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(rentalCount)).save(captor.capture());
        List<Payment> saved = captor.getAllValues();
        long distinctOrderIds = saved.stream()
                .map(Payment::getRazorpayOrderId).distinct().count();
        long distinctPaymentIds = saved.stream()
                .map(Payment::getRazorpayPaymentId).distinct().count();
        assertEquals(rentalCount, distinctOrderIds);
        assertEquals(rentalCount, distinctPaymentIds);
    }

    // ------------------------------------------------------------------
    // demo payment outcomes (SUCCESS / FAILED / CANCELLED)
    // ------------------------------------------------------------------

    private PaymentSimulateRequest outcomeRequest(long rentalId, String simulatedStatus) {
        PaymentSimulateRequest request = new PaymentSimulateRequest(rentalId);
        request.setSimulatedStatus(simulatedStatus);
        return request;
    }

    // Lighter stub set for flows that throw before the pending-attempt lookup
    // (kept separate from stubSimulateShared to satisfy strict stubbing).
    private void stubSimulateGuardsOnly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                anyLong(), any(PaymentStatus.class))).thenReturn(false);
    }

    // Outcome stubs without the rental-save stub (FAILED/CANCELLED never save
    // the rental, so stubbing it would be unnecessary under strict stubbing).
    private void stubSimulateUnsuccessfulOutcomePath() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                anyLong(), any(PaymentStatus.class))).thenReturn(false);
        when(paymentRepository.findByRentalRentalIdAndPaymentStatus(
                anyLong(), any(PaymentStatus.class))).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void simulatePaymentFailedKeepsRentalPending() {
        authenticateAs(customer());
        stubSimulateUnsuccessfulOutcomePath();
        stubSimulateRental(10L);

        PaymentSimulateResponse response =
                paymentService.simulatePayment(outcomeRequest(10L, "FAILED"));

        assertEquals("FAILED", response.getPaymentStatus());
        assertEquals("PENDING", response.getRentalStatus());
        assertTrue(response.getMessage().contains("DEMO FAILED"));
        // A FAILED attempt must never confirm the rental.
        verify(rentalRepository, never()).save(any(Rental.class));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.FAILED, captor.getValue().getPaymentStatus());
        assertEquals(TOTAL, captor.getValue().getAmount());
        assertNull(captor.getValue().getPaymentDate());
    }

    @Test
    void simulatePaymentCancelledKeepsRentalPending() {
        authenticateAs(customer());
        stubSimulateUnsuccessfulOutcomePath();
        stubSimulateRental(10L);

        PaymentSimulateResponse response =
                paymentService.simulatePayment(outcomeRequest(10L, "CANCELLED"));

        assertEquals("CANCELLED", response.getPaymentStatus());
        assertEquals("PENDING", response.getRentalStatus());
        assertTrue(response.getMessage().contains("DEMO CANCELLED"));
        verify(rentalRepository, never()).save(any(Rental.class));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.CANCELLED, captor.getValue().getPaymentStatus());
        assertNull(captor.getValue().getPaymentDate());
    }

    @Test
    void simulatePaymentRetryAfterFailureSucceeds() {
        authenticateAs(customer());
        stubSimulateShared();
        stubSimulateRental(10L);

        PaymentSimulateResponse failed =
                paymentService.simulatePayment(outcomeRequest(10L, "FAILED"));
        assertEquals("FAILED", failed.getPaymentStatus());
        assertEquals("PENDING", failed.getRentalStatus());

        // Retry with the default SUCCESS outcome through the existing flow.
        PaymentSimulateResponse succeeded =
                paymentService.simulatePayment(new PaymentSimulateRequest(10L));
        assertEquals("SUCCESS", succeeded.getPaymentStatus());
        assertEquals("CONFIRMED", succeeded.getRentalStatus());
        verify(rentalRepository, times(1)).save(any(Rental.class));
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    void simulatePaymentMissingOutcomeKeepsSuccessBehavior() {
        authenticateAs(customer());
        stubSimulateShared();
        // Fresh rental instance per call: the SUCCESS branch mutates the
        // loaded rental to CONFIRMED, exactly like the existing tests avoid
        // via distinct rental ids.
        when(rentalRepository.findByIdWithPessimisticLock(10L))
                .thenAnswer(inv -> Optional.of(rental()));

        PaymentSimulateResponse blank =
                paymentService.simulatePayment(outcomeRequest(10L, "   "));
        assertEquals("SUCCESS", blank.getPaymentStatus());
        assertEquals("CONFIRMED", blank.getRentalStatus());

        PaymentSimulateResponse lowerSuccess =
                paymentService.simulatePayment(outcomeRequest(10L, "success"));
        assertEquals("SUCCESS", lowerSuccess.getPaymentStatus());
        assertEquals("CONFIRMED", lowerSuccess.getRentalStatus());
    }

    @Test
    void simulatePaymentRejectsUnknownOutcome() {
        authenticateAs(customer());
        stubSimulateGuardsOnly();
        stubSimulateRental(10L);

        assertThrows(InvalidRequestException.class,
                () -> paymentService.simulatePayment(outcomeRequest(10L, "PARTIAL")));
        // REFUNDED/PENDING exist but are not valid demo outcomes.
        assertThrows(InvalidRequestException.class,
                () -> paymentService.simulatePayment(outcomeRequest(10L, "REFUNDED")));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void simulatePaymentDuplicateSuccessStaysProtectedForFailedOutcome() {
        authenticateAs(customer());
        when(rentalRepository.findByIdWithPessimisticLock(10L))
                .thenReturn(Optional.of(rental()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                anyLong(), eq(PaymentStatus.SUCCESS))).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> paymentService.simulatePayment(outcomeRequest(10L, "FAILED")));
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(rentalRepository, never()).save(any(Rental.class));
    }
}
