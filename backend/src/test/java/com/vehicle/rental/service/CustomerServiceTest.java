package com.vehicle.rental.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.CustomerResponse;
import com.vehicle.rental.dto.CustomerStatusRequest;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 9 unit tests: CUSTOMER-only scoping (admins are never visible or
 * modifiable), status parsing of the existing UserStatus values, and
 * password_hash never leaking into any response payload.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomerService customerService;

    private User customerFixture(Long id) {
        User user = new User();
        user.setUserId(id);
        user.setFullName("Asha Patil");
        user.setEmail("asha" + id + "@example.com");
        user.setPhone("98123456" + id);
        user.setPasswordHash("$2a$10$VerySecretHashValueThatMustNeverLeak");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now().minusDays(1));
        user.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return user;
    }

    private User adminFixture(Long id) {
        User user = customerFixture(id);
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private CustomerStatusRequest statusRequest(String status) {
        CustomerStatusRequest request = new CustomerStatusRequest();
        request.setStatus(status);
        return request;
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    @Test
    void getAllCustomersQueriesOnlyCustomerRoleAndMapsAllFields() {
        User first = customerFixture(7L);
        User second = customerFixture(8L);
        when(userRepository.findAllByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER))
                .thenReturn(List.of(second, first)); // repository owns ordering

        List<CustomerResponse> result = customerService.getAllCustomers();

        assertEquals(2, result.size());
        assertEquals(8L, result.get(0).getUserId());
        assertEquals(7L, result.get(1).getUserId());
        assertEquals("CUSTOMER", result.get(0).getRole());
        assertEquals("ACTIVE", result.get(0).getStatus());
        assertEquals("Asha Patil", result.get(0).getFullName());
        verify(userRepository).findAllByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER);
    }

    @Test
    void getAllCustomersReturnsEmptyListWhenNoneExist() {
        when(userRepository.findAllByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER))
                .thenReturn(List.of());

        assertTrue(customerService.getAllCustomers().isEmpty());
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @Test
    void getCustomerDetailsReturnsCustomer() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerFixture(7L)));

        CustomerResponse response = customerService.getCustomerDetails(7L);

        assertEquals(7L, response.getUserId());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void getCustomerDetailsUnknownIdThrowsNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> customerService.getCustomerDetails(999L));
    }

    @Test
    void getCustomerDetailsForAdminAccountThrowsNotFound() {
        // The customer endpoints operate only on CUSTOMER accounts; an ADMIN
        // id must look exactly like a missing customer (404, no admin leak).
        when(userRepository.findById(5L)).thenReturn(Optional.of(adminFixture(5L)));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> customerService.getCustomerDetails(5L));
        assertEquals("Customer not found", exception.getMessage());
    }

    @Test
    void getCustomerDetailsNeverExposesPasswordHash() throws Exception {
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerFixture(7L)));

        CustomerResponse response = customerService.getCustomerDetails(7L);
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // java.time support (jsr310 on classpath)
        String json = mapper.writeValueAsString(response);

        assertFalse(json.contains("password"), "payload must not contain password fields");
        assertFalse(json.contains("$2a$10$"), "bcrypt hash must never be serialized");
        assertFalse(json.contains("VerySecretHashValue"));
    }

    // ------------------------------------------------------------------
    // Status update
    // ------------------------------------------------------------------

    @Test
    void updateCustomerStatusToInactivePersists() {
        User customer = customerFixture(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        CustomerResponse response = customerService.updateCustomerStatus(7L, statusRequest("INACTIVE"));

        assertEquals("INACTIVE", response.getStatus());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(UserStatus.INACTIVE, captor.getValue().getStatus());
    }

    @Test
    void updateCustomerStatusAcceptsLowercaseValue() {
        User customer = customerFixture(7L);
        customer.setStatus(UserStatus.INACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        CustomerResponse response = customerService.updateCustomerStatus(7L, statusRequest("active"));

        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void updateCustomerStatusInvalidValueRejectedAndNothingSaved() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerFixture(7L)));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> customerService.updateCustomerStatus(7L, statusRequest("SUSPENDED")));

        assertEquals("Invalid customer status", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateCustomerStatusBlankValueRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerFixture(7L)));

        assertThrows(InvalidRequestException.class,
                () -> customerService.updateCustomerStatus(7L, statusRequest("   ")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateCustomerStatusOnAdminAccountThrowsNotFoundAndNeverSaves() {
        // ADMIN users must never be suspended through the customer APIs.
        when(userRepository.findById(5L)).thenReturn(Optional.of(adminFixture(5L)));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> customerService.updateCustomerStatus(5L, statusRequest("INACTIVE")));

        assertEquals("Customer not found", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateCustomerStatusUnknownIdThrowsNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> customerService.updateCustomerStatus(999L, statusRequest("INACTIVE")));
        verify(userRepository, never()).save(any());
    }
}
