package com.vehicle.rental.repository;

import com.vehicle.rental.entity.Payment;
import com.vehicle.rental.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Locking read by the trusted stored Razorpay order ID — the first database
     * access of verification, so concurrent verifications of the same attempt
     * serialize and cannot double-process.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.razorpayOrderId = :orderId")
    Optional<Payment> findByRazorpayOrderIdWithPessimisticLock(@Param("orderId") String orderId);

    /**
     * Non-locking read by Razorpay order ID for lookup before acquiring locks.
     */
    @Query("SELECT p FROM Payment p WHERE p.razorpayOrderId = :orderId")
    Optional<Payment> findByRazorpayOrderId(@Param("orderId") String orderId);

    boolean existsByRentalRentalIdAndPaymentStatus(Long rentalId, PaymentStatus paymentStatus);

    List<Payment> findByRentalRentalIdAndPaymentStatus(Long rentalId, PaymentStatus paymentStatus);

    /**
     * Resolves the rental ID without loading the Rental entity, so a later
     * pessimistic lock on the rental is a genuine locking read.
     */
    @Query("SELECT p.rental.rentalId FROM Payment p WHERE p.paymentId = :paymentId")
    Long findRentalIdByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Resolves the rental ID by Razorpay order ID without loading the Payment entity.
     */
    @Query("SELECT p.rental.rentalId FROM Payment p WHERE p.razorpayOrderId = :orderId")
    Long findRentalIdByRazorpayOrderId(@Param("orderId") String orderId);

    /** Phase 9 admin payment list: all payments, createdAt DESC, no filters. */
    List<Payment> findAllByOrderByCreatedAtDesc();

    /**
     * Phase 10 revenue report: per-status transaction count and amount sum.
     * Revenue derives exclusively from the SUCCESS row — never rental totals.
     */
    @Query("SELECT p.paymentStatus, COUNT(p), SUM(p.amount) FROM Payment p GROUP BY p.paymentStatus")
    List<Object[]> findPaymentStatusCountsAndSums();
}
