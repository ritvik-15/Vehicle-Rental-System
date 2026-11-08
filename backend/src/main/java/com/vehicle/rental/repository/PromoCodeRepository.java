package com.vehicle.rental.repository;

import com.vehicle.rental.entity.PromoCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromoCode p WHERE p.code = :code")
    Optional<PromoCode> findByCodeWithPessimisticLock(@Param("code") String code);

    /**
     * Phase 8 promo reconciliation: releases one reserved usage for an unpaid
     * rental that was cancelled/expired. Single atomic UPDATE guarded by
     * usedCount > 0 so usage is never negative and concurrent releases cannot
     * lose updates.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PromoCode p SET p.usedCount = p.usedCount - 1"
            + " WHERE p.promoId = :promoId AND p.usedCount > 0")
    int releasePromoUsage(@Param("promoId") Long promoId);
}
