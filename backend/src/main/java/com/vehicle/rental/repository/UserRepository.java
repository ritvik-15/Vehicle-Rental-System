package com.vehicle.rental.repository;

import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    /** Phase 9 admin customer list: CUSTOMER accounts only, createdAt DESC. */
    List<User> findAllByRoleOrderByCreatedAtDesc(UserRole role);

    /** Phase 10 summary: CUSTOMER account count (admins excluded). */
    long countByRole(UserRole role);

    /**
     * Phase 11A self-service profile update: phone uniqueness excluding the
     * current user, so keeping one's own phone is allowed while another
     * account's phone is rejected with 409.
     */
    boolean existsByPhoneAndUserIdNot(String phone, Long userId);
}
