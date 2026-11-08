package com.vehicle.rental.service;

import com.vehicle.rental.repository.MaintenanceRepository;
import com.vehicle.rental.repository.PromoCodeRepository;
import com.vehicle.rental.repository.RentalRepository;
import com.vehicle.rental.repository.UserRepository;
import com.vehicle.rental.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * R1-A: verifies the automatic pending-rental expiry sweep without
 * any Spring context or database: the scheduling annotation, the
 * delegation to the existing reconciliation core, failure
 * containment, and the idempotent (conditional-update) behavior.
 */
@ExtendWith(MockitoExtension.class)
class RentalExpirySchedulingTest {

    @Mock
    private RentalRepository rentalRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MaintenanceRepository maintenanceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PromoService promoService;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @InjectMocks
    private RentalService rentalService;

    @Test
    void sweepIsScheduledEveryFiveMinutes() throws Exception {
        Method method = RentalService.class.getDeclaredMethod("sweepExpiredPendingRentals");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "sweepExpiredPendingRentals must carry @Scheduled");
        assertEquals(300000L, scheduled.fixedRate(), "sweep interval must be five minutes");
    }

    @Test
    void sweepDelegatesToExistingReconciliationCore() {
        RentalService spyService = spy(rentalService);
        doNothing().when(spyService).reconcileExpiredPendingRentals(any());

        spyService.sweepExpiredPendingRentals();

        verify(spyService).reconcileExpiredPendingRentals(any());
    }

    @Test
    void sweepFailureIsContainedAndDoesNotPropagate() {
        RentalService spyService = spy(rentalService);
        doThrow(new RuntimeException("simulated sweep failure"))
                .when(spyService).reconcileExpiredPendingRentals(any());

        assertDoesNotThrow(spyService::sweepExpiredPendingRentals);
    }

    @Test
    void sweepWithNoExpiredRentalsIsIdempotentNoOp() {
        when(rentalRepository.findExpiredPendingRentals(any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() ->
                rentalService.reconcileExpiredPendingRentals(LocalDateTime.now()));

        verify(rentalRepository, never()).cancelRentalIfPending(anyLong(), any(), any());
        verify(promoCodeRepository, never()).releasePromoUsage(anyLong());
    }
}
