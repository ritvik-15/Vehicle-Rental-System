package com.vehicle.rental.service;

import com.vehicle.rental.dto.AvailabilityResponse;
import com.vehicle.rental.dto.RentalCreateRequest;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.dto.RentalStatusRequest;
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
import com.vehicle.rental.repository.RentalRepository;
import com.vehicle.rental.repository.UserRepository;
import com.vehicle.rental.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentalServiceTest {

    @Mock
    private RentalRepository rentalRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MaintenanceRepository maintenanceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.vehicle.rental.repository.PromoCodeRepository promoCodeRepository;

    @InjectMocks
    private RentalService rentalService;

    private static final BigDecimal DAILY_RATE = new BigDecimal("1200.00");

    private User customer() {
        User user = new User();
        user.setUserId(1L);
        user.setRole(UserRole.CUSTOMER);
        return user;
    }

    private User admin() {
        User user = new User();
        user.setUserId(2L);
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private Vehicle availableVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(1L);
        vehicle.setRegistrationNumber("MH05AB1234");
        vehicle.setBrand("Hyundai");
        vehicle.setModel("i20");
        vehicle.setDailyRate(DAILY_RATE);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        return vehicle;
    }

    private RentalCreateRequest createRequest() {
        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(1L);
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(LocalDateTime.now().plusDays(7));
        request.setNotes("Test rental");
        return request;
    }

    private Rental buildRental(User owner, Vehicle vehicle) {
        Rental rental = new Rental();
        rental.setRentalId(10L);
        rental.setUser(owner);
        rental.setVehicle(vehicle);
        rental.setPickupDatetime(LocalDateTime.now().plusDays(5));
        rental.setReturnDatetime(LocalDateTime.now().plusDays(7));
        rental.setNumberOfDays(2);
        rental.setDailyRate(DAILY_RATE);
        rental.setSubtotal(new BigDecimal("2400.00"));
        rental.setDiscountAmount(new BigDecimal("0.00"));
        rental.setTaxAmount(new BigDecimal("432.00"));
        rental.setSecurityDeposit(new BigDecimal("480.00"));
        rental.setTotalAmount(new BigDecimal("3312.00"));
        rental.setRentalStatus(RentalStatus.PENDING);
        return rental;
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(user.getUserId()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubHappyPath(User user, Vehicle vehicle) {
        authenticateAs(user);
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(vehicleRepository.findByIdWithPessimisticLock(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));
        when(rentalRepository.findExpiredPendingRentals(eq(RentalStatus.PENDING), any()))
                .thenReturn(List.of());
        when(rentalRepository.countOverlappingBlockingRentals(anyLong(), any(), any(), any()))
                .thenReturn(0L);
        when(maintenanceRepository.countConflictingMaintenance(anyLong(), any(), any()))
                .thenReturn(0L);
        when(rentalRepository.save(any(Rental.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createRentalSuccess() {
        User user = customer();
        Vehicle vehicle = availableVehicle();
        stubHappyPath(user, vehicle);

        RentalResponse response = rentalService.createRental(createRequest());

        assertEquals("PENDING", response.getRentalStatus());
        assertEquals(1L, response.getUserId());
        assertEquals(1L, response.getVehicleId());
        assertEquals(2, response.getNumberOfDays());
        assertEquals(new BigDecimal("1200.00"), response.getDailyRate());
        assertEquals(new BigDecimal("2400.00"), response.getSubtotal());
        assertEquals(new BigDecimal("0.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("432.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("480.00"), response.getSecurityDeposit());
        assertEquals(new BigDecimal("3312.00"), response.getTotalAmount());
        assertEquals("MH05AB1234", response.getVehicleRegistrationNumber());

        // server-side daily rate snapshot comes from the vehicle
        assertEquals(DAILY_RATE, response.getDailyRate());

        // a future booking must never change the vehicle status to RENTED
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());

        verify(rentalRepository).findExpiredPendingRentals(
                eq(RentalStatus.PENDING), any(LocalDateTime.class));
    }

    @Test
    void createRentalVehicleNotFound() {
        authenticateAs(customer());
        when(vehicleRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> rentalService.createRental(createRequest()));
    }

    @Test
    void createRentalInactiveVehicleRejected() {
        authenticateAs(customer());
        Vehicle vehicle = availableVehicle();
        vehicle.setStatus(VehicleStatus.INACTIVE);
        when(vehicleRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(vehicle));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.createRental(createRequest()));
        assertTrue(exception.getMessage().contains("inactive"));
    }

    @Test
    void createRentalRentedVehicleRejected() {
        authenticateAs(customer());
        Vehicle vehicle = availableVehicle();
        vehicle.setStatus(VehicleStatus.RENTED);
        when(vehicleRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(vehicle));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.createRental(createRequest()));
        assertTrue(exception.getMessage().contains("rented"));
    }

    @Test
    void createRentalMaintenanceVehicleRejected() {
        authenticateAs(customer());
        Vehicle vehicle = availableVehicle();
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        when(vehicleRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(vehicle));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.createRental(createRequest()));
        assertTrue(exception.getMessage().contains("maintenance"));
    }

    @Test
    void createRentalPickupInPastRejected() {
        RentalCreateRequest request = createRequest();
        request.setPickupDateTime(LocalDateTime.now().minusDays(1));
        request.setReturnDateTime(LocalDateTime.now().plusDays(1));

        assertThrows(InvalidRequestException.class,
                () -> rentalService.createRental(request));
    }

    @Test
    void createRentalReturnBeforePickupRejected() {
        RentalCreateRequest request = createRequest();
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(LocalDateTime.now().plusDays(4));

        assertThrows(InvalidRequestException.class,
                () -> rentalService.createRental(request));
    }

    @Test
    void createRentalReturnEqualToPickupRejected() {
        LocalDateTime pickup = LocalDateTime.now().plusDays(5);
        RentalCreateRequest request = createRequest();
        request.setPickupDateTime(pickup);
        request.setReturnDateTime(pickup);

        assertThrows(InvalidRequestException.class,
                () -> rentalService.createRental(request));
    }

    @Test
    void createRentalMinimumOneDay() {
        User user = customer();
        Vehicle vehicle = availableVehicle();
        stubHappyPath(user, vehicle);

        RentalCreateRequest request = createRequest();
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(request.getPickupDateTime().plusHours(1));

        RentalResponse response = rentalService.createRental(request);

        assertEquals(1, response.getNumberOfDays());
    }

    @Test
    void createRentalPartialDayRoundsUp() {
        User user = customer();
        Vehicle vehicle = availableVehicle();
        stubHappyPath(user, vehicle);

        RentalCreateRequest request = createRequest();
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(request.getPickupDateTime().plusHours(25));

        RentalResponse response = rentalService.createRental(request);

        assertEquals(2, response.getNumberOfDays());
    }

    @Test
    void createRentalRoundsUpToThreeDays() {
        User user = customer();
        Vehicle vehicle = availableVehicle();
        stubHappyPath(user, vehicle);

        RentalCreateRequest request = createRequest();
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(request.getPickupDateTime().plusHours(49));

        RentalResponse response = rentalService.createRental(request);

        assertEquals(3, response.getNumberOfDays());
    }

    @Test
    void createRentalOverlappingBookingRejected() {
        User user = customer();
        Vehicle vehicle = availableVehicle();
        authenticateAs(user);
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(vehicleRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(vehicle));
        when(rentalRepository.findExpiredPendingRentals(eq(RentalStatus.PENDING), any()))
                .thenReturn(List.of());
        when(rentalRepository.countOverlappingBlockingRentals(anyLong(), any(), any(), any()))
                .thenReturn(1L);

        assertThrows(DuplicateResourceException.class,
                () -> rentalService.createRental(createRequest()));
    }

    @Test
    void createRentalMaintenanceConflictRejected() {
        User user = customer();
        Vehicle vehicle = availableVehicle();
        authenticateAs(user);
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(vehicleRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(vehicle));
        when(rentalRepository.findExpiredPendingRentals(eq(RentalStatus.PENDING), any()))
                .thenReturn(List.of());
        when(rentalRepository.countOverlappingBlockingRentals(anyLong(), any(), any(), any()))
                .thenReturn(0L);
        when(maintenanceRepository.countConflictingMaintenance(anyLong(), any(), any()))
                .thenReturn(1L);

        assertThrows(DuplicateResourceException.class,
                () -> rentalService.createRental(createRequest()));
    }

    @Test
    void createRentalByAdminForbidden() {
        User admin = admin();
        authenticateAs(admin);
        when(vehicleRepository.findByIdWithPessimisticLock(1L))
                .thenReturn(Optional.of(availableVehicle()));
        when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));

        assertThrows(ForbiddenException.class,
                () -> rentalService.createRental(createRequest()));
    }

    @Test
    void checkAvailabilityAvailable() {
        Vehicle vehicle = availableVehicle();
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle));
        when(rentalRepository.countOverlappingBlockingRentals(anyLong(), any(), any(), any()))
                .thenReturn(0L);
        when(maintenanceRepository.countConflictingMaintenance(anyLong(), any(), any()))
                .thenReturn(0L);

        AvailabilityResponse response = rentalService.checkAvailability(1L,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));

        assertTrue(response.isAvailable());
        assertEquals("AVAILABLE", response.getVehicleStatus());
        assertEquals("MH05AB1234", response.getRegistrationNumber());
        assertEquals(1L, response.getVehicleId());
    }

    @Test
    void checkAvailabilityVehicleNotFound() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> rentalService.checkAvailability(99L,
                        LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7)));
    }

    @Test
    void checkAvailabilityInvalidDatesRejected() {
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(availableVehicle()));

        assertThrows(InvalidRequestException.class,
                () -> rentalService.checkAvailability(1L,
                        LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)));
    }

    @Test
    void checkAvailabilityRentedVehicleNotAvailable() {
        Vehicle vehicle = availableVehicle();
        vehicle.setStatus(VehicleStatus.RENTED);
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle));

        AvailabilityResponse response = rentalService.checkAvailability(1L,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));

        assertFalse(response.isAvailable());
        assertTrue(response.getMessage().contains("rented"));
    }

    @Test
    void checkAvailabilityInactiveVehicleNotAvailable() {
        Vehicle vehicle = availableVehicle();
        vehicle.setStatus(VehicleStatus.INACTIVE);
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle));

        AvailabilityResponse response = rentalService.checkAvailability(1L,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));

        assertFalse(response.isAvailable());
        assertTrue(response.getMessage().contains("inactive"));
    }

    @Test
    void checkAvailabilityMaintenanceVehicleNotAvailable() {
        Vehicle vehicle = availableVehicle();
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle));

        AvailabilityResponse response = rentalService.checkAvailability(1L,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));

        assertFalse(response.isAvailable());
        assertTrue(response.getMessage().contains("maintenance"));
    }

    @Test
    void checkAvailabilityOverlapConflict() {
        Vehicle vehicle = availableVehicle();
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle));
        when(rentalRepository.countOverlappingBlockingRentals(anyLong(), any(), any(), any()))
                .thenReturn(2L);

        AvailabilityResponse response = rentalService.checkAvailability(1L,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));

        assertFalse(response.isAvailable());
        assertTrue(response.getMessage().contains("booked"));
    }

    @Test
    void checkAvailabilityMaintenanceConflict() {
        Vehicle vehicle = availableVehicle();
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle));
        when(rentalRepository.countOverlappingBlockingRentals(anyLong(), any(), any(), any()))
                .thenReturn(0L);
        when(maintenanceRepository.countConflictingMaintenance(anyLong(), any(), any()))
                .thenReturn(1L);

        AvailabilityResponse response = rentalService.checkAvailability(1L,
                LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7));

        assertFalse(response.isAvailable());
        assertTrue(response.getMessage().contains("maintenance"));
    }

    @Test
    void getMyRentalsReturnsCurrentUserRentals() {
        User user = customer();
        authenticateAs(user);
        Rental rental = buildRental(user, availableVehicle());
        when(rentalRepository.findByUserUserIdOrderByPickupDatetimeDesc(1L))
                .thenReturn(List.of(rental));

        List<RentalResponse> rentals = rentalService.getMyRentals();

        assertEquals(1, rentals.size());
        assertEquals(10L, rentals.get(0).getRentalId());
        assertEquals(1L, rentals.get(0).getUserId());
        verify(rentalRepository).findByUserUserIdOrderByPickupDatetimeDesc(1L);
    }

    @Test
    void getRentalDetailsOwnRentalAllowed() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(rentalRepository.findById(10L)).thenReturn(Optional.of(buildRental(user, availableVehicle())));

        RentalResponse response = rentalService.getRentalDetails(10L);

        assertEquals(10L, response.getRentalId());
    }

    @Test
    void getRentalDetailsOtherUsersRentalForbidden() {
        User currentUser = customer();
        User owner = new User();
        owner.setUserId(50L);
        owner.setRole(UserRole.CUSTOMER);
        authenticateAs(currentUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(rentalRepository.findById(10L)).thenReturn(Optional.of(buildRental(owner, availableVehicle())));

        assertThrows(ForbiddenException.class,
                () -> rentalService.getRentalDetails(10L));
    }

    @Test
    void getRentalDetailsAdminCanViewAny() {
        User admin = admin();
        User owner = customer();
        authenticateAs(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(rentalRepository.findById(10L)).thenReturn(Optional.of(buildRental(owner, availableVehicle())));

        RentalResponse response = rentalService.getRentalDetails(10L);

        assertEquals(10L, response.getRentalId());
    }

    @Test
    void getRentalDetailsNotFound() {
        when(rentalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> rentalService.getRentalDetails(99L));
    }

    @Test
    void cancelRentalSuccess() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Rental rental = buildRental(user, availableVehicle());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        RentalResponse response = rentalService.cancelRental(10L);

        assertEquals("CANCELLED", response.getRentalStatus());
        assertEquals(RentalStatus.CANCELLED, rental.getRentalStatus());
    }

    @Test
    void cancelRentalAfterPickupRejected() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Rental rental = buildRental(user, availableVehicle());
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        rental.setPickupDatetime(LocalDateTime.now().minusDays(1));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.cancelRental(10L));
        assertTrue(exception.getMessage().contains("pickup time has passed"));
    }

    @Test
    void cancelRentalActiveRejected() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Rental rental = buildRental(user, availableVehicle());
        rental.setRentalStatus(RentalStatus.ACTIVE);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.cancelRental(10L));
        assertTrue(exception.getMessage().contains("after pickup has started"));
    }

    @Test
    void cancelRentalCompletedRejected() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Rental rental = buildRental(user, availableVehicle());
        rental.setRentalStatus(RentalStatus.COMPLETED);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.cancelRental(10L));
        assertTrue(exception.getMessage().contains("Completed"));
    }

    @Test
    void cancelRentalAlreadyCancelledRejected() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Rental rental = buildRental(user, availableVehicle());
        rental.setRentalStatus(RentalStatus.CANCELLED);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.cancelRental(10L));
        assertTrue(exception.getMessage().contains("already cancelled"));
    }

    @Test
    void cancelRentalOtherUsersRentalForbidden() {
        User currentUser = customer();
        User owner = new User();
        owner.setUserId(50L);
        owner.setRole(UserRole.CUSTOMER);
        authenticateAs(currentUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(buildRental(owner, availableVehicle())));

        assertThrows(ForbiddenException.class,
                () -> rentalService.cancelRental(10L));
    }

    @Test
    void cancelRentalByNonOwnerAdminForbidden() {
        User admin = admin();
        authenticateAs(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(buildRental(customer(), availableVehicle())));

        assertThrows(ForbiddenException.class,
                () -> rentalService.cancelRental(10L));
    }

    @Test
    void cancelRentalNotFound() {
        when(rentalRepository.findByIdWithPessimisticLock(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> rentalService.cancelRental(99L));
    }

    // ------------------------------------------------------------------
    // Phase 8 promo release accounting
    // ------------------------------------------------------------------

    private PromoCode promoFixture() {
        PromoCode promo = new PromoCode();
        promo.setPromoId(50L);
        return promo;
    }

    @Test
    void cancelRentalReleasesPromoUsageForUnpaidRental() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Rental rental = buildRental(user, availableVehicle());
        rental.setPromoCode(promoFixture());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        rentalService.cancelRental(10L);

        assertEquals(RentalStatus.CANCELLED, rental.getRentalStatus());
        verify(promoCodeRepository).releasePromoUsage(50L);
    }

    @Test
    void cancelConfirmedPaidRentalKeepsConsumedPromoUsage() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Rental rental = buildRental(user, availableVehicle());
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        rental.setPromoCode(promoFixture());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        rentalService.cancelRental(10L);

        assertEquals(RentalStatus.CANCELLED, rental.getRentalStatus());
        verify(promoCodeRepository, never()).releasePromoUsage(any());
    }

    @Test
    void cancelRentalWithoutPromoDoesNotTouchPromoUsage() {
        User user = customer();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Rental rental = buildRental(user, availableVehicle());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        rentalService.cancelRental(10L);

        verify(promoCodeRepository, never()).releasePromoUsage(any());
    }

    @Test
    void expiredRentalSweepCancelsAndReleasesPromoOnce() {
        User user = customer();
        Vehicle vehicle = availableVehicle();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(vehicleRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(vehicle));
        Rental expired = buildRental(user, vehicle);
        expired.setPromoCode(promoFixture());
        when(rentalRepository.findExpiredPendingRentals(eq(RentalStatus.PENDING), any()))
                .thenReturn(List.of(expired));
        when(rentalRepository.cancelRentalIfPending(
                eq(10L), eq(RentalStatus.PENDING), eq(RentalStatus.CANCELLED))).thenReturn(1);
        when(rentalRepository.countOverlappingBlockingRentals(anyLong(), any(), any(), any()))
                .thenReturn(0L);
        when(maintenanceRepository.countConflictingMaintenance(anyLong(), any(), any()))
                .thenReturn(0L);
        when(rentalRepository.save(any(Rental.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        rentalService.createRental(createRequest());

        verify(rentalRepository).cancelRentalIfPending(
                eq(10L), eq(RentalStatus.PENDING), eq(RentalStatus.CANCELLED));
        verify(promoCodeRepository).releasePromoUsage(50L);
    }

    @Test
    void expiredRentalSweepSkipsReleaseWhenTransitionAlreadyDoneElsewhere() {
        User user = customer();
        Vehicle vehicle = availableVehicle();
        authenticateAs(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(vehicleRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(vehicle));
        Rental expired = buildRental(user, vehicle);
        expired.setPromoCode(promoFixture());
        when(rentalRepository.findExpiredPendingRentals(eq(RentalStatus.PENDING), any()))
                .thenReturn(List.of(expired));
        when(rentalRepository.cancelRentalIfPending(
                eq(10L), eq(RentalStatus.PENDING), eq(RentalStatus.CANCELLED))).thenReturn(0);
        when(rentalRepository.countOverlappingBlockingRentals(anyLong(), any(), any(), any()))
                .thenReturn(0L);
        when(maintenanceRepository.countConflictingMaintenance(anyLong(), any(), any()))
                .thenReturn(0L);
        when(rentalRepository.save(any(Rental.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        rentalService.createRental(createRequest());

        verify(promoCodeRepository, never()).releasePromoUsage(any());
    }

    // ------------------------------------------------------------------
    // Phase 9: admin rental operations (status matrix + return)
    // ------------------------------------------------------------------

    private RentalStatusRequest statusRequest(String status) {
        RentalStatusRequest request = new RentalStatusRequest();
        request.setStatus(status);
        return request;
    }

    @Test
    void adminGetAllRentalsReturnsEveryRentalInRepositoryOrder() {
        Rental first = buildRental(customer(), availableVehicle());
        Rental second = buildRental(customer(), availableVehicle());
        second.setRentalId(11L);
        second.setRentalStatus(RentalStatus.ACTIVE);
        when(rentalRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(second, first));

        List<RentalResponse> result = rentalService.getAllRentals();

        assertEquals(2, result.size());
        assertEquals(11L, result.get(0).getRentalId());
        assertEquals("ACTIVE", result.get(0).getRentalStatus());
        assertEquals("PENDING", result.get(1).getRentalStatus());
    }

    @Test
    void adminPendingToCancelledSucceedsAndReleasesPromoUsage() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setPromoCode(promoFixture());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        RentalResponse response =
                rentalService.updateRentalStatusForAdmin(10L, statusRequest("cancelled"));

        assertEquals("CANCELLED", response.getRentalStatus());
        assertEquals(RentalStatus.CANCELLED, rental.getRentalStatus());
        verify(promoCodeRepository).releasePromoUsage(50L);
    }

    @Test
    void adminConfirmedToCancelledSucceedsAndKeepsConsumedPromoUsage() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        rental.setPromoCode(promoFixture());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        RentalResponse response =
                rentalService.updateRentalStatusForAdmin(10L, statusRequest("CANCELLED"));

        assertEquals("CANCELLED", response.getRentalStatus());
        verify(promoCodeRepository, never()).releasePromoUsage(any());
    }

    @Test
    void adminConfirmedToActiveSucceedsWhenPickupReached() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        rental.setPickupDatetime(LocalDateTime.now().minusHours(1));
        LocalDateTime pickupBefore = rental.getPickupDatetime();
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        RentalResponse response =
                rentalService.updateRentalStatusForAdmin(10L, statusRequest("ACTIVE"));

        assertEquals("ACTIVE", response.getRentalStatus());
        assertEquals(RentalStatus.ACTIVE, rental.getRentalStatus());
        assertEquals(pickupBefore, rental.getPickupDatetime()); // activation never rewrites pickup
        verify(promoCodeRepository, never()).releasePromoUsage(any());
    }

    @Test
    void adminConfirmedToActiveBeforePickupRejected() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.CONFIRMED); // pickup remains now + 5 days
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("ACTIVE")));

        assertTrue(exception.getMessage().contains("pickup time"));
        assertEquals(RentalStatus.CONFIRMED, rental.getRentalStatus());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminPendingToConfirmedRejectedByAdmin() {
        Rental rental = buildRental(customer(), availableVehicle());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("CONFIRMED")));

        assertTrue(exception.getMessage().contains("confirmed through successful payment"));
        assertEquals(RentalStatus.PENDING, rental.getRentalStatus());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminPendingToActiveRejected() {
        Rental rental = buildRental(customer(), availableVehicle());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("ACTIVE")));

        assertEquals("Invalid rental status transition", exception.getMessage());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminPendingToCompletedRejected() {
        Rental rental = buildRental(customer(), availableVehicle());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("COMPLETED")));

        assertEquals("Invalid rental status transition", exception.getMessage());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminActiveToCompletedViaStatusRejected() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.ACTIVE);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("COMPLETED")));

        assertTrue(exception.getMessage().contains("return endpoint"));
        assertEquals(RentalStatus.ACTIVE, rental.getRentalStatus());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminActiveToCancelledViaStatusRejected() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.ACTIVE);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("CANCELLED")));

        assertTrue(exception.getMessage().contains("return endpoint"));
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminCompletedStatusCannotBeChanged() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.COMPLETED);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("CANCELLED")));

        assertTrue(exception.getMessage().contains("Completed"));
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminCancelledStatusCannotBeChanged() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.CANCELLED);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("ACTIVE")));

        assertTrue(exception.getMessage().contains("Cancelled"));
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminSameStatusRejected() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("CONFIRMED")));

        assertTrue(exception.getMessage().contains("already CONFIRMED"));
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminStatusInvalidEnumValueRejectedBeforeAnyDatabaseAccess() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("BOGUS")));

        assertEquals("Invalid rental status", exception.getMessage());
        verify(rentalRepository, never()).findByIdWithPessimisticLock(anyLong());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminStatusBlankRejected() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.updateRentalStatusForAdmin(10L, statusRequest("   ")));

        assertEquals("Invalid rental status", exception.getMessage());
        verify(rentalRepository, never()).findByIdWithPessimisticLock(anyLong());
    }

    @Test
    void adminStatusUnknownRentalNotFound() {
        when(rentalRepository.findByIdWithPessimisticLock(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> rentalService.updateRentalStatusForAdmin(99L, statusRequest("CANCELLED")));
    }

    @Test
    void adminReturnCompletesActiveRentalSetsActualReturnTimeWithoutRepricing() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.ACTIVE);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        LocalDateTime before = LocalDateTime.now();
        RentalResponse response = rentalService.returnRentalForAdmin(10L);
        LocalDateTime after = LocalDateTime.now();

        assertEquals("COMPLETED", response.getRentalStatus());
        assertNotNull(response.getActualReturnDatetime());
        assertFalse(response.getActualReturnDatetime().isBefore(before));
        assertFalse(response.getActualReturnDatetime().isAfter(after));

        // Pricing is immutable on return: no repricing, no late fees.
        assertEquals(0, response.getSubtotal().compareTo(new BigDecimal("2400.00")));
        assertEquals(0, response.getDiscountAmount().compareTo(new BigDecimal("0.00")));
        assertEquals(0, response.getTaxAmount().compareTo(new BigDecimal("432.00")));
        assertEquals(0, response.getSecurityDeposit().compareTo(new BigDecimal("480.00")));
        assertEquals(0, response.getTotalAmount().compareTo(new BigDecimal("3312.00")));
        assertEquals(RentalStatus.COMPLETED, rental.getRentalStatus());
    }

    @Test
    void adminReturnRejectedForConfirmedRental() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.CONFIRMED);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> rentalService.returnRentalForAdmin(10L));

        assertTrue(exception.getMessage().contains("Only active rentals"));
        assertNull(rental.getActualReturnDatetime());
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminReturnRejectedForPendingRental() {
        Rental rental = buildRental(customer(), availableVehicle());
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        assertThrows(InvalidRequestException.class,
                () -> rentalService.returnRentalForAdmin(10L));
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminReturnRejectedForCompletedRental() {
        Rental rental = buildRental(customer(), availableVehicle());
        rental.setRentalStatus(RentalStatus.COMPLETED);
        when(rentalRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(rental));

        assertThrows(InvalidRequestException.class,
                () -> rentalService.returnRentalForAdmin(10L));
        verify(rentalRepository, never()).save(any());
    }

    @Test
    void adminReturnUnknownRentalNotFound() {
        when(rentalRepository.findByIdWithPessimisticLock(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> rentalService.returnRentalForAdmin(99L));
    }
}
