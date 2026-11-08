package com.vehicle.rental.service;

import com.vehicle.rental.dto.PasswordChangeRequest;
import com.vehicle.rental.dto.ProfileUpdateRequest;
import com.vehicle.rental.dto.UserProfileResponse;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.UserRepository;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 11A customer self-service: profile read/update and password change.
 * Identity comes exclusively from the authenticated JWT principal in the
 * SecurityContext — no userId is ever accepted from the request. ADMIN
 * principals are rejected with ForbiddenException (403). Profile responses
 * carry no credential field, and only the six editable profile fields (plus
 * the password hash in the dedicated change flow) are ever written.
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile() {
        return toResponse(getCurrentCustomer());
    }

    @Transactional
    public UserProfileResponse updateProfile(ProfileUpdateRequest request) {
        User user = getCurrentCustomer();
        if (userRepository.existsByPhoneAndUserIdNot(request.getPhone(), user.getUserId())) {
            throw new DuplicateResourceException("Phone already exists");
        }
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setAddress(optional(request.getAddress()));
        user.setCity(optional(request.getCity()));
        user.setState(optional(request.getState()));
        user.setPincode(optional(request.getPincode()));
        // email, role, status and passwordHash are never touched here.
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public Map<String, String> changePassword(PasswordChangeRequest request) {
        User user = getCurrentCustomer();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // email, role, status and profile fields are never touched here.
        userRepository.save(user);
        return Map.of("message", "Password updated successfully");
    }

    /**
     * Resolves the caller from the SecurityContext principal (userId string
     * set by JwtAuthenticationFilter) — never from any request input — and
     * enforces the CUSTOMER-only self-service rule.
     */
    private User getCurrentCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new InvalidRequestException("Authentication required");
        }
        Long userId = Long.valueOf(String.valueOf(authentication.getPrincipal()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ForbiddenException("Self-service account management is available to customers only");
        }
        return user;
    }

    private UserProfileResponse toResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getUserId());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setAddress(user.getAddress());
        response.setCity(user.getCity());
        response.setState(user.getState());
        response.setPincode(user.getPincode());
        response.setRole(user.getRole().name());
        response.setStatus(user.getStatus().name());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

    /** Optional profile fields: null or blank input clears the stored value. */
    private String optional(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
