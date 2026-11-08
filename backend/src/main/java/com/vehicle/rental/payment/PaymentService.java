package com.vehicle.rental.payment;

import com.vehicle.rental.dto.PaymentCreateOrderRequest;
import com.vehicle.rental.dto.PaymentCreateOrderResponse;
import com.vehicle.rental.dto.PaymentResponse;
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
import com.vehicle.rental.payment.RazorpayOrderInfo;
import com.vehicle.rental.repository.PaymentRepository;
import com.vehicle.rental.repository.RentalRepository;
import com.vehicle.rental.repository.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 8 payment orchestration: create-order and verify for Razorpay.
 *
 * <p>Security model: the amount always comes from rental.total_amount in the
 * database, the Razorpay order ID used for signature verification is always
 * the trusted one stored on the Payment row, and the rental is confirmed only
 * after signature + server-side fetch (order relationship, exact amount, INR,
 * captured) all agree. Client-supplied amounts/statuses are never trusted.</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Must match the Phase 6 pending-rental timeout in RentalService. */
    private static final int PENDING_EXPIRY_MINUTES = 15;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;

    public PaymentService(PaymentGateway paymentGateway,
                          PaymentRepository paymentRepository,
                          RentalRepository rentalRepository,
                          UserRepository userRepository) {
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
        this.rentalRepository = rentalRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a Razorpay order for the customer's own eligible PENDING rental
     * and persists a PENDING Payment attempt. The rental's stored total is the
     * single source of truth for the amount.
     */
    @Transactional
    public PaymentCreateOrderResponse createOrderForRental(PaymentCreateOrderRequest request) {
        // Role gate from the server-issued JWT (no database read): runs before
        // any resource lookup so wrong-role callers always get 403 instead of
        // 404, and the locking read below stays the first database access.
        Long currentUserId = getCurrentUserId();
        requireCustomerAuthority("Only customers can make payments");

        // Locking read must be the first database access (Phase 6 snapshot
        // discipline): concurrent verify/create on the same rental serializes
        // on this row so "already paid" checks cannot race.
        Rental rental = rentalRepository.findByIdWithPessimisticLock(request.getRentalId())
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (currentUser.getRole() != UserRole.CUSTOMER) {
            throw new ForbiddenException("Only customers can make payments");
        }
        if (!rental.getUser().getUserId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied");
        }
        validateRentalEligibleForPayment(rental);

        if (paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                rental.getRentalId(), PaymentStatus.SUCCESS)) {
            throw new DuplicateResourceException("Rental has already been paid");
        }

        // Check for existing PENDING payment attempts for this rental/user
        Optional<Payment> existingPending = findReusablePendingPayment(rental.getRentalId(), currentUserId);
        if (existingPending.isPresent()) {
            Payment existing = existingPending.get();
            // Try to resolve the existing PENDING attempt
            try {
                Optional<PaymentVerifyResponse> resolved = resolvePendingPayment(existing);
                if (resolved.isPresent()) {
                    // Existing attempt was already captured - return success
                    return new PaymentCreateOrderResponse(
                            existing.getPaymentId(),
                            rental.getRentalId(),
                            existing.getRazorpayOrderId(),
                            PaymentGateway.toPaise(existing.getAmount()),
                            "INR",
                            paymentGateway.getKeyId()
                    );
                }
                // Empty Optional means existing order is still usable - reuse it
                return new PaymentCreateOrderResponse(
                        existing.getPaymentId(),
                        rental.getRentalId(),
                        existing.getRazorpayOrderId(),
                        PaymentGateway.toPaise(existing.getAmount()),
                        "INR",
                        paymentGateway.getKeyId()
                );
            } catch (InvalidRequestException e) {
                // Order is failed/cancelled/expired or reconciliation needed - allow new attempt
                // Fall through to create new order
                log.info("Existing PENDING payment {} cannot be reused: {}", existing.getPaymentId(), e.getMessage());
            } catch (PaymentGatewayException e) {
                // Gateway unavailable - don't create new order, preserve existing attempt
                throw new PaymentGatewayException("Unable to verify existing payment order: " + e.getMessage());
            }
        }

        BigDecimal amount = rental.getTotalAmount();
        String receipt = "rental_" + rental.getRentalId() + "_" + randomSuffix();
        String orderId = paymentGateway.createOrder(amount, receipt);

        Payment payment = new Payment();
        payment.setRental(rental);
        payment.setUser(rental.getUser());
        payment.setAmount(amount);
        payment.setPaymentMethod(PaymentMethod.RAZORPAY);
        payment.setRazorpayOrderId(orderId);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        PaymentCreateOrderResponse response = new PaymentCreateOrderResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setRentalId(rental.getRentalId());
        response.setRazorpayOrderId(orderId);
        response.setAmountPaise(PaymentGateway.toPaise(amount));
        response.setCurrency("INR");
        response.setKeyId(paymentGateway.getKeyId());
        return response;
    }

    /**
     * Verifies a Razorpay checkout result and, only when everything is
     * authoritative and consistent, atomically marks the Payment SUCCESS and
     * the Rental CONFIRMED.
     *
     * <p>noRollbackFor PaymentFailedException: when Razorpay itself reports the
     * attempt failed/cancelled we must persist that mark while still returning
     * an error; every other rejection rolls back with nothing written.</p>
     */
    @Transactional(noRollbackFor = PaymentFailedException.class)
    public PaymentVerifyResponse verifyPayment(PaymentVerifyRequest request) {
        // Role gate from the server-issued JWT (no database read): wrong-role
        // callers get 403 for any order ID, and the payment locking read below
        // remains the first database access (Phase 6 snapshot discipline).
        getCurrentUserId();
        requireCustomerAuthority("Only customers can verify payments");

        // First, find the payment WITHOUT lock to get the rental ID.
        // We need the rental ID first to establish consistent lock ordering (Rental first).
        Payment paymentUnlocked = paymentRepository
                .findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        // Lock the rental FIRST to maintain consistent lock ordering (Rental before Payment).
        Long currentUserId = getCurrentUserId();
        requireCustomerAuthority("Only customers can verify payments");

        Long rentalId = paymentRepository.findRentalIdByRazorpayOrderId(request.getRazorpayOrderId());
        Rental rental = rentalRepository.findByIdWithPessimisticLock(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (currentUser.getRole() != UserRole.CUSTOMER) {
            throw new ForbiddenException("Only customers can verify payments");
        }
        if (!paymentUnlocked.getUser().getUserId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied");
        }
        if (!rental.getUser().getUserId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied");
        }
        validateRentalEligibleForPayment(rental);

        // Now lock the specific payment row for this order
        Payment payment = paymentRepository
                .findByRazorpayOrderIdWithPessimisticLock(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            throw new DuplicateResourceException("Payment has already been processed");
        }

        if (!payment.getUser().getUserId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied");
        }
        validateRentalEligibleForPayment(rental);

        // Signature check uses the TRUSTED stored order ID + received payment ID.
        if (!paymentGateway.verifySignature(payment.getRazorpayOrderId(),
                request.getRazorpayPaymentId(), request.getRazorpaySignature())) {
            throw new InvalidRequestException("Payment signature verification failed");
        }

        // Authoritative values from Razorpay's server-side API.
        RazorpayPaymentInfo info = paymentGateway.fetchPayment(request.getRazorpayPaymentId());

        if (!payment.getRazorpayOrderId().equals(info.getOrderId())) {
            throw new InvalidRequestException("Razorpay payment does not match the order");
        }
        if (!"INR".equalsIgnoreCase(info.getCurrency())) {
            throw new InvalidRequestException("Payment currency must be INR");
        }
        if (info.getAmountPaise() != PaymentGateway.toPaise(payment.getAmount())) {
            throw new InvalidRequestException("Payment amount does not match the rental amount");
        }

        String status = info.getStatus() == null
                ? "" : info.getStatus().toLowerCase(Locale.ROOT);
        if (!"captured".equals(status)) {
            if ("failed".equals(status)) {
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.info("Payment {} marked FAILED from authoritative Razorpay status", payment.getPaymentId());
                throw new PaymentFailedException("Payment failed at Razorpay");
            }
            if ("cancelled".equals(status)) {
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                log.info("Payment {} marked CANCELLED from authoritative Razorpay status", payment.getPaymentId());
                throw new PaymentFailedException("Payment was cancelled at Razorpay");
            }
            throw new InvalidRequestException("Payment is not captured");
        }

        // Success: Payment PENDING -> SUCCESS and Rental PENDING -> CONFIRMED
        // in this transaction. Pricing fields and promo usage are untouched.
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        rental.setRentalStatus(RentalStatus.CONFIRMED);
        rentalRepository.save(rental);

        PaymentVerifyResponse response = new PaymentVerifyResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setRentalId(rental.getRentalId());
        response.setRazorpayOrderId(payment.getRazorpayOrderId());
        response.setRazorpayPaymentId(payment.getRazorpayPaymentId());
        response.setAmount(payment.getAmount());
        response.setPaymentStatus(payment.getPaymentStatus().name());
        response.setRentalStatus(rental.getRentalStatus().name());
        return response;
    }

    /**
     * Shared authoritative success path: transitions a PENDING payment to SUCCESS
     * and the associated rental to CONFIRMED, after all validations pass.
     * Idempotent: if the payment is already SUCCESS, returns the current state.
     */
    @Transactional
    public PaymentVerifyResponse confirmPayment(Payment payment) {
        // Re-validate rental eligibility inside this transaction
        Long rentalId = paymentRepository.findRentalIdByPaymentId(payment.getPaymentId());
        Rental rental = rentalRepository.findByIdWithPessimisticLock(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found"));

        // If already SUCCESS, return current state (idempotent)
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            PaymentVerifyResponse response = new PaymentVerifyResponse();
            response.setPaymentId(payment.getPaymentId());
            response.setRentalId(rental.getRentalId());
            response.setRazorpayOrderId(payment.getRazorpayOrderId());
            response.setRazorpayPaymentId(payment.getRazorpayPaymentId());
            response.setAmount(payment.getAmount());
            response.setPaymentStatus(payment.getPaymentStatus().name());
            response.setRentalStatus(rental.getRentalStatus().name());
            return response;
        }

        // Prevent duplicate SUCCESS for the same rental
        if (paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                rental.getRentalId(), PaymentStatus.SUCCESS)) {
            throw new DuplicateResourceException("Rental has already been paid");
        }

        // Verify the payment is actually captured at Razorpay
        RazorpayPaymentInfo info = paymentGateway.fetchPayment(payment.getRazorpayPaymentId());

        if (!payment.getRazorpayOrderId().equals(info.getOrderId())) {
            throw new InvalidRequestException("Razorpay payment does not match the order");
        }
        if (!"INR".equalsIgnoreCase(info.getCurrency())) {
            throw new InvalidRequestException("Payment currency must be INR");
        }
        if (info.getAmountPaise() != PaymentGateway.toPaise(payment.getAmount())) {
            throw new InvalidRequestException("Payment amount does not match the rental amount");
        }

        String status = info.getStatus() == null
                ? "" : info.getStatus().toLowerCase(Locale.ROOT);
        if (!"captured".equals(status)) {
            throw new InvalidRequestException("Payment is not captured");
        }

        // Success: Payment PENDING -> SUCCESS and Rental PENDING -> CONFIRMED
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // Use the rental already fetched at the beginning of the method
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        rentalRepository.save(rental);

        PaymentVerifyResponse response = new PaymentVerifyResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setRentalId(rental.getRentalId());
        response.setRazorpayOrderId(payment.getRazorpayOrderId());
        response.setRazorpayPaymentId(payment.getRazorpayPaymentId());
        response.setAmount(payment.getAmount());
        response.setPaymentStatus(PaymentStatus.SUCCESS.name());
        response.setRentalStatus(RentalStatus.CONFIRMED.name());
        return response;
    }

    /**
     * Simulates a successful payment for a rental (demo purposes only).
     * This is a college demo feature that records a successful payment without
     * contacting any real payment gateway. The payment is marked with a DEMO
     * reference to clearly distinguish it from real payments.
     *
     * The operation is atomic: either both the payment and rental status are
     * updated, or neither is changed.
     */
    @Transactional
    public PaymentSimulateResponse simulatePayment(PaymentSimulateRequest request) {
        // Role gate from the server-issued JWT (no database read): runs before
        // any resource lookup so wrong-role callers always get 403 instead of
        // 404, and the locking read below stays the first database access.
        Long currentUserId = getCurrentUserId();
        requireCustomerAuthority("Only customers can make payments");

        // Locking read must be the first database access: concurrent
        // simulate/verify/create on the same rental serializes on this row
        // so "already paid" checks cannot race.
        Rental rental = rentalRepository.findByIdWithPessimisticLock(request.getRentalId())
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (currentUser.getRole() != UserRole.CUSTOMER) {
            throw new ForbiddenException("Only customers can make payments");
        }
        if (!rental.getUser().getUserId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied");
        }
        validateRentalEligibleForPayment(rental);

        // Check if rental already has a successful payment
        if (paymentRepository.existsByRentalRentalIdAndPaymentStatus(
                rental.getRentalId(), PaymentStatus.SUCCESS)) {
            throw new DuplicateResourceException("Rental has already been paid");
        }

        // Optional demo outcome. Missing/null/blank/"SUCCESS" keeps the existing
        // success behaviour; FAILED and CANCELLED persist the attempt as that
        // status and leave the rental PENDING so the customer can retry.
        // Unknown values are rejected instead of silently becoming SUCCESS.
        PaymentStatus simulatedStatus = PaymentStatus.SUCCESS;
        if (request.getSimulatedStatus() != null && !request.getSimulatedStatus().isBlank()) {
            try {
                simulatedStatus = PaymentStatus.valueOf(
                        request.getSimulatedStatus().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException(
                        "simulatedStatus must be SUCCESS, FAILED, or CANCELLED");
            }
            if (simulatedStatus != PaymentStatus.SUCCESS
                    && simulatedStatus != PaymentStatus.FAILED
                    && simulatedStatus != PaymentStatus.CANCELLED) {
                throw new InvalidRequestException(
                        "simulatedStatus must be SUCCESS, FAILED, or CANCELLED");
            }
        }
        boolean outcomeIsSuccess = simulatedStatus == PaymentStatus.SUCCESS;

        // Check for existing PENDING payment attempts for this rental/user
        Optional<Payment> existingPending = findReusablePendingPayment(rental.getRentalId(), currentUserId);
        if (existingPending.isPresent()) {
            Payment existing = existingPending.get();
            // Check the status of the existing PENDING payment's Razorpay order
            // Do not blindly cancel - if a real Razorpay payment was captured, we must reconcile it
            try {
                Optional<PaymentVerifyResponse> resolved = resolvePendingPayment(existing);
                if (resolved.isPresent()) {
                    // Existing attempt was already captured - return that success instead of creating demo
                    log.info("Existing PENDING payment {} was already captured, returning reconciled result", existing.getPaymentId());
                    PaymentSimulateResponse response = new PaymentSimulateResponse();
                    response.setPaymentId(existing.getPaymentId());
                    response.setRentalId(rental.getRentalId());
                    response.setAmount(existing.getAmount());
                    response.setPaymentStatus(PaymentStatus.SUCCESS.name());
                    response.setRentalStatus(RentalStatus.CONFIRMED.name());
                    response.setDemoReference("RECONCILED-" + existing.getRazorpayPaymentId());
                    response.setPaymentDate(existing.getPaymentDate());
                    response.setMessage("RECONCILED FROM EXISTING PAYMENT — This payment was already captured at Razorpay.");
                    return response;
                }
                // Empty Optional means existing order is still usable (created/attempted) - reject simulation
                log.info("Existing PENDING payment {} has usable order, rejecting simulation", existing.getPaymentId());
                throw new InvalidRequestException("Cannot simulate payment: existing Razorpay order is still active. Please complete or cancel the existing payment first.");
            } catch (PaymentGatewayException e) {
                // Gateway unavailable - don't create new order, preserve existing attempt
                throw new PaymentGatewayException("Unable to verify existing payment order: " + e.getMessage());
            }
        }

        BigDecimal amount = rental.getTotalAmount();
        String demoReference = "DEMO-" + System.currentTimeMillis() + "-" + randomSuffix();

        // Create simulated payment record
        Payment payment = new Payment();
        payment.setRental(rental);
        payment.setUser(rental.getUser());
        payment.setAmount(amount);
        payment.setPaymentMethod(PaymentMethod.RAZORPAY); // Schema only supports RAZORPAY
        // Demo identifiers follow the real-order convention (entity ID +
        // random suffix): the rental ID keeps concurrent simulations of
        // different rentals distinct, and the random suffix defends
        // against same-millisecond collisions. Both columns are UNIQUE,
        // so a collision would surface as a misleading 401 instead of a
        // successful simulation.
        payment.setRazorpayOrderId("DEMO-ORDER-" + rental.getRentalId() + "-" + randomSuffix());
        payment.setRazorpayPaymentId("DEMO-PAY-" + rental.getRentalId() + "-" + randomSuffix());
        payment.setPaymentStatus(simulatedStatus);
        if (outcomeIsSuccess) {
            payment.setPaymentDate(LocalDateTime.now());
        }
        payment = paymentRepository.save(payment);

        // Only a successful outcome confirms the rental; FAILED/CANCELLED
        // leave the booking PENDING (retry is handled by the existing flow).
        if (outcomeIsSuccess) {
            rental.setRentalStatus(RentalStatus.CONFIRMED);
            rentalRepository.save(rental);
        }

        log.info("Demo payment {} created for rental {} with status {}",
                payment.getPaymentId(), rental.getRentalId(), simulatedStatus);

        PaymentSimulateResponse response = new PaymentSimulateResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setRentalId(rental.getRentalId());
        response.setAmount(payment.getAmount());
        response.setPaymentStatus(payment.getPaymentStatus().name());
        response.setRentalStatus(rental.getRentalStatus().name());
        response.setDemoReference(demoReference);
        response.setPaymentDate(payment.getPaymentDate());
        response.setMessage(outcomeIsSuccess
                ? "SIMULATED DEMO PAYMENT \u2014 No real money was charged. Payment recorded as DEMO SUCCESS."
                : (simulatedStatus == PaymentStatus.CANCELLED
                        ? "SIMULATED DEMO PAYMENT - No real money was charged. Payment recorded as DEMO CANCELLED. The rental is still PENDING, so you can retry."
                        : "SIMULATED DEMO PAYMENT - No real money was charged. Payment recorded as DEMO FAILED. The rental is still PENDING, so you can retry."));
        return response;
    }

    /**
     * Attempts to resolve an existing PENDING payment attempt for the given rental.
     * Returns the existing payment if it can be safely reused, or null if a new
     * attempt should be created.
     */
    private Optional<Payment> findReusablePendingPayment(Long rentalId, Long currentUserId) {
        // Find any PENDING payment for this rental belonging to the current user
        List<Payment> pendingPayments = paymentRepository.findByRentalRentalIdAndPaymentStatus(
                rentalId, PaymentStatus.PENDING);
        return pendingPayments.stream()
                .filter(p -> p.getUser().getUserId().equals(currentUserId))
                .findFirst();
    }

    /**
     * Checks the status of an existing PENDING payment's Razorpay order and
     * determines the appropriate action.
     * Returns:
     * - Empty Optional: existing order is still usable, reuse it
     * - PaymentVerifyResponse: payment was already captured, returns success response
     * - Throws exception: order is failed/cancelled/expired or gateway unavailable
     */
    private Optional<PaymentVerifyResponse> resolvePendingPayment(Payment payment) {
        try {
            RazorpayOrderInfo orderInfo = paymentGateway.fetchOrder(payment.getRazorpayOrderId());
            // If gateway returns null or order not found, treat as order no longer exists in Razorpay
            if (orderInfo == null || orderInfo.getStatus() == null) {
                throw new InvalidRequestException("Razorpay order not found; new attempt allowed");
            }
            String orderStatus = orderInfo.getStatus().toLowerCase(Locale.ROOT);

            switch (orderStatus) {
                case "created":
                case "attempted":
                    // Order is still usable, can be reused
                    return Optional.empty();

                case "paid":
                    // Order was paid but we never verified it - reconcile it
                    // Use the shared confirmPayment path to atomically mark SUCCESS/CONFIRMED
                    return Optional.of(confirmPayment(payment));

                case "failed":
                case "expired":
                case "cancelled":
                    // Order is definitively no longer usable, mark local attempt
                    throw new InvalidRequestException("Razorpay order is " + orderStatus + "; new attempt allowed");

                default:
                    // Unknown status - treat as unknown, don't create new order
                    throw new InvalidRequestException("Razorpay order status unknown: " + orderStatus);
            }
        } catch (PaymentGatewayException e) {
            // Gateway unavailable - don't create new order, preserve existing attempt
            throw new PaymentGatewayException("Unable to determine existing order status: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Phase 9: admin payment listing (read-only)
    // ------------------------------------------------------------------

    /**
     * Every payment, createdAt DESC — a flat read-only list (no pagination,
     * filters, refunds or gateway interaction). Only persisted payment facts
     * are returned; no credentials or secret material exists on the entity.
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toPaymentResponse)
                .collect(Collectors.toList());
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setRentalId(payment.getRental() != null ? payment.getRental().getRentalId() : null);
        response.setUserId(payment.getUser() != null ? payment.getUser().getUserId() : null);
        response.setAmount(payment.getAmount());
        response.setPaymentMethod(payment.getPaymentMethod() != null
                ? payment.getPaymentMethod().name() : null);
        response.setRazorpayOrderId(payment.getRazorpayOrderId());
        response.setRazorpayPaymentId(payment.getRazorpayPaymentId());
        response.setPaymentStatus(payment.getPaymentStatus() != null
                ? payment.getPaymentStatus().name() : null);
        response.setPaymentDate(payment.getPaymentDate());
        response.setCreatedAt(payment.getCreatedAt());
        return response;
    }

    /**
     * Shared rental eligibility rules for payment (Phase 8 section 5):
     * owned by the caller (checked separately), PENDING, not past the Phase 6
     * 15-minute timeout, and a positive stored total.
     */
    private void validateRentalEligibleForPayment(Rental rental) {
        if (rental.getRentalStatus() == RentalStatus.CANCELLED) {
            throw new InvalidRequestException("Cancelled rental cannot be paid");
        }
        if (rental.getRentalStatus() != RentalStatus.PENDING) {
            throw new DuplicateResourceException("Rental has already been paid");
        }
        if (rental.getCreatedAt() != null
                && rental.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES))) {
            throw new InvalidRequestException("Rental payment session has expired");
        }
        if (rental.getTotalAmount() == null
                || rental.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Rental amount must be greater than zero");
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new InvalidRequestException("Authentication required");
        }
        return Long.valueOf(String.valueOf(authentication.getPrincipal()));
    }

    /**
     * Rejects non-CUSTOMER callers using the authorities embedded in the
     * server-issued JWT (login-time roles, never client-supplied) before any
     * resource is looked up. The database-backed role check inside each flow
     * remains as defense against stale JWTs.
     */
    private void requireCustomerAuthority(String message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isCustomer = authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
        if (!isCustomer) {
            throw new ForbiddenException(message);
        }
    }

    private static String randomSuffix() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}