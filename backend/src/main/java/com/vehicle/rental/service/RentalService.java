package com.vehicle.rental.service;

import com.vehicle.rental.dto.AvailabilityResponse;
import com.vehicle.rental.dto.RentalCreateRequest;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.dto.RentalStatusRequest;
import com.vehicle.rental.entity.Maintenance;
import com.vehicle.rental.entity.PromoCode;
import com.vehicle.rental.entity.Rental;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.RentalStatus;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.MaintenanceRepository;
import com.vehicle.rental.repository.PromoCodeRepository;
import com.vehicle.rental.repository.RentalRepository;
import com.vehicle.rental.repository.UserRepository;
import com.vehicle.rental.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class RentalService {

    private static final int PENDING_EXPIRY_MINUTES = 15;
    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");
    private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.20");
    private static final long SECONDS_PER_DAY = 86400L;

    private static final Logger log = LoggerFactory.getLogger(RentalService.class);

    private final RentalRepository rentalRepository;
    private final VehicleRepository vehicleRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final UserRepository userRepository;
    private final PromoService promoService;
    private final PromoCodeRepository promoCodeRepository;

    public RentalService(RentalRepository rentalRepository,
                          VehicleRepository vehicleRepository,
                          MaintenanceRepository maintenanceRepository,
                          UserRepository userRepository,
                          PromoService promoService,
                          PromoCodeRepository promoCodeRepository) {
        this.rentalRepository = rentalRepository;
        this.vehicleRepository = vehicleRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.userRepository = userRepository;
        this.promoService = promoService;
        this.promoCodeRepository = promoCodeRepository;
    }

    public RentalResponse createRental(RentalCreateRequest request) {
        LocalDateTime pickupDateTime = request.getPickupDateTime();
        LocalDateTime returnDateTime = request.getReturnDateTime();
        validateRentalDates(pickupDateTime, returnDateTime);

        Long currentUserId = getCurrentUserId();

        // The pessimistic lock must be the first database access in this
        // transaction. Under MySQL REPEATABLE READ, a plain SELECT before the
        // locking read would establish the transaction snapshot too early, so a
        // concurrent booking that commits while this transaction waits for the
        // lock would remain invisible to the availability checks below.
        Vehicle vehicle = vehicleRepository.findByIdWithPessimisticLock(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        validateVehicleBookable(vehicle);

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ForbiddenException("Only customers can create rentals");
        }

        LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES);
        reconcileExpiredPendingRentals(expiryThreshold);

        if (rentalRepository.countOverlappingBlockingRentals(
                vehicle.getVehicleId(), pickupDateTime, returnDateTime, expiryThreshold) > 0) {
            throw new DuplicateResourceException("Vehicle is already booked for the requested period");
        }

        if (maintenanceRepository.countConflictingMaintenance(
                vehicle.getVehicleId(),
                pickupDateTime.toLocalDate(),
                returnDateTime.toLocalDate()) > 0) {
            throw new DuplicateResourceException("Vehicle is under maintenance during the requested period");
        }

        int numberOfDays = calculateNumberOfDays(pickupDateTime, returnDateTime);
        BigDecimal dailyRate = vehicle.getDailyRate();
        BigDecimal subtotal = dailyRate.multiply(BigDecimal.valueOf(numberOfDays));

        // Optional promo code: resolved, validated and priced entirely
        // server-side. The promo row is locked so the usage limit is
        // enforced consistently, and the usage count is incremented inside
        // this same transaction - a failed booking never consumes a promo.
        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2);
        PromoCode promoCode = null;
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promoCode = promoCodeRepository.findByCodeWithPessimisticLock(request.getPromoCode().trim())
                    .orElseThrow(() -> new InvalidRequestException("Promo code not found"));
            promoService.validateEligibility(promoCode, subtotal);
            discountAmount = promoService.calculateDiscount(promoCode, subtotal);
            promoCode.setUsedCount(promoCode.getUsedCount() + 1);
        }

        BigDecimal taxAmount = subtotal.subtract(discountAmount)
                .multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal securityDeposit = subtotal.multiply(DEPOSIT_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.subtract(discountAmount)
                .add(taxAmount)
                .add(securityDeposit);

        Rental rental = new Rental();
        rental.setUser(user);
        rental.setVehicle(vehicle);
        rental.setPickupDatetime(pickupDateTime);
        rental.setReturnDatetime(returnDateTime);
        rental.setNumberOfDays(numberOfDays);
        rental.setDailyRate(dailyRate);
        rental.setSubtotal(subtotal);
        rental.setDiscountAmount(discountAmount);
        rental.setTaxAmount(taxAmount);
        rental.setSecurityDeposit(securityDeposit);
        rental.setTotalAmount(totalAmount);
        rental.setRentalStatus(RentalStatus.PENDING);
        rental.setNotes(request.getNotes());
        rental.setPromoCode(promoCode);

        return toResponse(rentalRepository.save(rental));
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(Long vehicleId,
                                                    LocalDateTime pickupDateTime,
                                                    LocalDateTime returnDateTime) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        validateRentalDates(pickupDateTime, returnDateTime);

        AvailabilityResponse response = new AvailabilityResponse();
        response.setVehicleId(vehicle.getVehicleId());
        response.setRegistrationNumber(vehicle.getRegistrationNumber());
        response.setVehicleStatus(vehicle.getStatus().name());

        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            response.setAvailable(false);
            response.setMessage(unavailableReason(vehicle.getStatus()));
            return response;
        }

        LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES);
        if (rentalRepository.countOverlappingBlockingRentals(
                vehicleId, pickupDateTime, returnDateTime, expiryThreshold) > 0) {
            response.setAvailable(false);
            response.setMessage("Vehicle is already booked for the requested period");
            return response;
        }

        if (maintenanceRepository.countConflictingMaintenance(
                vehicleId, pickupDateTime.toLocalDate(), returnDateTime.toLocalDate()) > 0) {
            response.setAvailable(false);
            response.setMessage("Vehicle is under maintenance during the requested period");
            return response;
        }

        response.setAvailable(true);
        response.setMessage("Vehicle is available for the requested period");
        return response;
    }

    @Transactional(readOnly = true)
    public List<RentalResponse> getMyRentals() {
        Long currentUserId = getCurrentUserId();
        return rentalRepository.findByUserUserIdOrderByPickupDatetimeDesc(currentUserId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RentalResponse getRentalDetails(Long rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found"));

        User currentUser = getCurrentUser();
        if (currentUser.getRole() == UserRole.CUSTOMER
                && !rental.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new ForbiddenException("Access denied");
        }

        return toResponse(rental);
    }

    public RentalResponse cancelRental(Long rentalId) {
        // Locking read as the first database access (Phase 6/8 discipline):
        // serializes customer cancellation against payment verification,
        // the admin status flow and the Phase 8 expiry sweep. The status
        // checks below therefore always evaluate the current row state —
        // a stale PENDING read can never overwrite a concurrently
        // confirmed payment (which would wrongly release promo usage
        // that a paid booking legitimately consumed).
        Rental rental = rentalRepository.findByIdWithPessimisticLock(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found"));

        User currentUser = getCurrentUser();
        if (!rental.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new ForbiddenException("Access denied");
        }

        if (rental.getRentalStatus() == RentalStatus.ACTIVE) {
            throw new InvalidRequestException("Rental cannot be cancelled after pickup has started");
        }
        if (rental.getRentalStatus() == RentalStatus.COMPLETED) {
            throw new InvalidRequestException("Completed rental cannot be cancelled");
        }
        if (rental.getRentalStatus() == RentalStatus.CANCELLED) {
            throw new InvalidRequestException("Rental is already cancelled");
        }
        if (!rental.getPickupDatetime().isAfter(LocalDateTime.now())) {
            throw new InvalidRequestException("Rental cannot be cancelled after pickup time has passed");
        }

        return cancelRentalCore(rental);
    }

    // ------------------------------------------------------------------
    // Phase 9: admin rental operations (single shared cancellation core)
    // ------------------------------------------------------------------

    /**
     * Shared cancellation core used by both the customer cancel flow above and
     * the Phase 9 admin status flow, so the Phase 8 promo-release rule is
     * implemented exactly once: release the reserved usage iff the rental was
     * an unpaid PENDING booking (paid CONFIRMED rentals keep the consumption).
     */
    private RentalResponse cancelRentalCore(Rental rental) {
        boolean wasUnpaidPending = rental.getRentalStatus() == RentalStatus.PENDING;
        PromoCode promoCode = rental.getPromoCode();
        rental.setRentalStatus(RentalStatus.CANCELLED);
        Rental saved = rentalRepository.save(rental);

        if (wasUnpaidPending && promoCode != null) {
            promoCodeRepository.releasePromoUsage(promoCode.getPromoId());
        }
        return toResponse(saved);
    }

    /** Admin rental list: every rental, createdAt DESC (no pagination/filters). */
    @Transactional(readOnly = true)
    public List<RentalResponse> getAllRentals() {
        return rentalRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Phase 9 admin status transitions — the exact approved matrix:
     * PENDING -> CANCELLED, CONFIRMED -> CANCELLED, CONFIRMED -> ACTIVE (only
     * once server time has reached pickup). Everything else is 400, including
     * PENDING -> CONFIRMED (confirmation is exclusively Phase 8 payment
     * verification) and any change to ACTIVE through this endpoint (completing
     * an ACTIVE rental is /return's job only).
     */
    @Transactional
    public RentalResponse updateRentalStatusForAdmin(Long rentalId, RentalStatusRequest request) {
        RentalStatus newStatus;
        try {
            newStatus = RentalStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid rental status");
        }

        // Locking read as the first database access (Phase 6/8 discipline):
        // serializes against customer cancels, the Phase 8 expiry sweep and
        // concurrent admin updates of the same rental.
        Rental rental = rentalRepository.findByIdWithPessimisticLock(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found"));

        RentalStatus current = rental.getRentalStatus();
        if (newStatus == current) {
            throw new InvalidRequestException("Rental status is already " + current.name());
        }
        if (current == RentalStatus.ACTIVE) {
            throw new InvalidRequestException(
                    "Active rentals can only be completed through the return endpoint");
        }
        if (current == RentalStatus.COMPLETED) {
            throw new InvalidRequestException("Completed rental status cannot be changed");
        }
        if (current == RentalStatus.CANCELLED) {
            throw new InvalidRequestException("Cancelled rental status cannot be changed");
        }
        if (newStatus == RentalStatus.CONFIRMED) {
            throw new InvalidRequestException(
                    "Rentals can only be confirmed through successful payment");
        }
        if (newStatus == RentalStatus.ACTIVE) {
            if (current != RentalStatus.CONFIRMED) {
                throw new InvalidRequestException("Invalid rental status transition");
            }
            if (rental.getPickupDatetime().isAfter(LocalDateTime.now())) {
                throw new InvalidRequestException(
                        "Rental cannot be activated before the pickup time");
            }
            rental.setRentalStatus(RentalStatus.ACTIVE);
            return toResponse(rentalRepository.save(rental));
        }
        if (newStatus == RentalStatus.CANCELLED) {
            if (current != RentalStatus.PENDING && current != RentalStatus.CONFIRMED) {
                throw new InvalidRequestException("Invalid rental status transition");
            }
            return cancelRentalCore(rental);
        }
        // Remaining combinations (e.g. PENDING -> COMPLETED) are not legal.
        throw new InvalidRequestException("Invalid rental status transition");
    }

    /**
     * Phase 9 /return: the only way an ACTIVE rental becomes COMPLETED.
     * Records the server's current datetime as actual_return_datetime and
     * touches nothing else — no repricing, no late fees, pricing fields
     * unchanged.
     */
    @Transactional
    public RentalResponse returnRentalForAdmin(Long rentalId) {
        Rental rental = rentalRepository.findByIdWithPessimisticLock(rentalId)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found"));

        if (rental.getRentalStatus() != RentalStatus.ACTIVE) {
            throw new InvalidRequestException("Only active rentals can be returned");
        }

        rental.setRentalStatus(RentalStatus.COMPLETED);
        rental.setActualReturnDatetime(LocalDateTime.now());
        return toResponse(rentalRepository.save(rental));
    }

    /**
     * Phase 8 promo reconciliation for the Phase 6 15-minute pending timeout.
     * Expiry is processed globally here: each expired PENDING rental is
     * transitioned to CANCELLED at most once (conditional update), and any promo
     * usage it reserved is released in the same transaction — so failed,
     * expired or cancelled unpaid bookings never permanently consume a promo,
     * while retries and duplicate processing never release twice.
     *
     * Shared by the booking flow (which calls it inside its transaction) and
     * by the automatic scheduled sweep below. Public so the sweep can be
     * verified with a Mockito spy in unit tests.
     */
    public void reconcileExpiredPendingRentals(LocalDateTime expiryThreshold) {
        List<Rental> expiredRentals = rentalRepository.findExpiredPendingRentals(
                RentalStatus.PENDING, expiryThreshold);
        for (Rental rental : expiredRentals) {
            PromoCode promoCode = rental.getPromoCode();
            int cancelled = rentalRepository.cancelRentalIfPending(
                    rental.getRentalId(), RentalStatus.PENDING, RentalStatus.CANCELLED);
            if (cancelled == 1 && promoCode != null) {
                promoCodeRepository.releasePromoUsage(promoCode.getPromoId());
            }
        }
    }

    /**
     * R1-A: automatic pending-rental expiry sweep, executed by Spring's
     * scheduler every five minutes (scheduling is enabled on the
     * application class). It delegates to the exact same reconciliation
     * core the booking flow uses, so the 15-minute expiry rule, the
     * idempotent conditional cancellation and the promo-usage release
     * keep identical semantics — expired pending rentals no longer wait
     * for the next booking to be reconciled. Confirmed and active
     * rentals are never touched. A scheduler failure is logged and
     * contained so it neither crashes the application nor blocks other
     * scheduled work; the next run simply retries.
     */
    @Scheduled(fixedRate = 300000L)
    public void sweepExpiredPendingRentals() {
        try {
            reconcileExpiredPendingRentals(LocalDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES));
        } catch (Exception e) {
            log.error("Pending-rental expiry sweep failed; it will retry on the next scheduled run", e);
        }
    }

    private void validateRentalDates(LocalDateTime pickupDateTime, LocalDateTime returnDateTime) {
        if (pickupDateTime == null || returnDateTime == null) {
            throw new InvalidRequestException("Pickup and return date and time are required");
        }
        if (!pickupDateTime.isAfter(LocalDateTime.now())) {
            throw new InvalidRequestException("Pickup date and time must be in the future");
        }
        if (!returnDateTime.isAfter(pickupDateTime)) {
            throw new InvalidRequestException("Return date and time must be after pickup date and time");
        }
    }

    private void validateVehicleBookable(Vehicle vehicle) {
        VehicleStatus status = vehicle.getStatus();
        if (status == VehicleStatus.INACTIVE) {
            throw new InvalidRequestException("Vehicle is inactive and cannot be booked");
        }
        if (status == VehicleStatus.RENTED) {
            throw new InvalidRequestException("Vehicle is currently rented");
        }
        if (status == VehicleStatus.MAINTENANCE) {
            throw new InvalidRequestException("Vehicle is under maintenance");
        }
    }

    private int calculateNumberOfDays(LocalDateTime pickupDateTime, LocalDateTime returnDateTime) {
        long seconds = Duration.between(pickupDateTime, returnDateTime).getSeconds();
        long days = (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY;
        return (int) Math.max(days, 1);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new InvalidRequestException("Authentication required");
        }
        return Long.valueOf(String.valueOf(authentication.getPrincipal()));
    }

    private User getCurrentUser() {
        Long currentUserId = getCurrentUserId();
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String unavailableReason(VehicleStatus status) {
        if (status == VehicleStatus.INACTIVE) {
            return "Vehicle is inactive";
        }
        if (status == VehicleStatus.RENTED) {
            return "Vehicle is currently rented";
        }
        if (status == VehicleStatus.MAINTENANCE) {
            return "Vehicle is under maintenance";
        }
        return "Vehicle is not available";
    }

    private RentalResponse toResponse(Rental rental) {
        RentalResponse response = new RentalResponse();
        response.setRentalId(rental.getRentalId());
        response.setUserId(rental.getUser().getUserId());
        response.setVehicleId(rental.getVehicle().getVehicleId());
        response.setVehicleRegistrationNumber(rental.getVehicle().getRegistrationNumber());
        response.setVehicleBrand(rental.getVehicle().getBrand());
        response.setVehicleModel(rental.getVehicle().getModel());
        response.setPickupDateTime(rental.getPickupDatetime());
        response.setReturnDateTime(rental.getReturnDatetime());
        response.setNumberOfDays(rental.getNumberOfDays());
        response.setDailyRate(rental.getDailyRate());
        response.setSubtotal(rental.getSubtotal());
        response.setDiscountAmount(rental.getDiscountAmount());
        response.setTaxAmount(rental.getTaxAmount());
        response.setSecurityDeposit(rental.getSecurityDeposit());
        response.setTotalAmount(rental.getTotalAmount());
        response.setRentalStatus(rental.getRentalStatus().name());
        response.setNotes(rental.getNotes());
        response.setPromoCode(rental.getPromoCode() != null ? rental.getPromoCode().getCode() : null);
        response.setActualReturnDatetime(rental.getActualReturnDatetime());
        response.setCreatedAt(rental.getCreatedAt());
        response.setUpdatedAt(rental.getUpdatedAt());
        return response;
    }
}
