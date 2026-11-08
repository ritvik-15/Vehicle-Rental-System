package com.vehicle.rental.service;

import com.vehicle.rental.dto.CustomerResponse;
import com.vehicle.rental.dto.CustomerStatusRequest;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 9 customer management (ADMIN only — enforced by /api/admin/** in
 * SecurityConfig). These endpoints operate exclusively on CUSTOMER accounts:
 * an ADMIN user is never visible or modifiable through them. password_hash is
 * never included in any response.
 */
@Service
public class CustomerService {

    private final UserRepository userRepository;

    public CustomerService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** All CUSTOMER accounts, createdAt DESC. No pagination, filters or sorting params. */
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return userRepository.findAllByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerDetails(Long userId) {
        return toResponse(findCustomer(userId));
    }

    /**
     * Sets ACTIVE/INACTIVE (the existing UserStatus enum values, as-is).
     * Only CUSTOMER accounts can be updated — ADMIN accounts return 404 here.
     */
    @Transactional
    public CustomerResponse updateCustomerStatus(Long userId, CustomerStatusRequest request) {
        User customer = findCustomer(userId);

        UserStatus newStatus;
        try {
            newStatus = UserStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid customer status");
        }

        customer.setStatus(newStatus);
        return toResponse(userRepository.save(customer));
    }

    /**
     * A missing user and a non-CUSTOMER user are both reported as
     * "Customer not found" so these endpoints reveal nothing about ADMIN
     * accounts.
     */
    private User findCustomer(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ResourceNotFoundException("Customer not found");
        }
        return user;
    }

    private CustomerResponse toResponse(User user) {
        CustomerResponse response = new CustomerResponse();
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
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}
