package com.vehicle.rental.repository;

import com.vehicle.rental.entity.Rental;
import com.vehicle.rental.enums.RentalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RentalRepository extends JpaRepository<Rental, Long> {

    List<Rental> findByUserUserIdOrderByPickupDatetimeDesc(Long userId);

    List<Rental> findByVehicleVehicleIdOrderByPickupDatetimeAsc(Long vehicleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Rental r WHERE r.rentalId = :rentalId")
    Optional<Rental> findByIdWithPessimisticLock(@Param("rentalId") Long rentalId);

    @Query("SELECT COUNT(r) FROM Rental r WHERE r.vehicle.vehicleId = :vehicleId"
            + " AND r.pickupDatetime < :returnDatetime"
            + " AND r.returnDatetime > :pickupDatetime"
            + " AND r.rentalStatus IN ('PENDING', 'CONFIRMED', 'ACTIVE')"
            + " AND NOT (r.rentalStatus = 'PENDING' AND r.createdAt < :expiryThreshold)")
    long countOverlappingBlockingRentals(@Param("vehicleId") Long vehicleId,
                                           @Param("pickupDatetime") LocalDateTime pickupDatetime,
                                           @Param("returnDatetime") LocalDateTime returnDatetime,
                                           @Param("expiryThreshold") LocalDateTime expiryThreshold);

    /**
     * Phase 9 admin rental list: all rentals, createdAt DESC, no filters.
     */
    List<Rental> findAllByOrderByCreatedAtDesc();

    /**
     * Phase 8 (global expiry sweep): all expired PENDING rentals, regardless of
     * vehicle. Used with {@link #cancelRentalIfPending} so each rental is
     * transitioned at most once and its promo reservation released exactly once.
     */
    @Query("SELECT r FROM Rental r WHERE r.rentalStatus = :status AND r.createdAt < :expiryThreshold")
    List<Rental> findExpiredPendingRentals(@Param("status") RentalStatus status,
                                            @Param("expiryThreshold") LocalDateTime expiryThreshold);

    /**
     * Conditional PENDING -> CANCELLED transition; returns 1 only for the caller
     * that actually performed it, which makes concurrent sweeps release promo
     * usage exactly once (row count, not snapshot state, is the guard).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Rental r SET r.rentalStatus = :cancelled"
            + " WHERE r.rentalId = :rentalId AND r.rentalStatus = :pending")
    int cancelRentalIfPending(@Param("rentalId") Long rentalId,
                              @Param("pending") RentalStatus pending,
                              @Param("cancelled") RentalStatus cancelled);

    /** Phase 10 rentals report: count for every stored rental status. */
    @Query("SELECT r.rentalStatus, COUNT(r) FROM Rental r GROUP BY r.rentalStatus")
    List<Object[]> findRentalStatusCounts();
}
