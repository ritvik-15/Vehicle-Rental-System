package com.vehicle.rental.service;

import com.vehicle.rental.dto.PasswordChangeRequest;
import com.vehicle.rental.dto.ProfileUpdateRequest;
import com.vehicle.rental.dto.UserProfileResponse;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase 11A unit tests: principal-derived identity (never request input),
 * CUSTOMER-only gating (ADMIN -> 403), own-phone vs duplicate-phone (409),
 * password rules (wrong current -> 400, equal new -> 400, BCrypt encode on
 * success) and the invariant that email/role/status are untouched and no
 * credential ever reaches a response.
 */
@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserAccountService userAccountService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    private User customerFixture(Long id, String phone) {
        User user = new User();
        user.setUserId(id);
        user.setFullName("Asha Patil");
        user.setEmail("asha" + id + "@example.com");
        user.setPhone(phone);
        user.setPasswordHash("$2a$10$ExistingHashThatMustNeverLeak000000");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setAddress("12 MG Road");
        user.setCity("Pune");
        user.setState("MH");
        user.setPincode("411001");
        user.setCreatedAt(LocalDateTime.now().minusDays(3));
        return user;
    }

    private ProfileUpdateRequest updateRequest(String fullName, String phone) {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setFullName(fullName);
        request.setPhone(phone);
        request.setAddress(null);
        request.setCity(" ");
        request.setState("Karnataka");
        request.setPincode("560001");
        return request;
    }

    private PasswordChangeRequest passwordRequest(String current, String next) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    // ------------------------------------------------------------------
    // Identity & access
    // ------------------------------------------------------------------

    @Test
    void profileUsesPrincipalDerivedUserIdOnly() {
        authenticateAs("7");
        User fixture = customerFixture(7L, "9812345678");
        when(userRepository.findById(7L)).thenReturn(Optional.of(fixture));

        UserProfileResponse response = userAccountService.getProfile();

        assertEquals(7L, response.getUserId());
        verify(userRepository).findById(7L);
    }

    @Test
    void missingUserForPrincipalThrowsNotFound() {
        authenticateAs("99");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userAccountService.getProfile());
    }

    @Test
    void adminPrincipalIsForbiddenOnAllThreeOperations() {
        authenticateAs("1");
        User admin = customerFixture(1L, "9800000000");
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(ForbiddenException.class, () -> userAccountService.getProfile());
        assertThrows(ForbiddenException.class, () -> userAccountService.updateProfile(
                updateRequest("Admin", "9800000000")));
        assertThrows(ForbiddenException.class, () -> userAccountService.changePassword(
                passwordRequest("OldDemo@2026", "NewDemo@2026")));
    }

    // ------------------------------------------------------------------
    // Profile response safety
    // ------------------------------------------------------------------

    @Test
    void profileResponseContainsNoCredentialField() {
        for (var field : UserProfileResponse.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("password") || name.contains("hash") || name.contains("secret"),
                    "UserProfileResponse must not declare credential field: " + field.getName());
        }
    }

    @Test
    void profileMapsAllApprovedFields() {
        authenticateAs("7");
        when(userRepository.findById(7L)).thenReturn(Optional.of(customerFixture(7L, "9812345678")));

        UserProfileResponse response = userAccountService.getProfile();

        assertEquals("Asha Patil", response.getFullName());
        assertEquals("asha7@example.com", response.getEmail());
        assertEquals("9812345678", response.getPhone());
        assertEquals("12 MG Road", response.getAddress());
        assertEquals("Pune", response.getCity());
        assertEquals("MH", response.getState());
        assertEquals("411001", response.getPincode());
        assertEquals("CUSTOMER", response.getRole());
        assertEquals("ACTIVE", response.getStatus());
        assertNotNull(response.getCreatedAt());
    }

    // ------------------------------------------------------------------
    // Profile update
    // ------------------------------------------------------------------

    @Test
    void updatePersistsEditableFieldsAndLeavesImmutableOnesUntouched() {
        authenticateAs("7");
        User fixture = customerFixture(7L, "9812345678");
        when(userRepository.findById(7L)).thenReturn(Optional.of(fixture));
        when(userRepository.existsByPhoneAndUserIdNot(anyString(), anyLong())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse response = userAccountService.updateProfile(
                updateRequest("Asha Updated", "9811112222"));

        assertEquals("Asha Updated", fixture.getFullName());
        assertEquals("9811112222", fixture.getPhone());
        assertNull(fixture.getAddress(), "null address clears the value");
        assertNull(fixture.getCity(), "blank city clears the value");
        assertEquals("Karnataka", fixture.getState());
        assertEquals("560001", fixture.getPincode());
        // Immutable fields untouched:
        assertEquals("asha7@example.com", fixture.getEmail());
        assertEquals(UserRole.CUSTOMER, fixture.getRole());
        assertEquals(UserStatus.ACTIVE, fixture.getStatus());
        assertEquals("$2a$10$ExistingHashThatMustNeverLeak000000", fixture.getPasswordHash());
        assertEquals("asha7@example.com", response.getEmail());
    }

    @Test
    void ownExistingPhoneIsAccepted() {
        authenticateAs("7");
        User fixture = customerFixture(7L, "9812345678");
        when(userRepository.findById(7L)).thenReturn(Optional.of(fixture));
        when(userRepository.existsByPhoneAndUserIdNot("9812345678", 7L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse response = userAccountService.updateProfile(
                updateRequest("Asha Patil", "9812345678"));

        assertEquals("9812345678", response.getPhone());
        verify(userRepository).existsByPhoneAndUserIdNot("9812345678", 7L);
    }

    @Test
    void anotherUsersPhoneIsRejectedWithConflict() {
        authenticateAs("7");
        User fixture = customerFixture(7L, "9812345678");
        when(userRepository.findById(7L)).thenReturn(Optional.of(fixture));
        when(userRepository.existsByPhoneAndUserIdNot("9988776655", 7L)).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> userAccountService.updateProfile(updateRequest("Asha Patil", "9988776655")));
        assertEquals("Phone already exists", ex.getMessage());
        assertEquals("9812345678", fixture.getPhone(), "conflict must not mutate the entity");
        verify(userRepository, never()).save(any(User.class));
    }

    // ------------------------------------------------------------------
    // Password change
    // ------------------------------------------------------------------

    @Test
    void wrongCurrentPasswordIsRejectedWithoutEncoding() {
        authenticateAs("7");
        User fixture = customerFixture(7L, "9812345678");
        when(userRepository.findById(7L)).thenReturn(Optional.of(fixture));
        when(passwordEncoder.matches("WrongOld", fixture.getPasswordHash())).thenReturn(false);

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> userAccountService.changePassword(passwordRequest("WrongOld", "NewDemo@2026")));
        assertEquals("Current password is incorrect", ex.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void newPasswordEqualToCurrentIsRejected() {
        authenticateAs("7");
        User fixture = customerFixture(7L, "9812345678");
        when(userRepository.findById(7L)).thenReturn(Optional.of(fixture));
        when(passwordEncoder.matches("SameDemo@2026", fixture.getPasswordHash())).thenReturn(true);

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> userAccountService.changePassword(passwordRequest("SameDemo@2026", "SameDemo@2026")));
        assertEquals("New password must be different from the current password", ex.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void successfulChangeEncodesNewPasswordAndTouchesNothingElse() {
        authenticateAs("7");
        User fixture = customerFixture(7L, "9812345678");
        when(userRepository.findById(7L)).thenReturn(Optional.of(fixture));
        when(passwordEncoder.matches("OldDemo@2026", fixture.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("NewDemo@2026", fixture.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode("NewDemo@2026")).thenReturn("$2a$10$FreshlyEncodedHashValue0000000000");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = userAccountService.changePassword(passwordRequest("OldDemo@2026", "NewDemo@2026"));

        assertEquals("Password updated successfully", response.get("message"));
        assertEquals("$2a$10$FreshlyEncodedHashValue0000000000", fixture.getPasswordHash());
        verify(passwordEncoder).encode("NewDemo@2026");
        verify(userRepository).save(fixture);
        // Untouched invariants:
        assertEquals("asha7@example.com", fixture.getEmail());
        assertEquals(UserRole.CUSTOMER, fixture.getRole());
        assertEquals(UserStatus.ACTIVE, fixture.getStatus());
        assertEquals("Asha Patil", fixture.getFullName());
        assertEquals("9812345678", fixture.getPhone());
        // The response map carries no password material:
        assertFalse(response.containsKey("password"));
        assertFalse(response.containsKey("passwordHash"));
        assertFalse(response.containsKey("newPassword"));
        assertFalse(response.containsKey("currentPassword"));
    }
}
