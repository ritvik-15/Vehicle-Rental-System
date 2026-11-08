package com.vehicle.rental.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.PasswordChangeRequest;
import com.vehicle.rental.dto.ProfileUpdateRequest;
import com.vehicle.rental.dto.UserProfileResponse;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.service.AuthService;
import com.vehicle.rental.service.UserAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11A real-MySQL integration: own-record read, allowed-field persistence
 * with immutable email/role/status, excluding-self phone uniqueness, BCrypt
 * password round trip through the real login, and credential-free responses.
 * Class-level @Transactional rolls everything back — no leftover data.
 */
@SpringBootTest
@Transactional
class UserAccountIntegrationTest {

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private User customer;
    private User otherCustomer;
    private String customerPhone;
    private String otherPhone;
    private String oldPassword;

    @BeforeEach
    void setUp() {
        long unique = Math.abs(System.nanoTime() % 100000000L);
        customerPhone = "97" + String.format("%08d", unique);
        otherPhone = "98" + String.format("%08d", unique);
        oldPassword = "Phase11AOld@2026";

        customer = new User();
        customer.setFullName("Phase 11A Customer");
        customer.setEmail("phase11a" + unique + "1@example.com");
        customer.setPhone(customerPhone);
        customer.setPasswordHash(passwordEncoder.encode(oldPassword));
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        customer.setAddress("12 MG Road");
        customer.setCity("Pune");
        customer.setState("MH");
        customer.setPincode("411001");
        customer = userRepository.saveAndFlush(customer);

        otherCustomer = new User();
        otherCustomer.setFullName("Phase 11A Other");
        otherCustomer.setEmail("phase11a" + unique + "2@example.com");
        otherCustomer.setPhone(otherPhone);
        otherCustomer.setPasswordHash(passwordEncoder.encode("Other@2026"));
        otherCustomer.setRole(UserRole.CUSTOMER);
        otherCustomer.setStatus(UserStatus.ACTIVE);
        otherCustomer = userRepository.saveAndFlush(otherCustomer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        String role = user.getRole() == UserRole.ADMIN ? "ROLE_ADMIN" : "ROLE_CUSTOMER";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(user.getUserId()), null,
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private ProfileUpdateRequest updateRequest(String fullName, String phone,
                                                String address, String city, String state, String pincode) {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setFullName(fullName);
        request.setPhone(phone);
        request.setAddress(address);
        request.setCity(city);
        request.setState(state);
        request.setPincode(pincode);
        return request;
    }

    private PasswordChangeRequest passwordRequest(String current, String next) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    private String dbColumn(String column, Long userId) {
        userRepository.flush();
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM users WHERE user_id = ?", String.class, userId);
    }

    // ------------------------------------------------------------------
    // GET /me
    // ------------------------------------------------------------------

    @Test
    void getReturnsAuthenticatedUsersOwnRecordWithoutAnyCredential() throws Exception {
        authenticateAs(customer);

        UserProfileResponse response = userAccountService.getProfile();

        assertEquals(customer.getUserId(), response.getUserId());
        assertEquals("Phase 11A Customer", response.getFullName());
        assertEquals(customer.getEmail(), response.getEmail());
        assertEquals(customerPhone, response.getPhone());
        assertEquals("12 MG Road", response.getAddress());
        assertEquals("Pune", response.getCity());
        assertEquals("MH", response.getState());
        assertEquals("411001", response.getPincode());
        assertEquals("CUSTOMER", response.getRole());
        assertEquals("ACTIVE", response.getStatus());
        assertNotNull(response.getCreatedAt());

        String json = objectMapper.writeValueAsString(response);
        assertFalse(json.contains("passwordHash"), "JSON must not contain passwordHash");
        assertFalse(json.contains("\"password\""), "JSON must not contain a password field");
        assertFalse(json.contains(customer.getPasswordHash()), "JSON must not leak the stored hash");
    }

    @Test
    void adminCallingSelfServiceIsForbidden() {
        User admin = new User();
        admin.setFullName("Phase 11A Admin");
        admin.setEmail("phase11aadmin@example.com");
        admin.setPhone("96" + customerPhone.substring(2));
        admin.setPasswordHash(passwordEncoder.encode("Admin@2026"));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        User savedAdmin = userRepository.saveAndFlush(admin);

        authenticateAs(savedAdmin);

        assertThrows(ForbiddenException.class, () -> userAccountService.getProfile());
        assertThrows(ForbiddenException.class, () -> userAccountService.updateProfile(
                updateRequest("X", savedAdmin.getPhone(), null, null, null, null)));
        assertThrows(ForbiddenException.class, () -> userAccountService.changePassword(
                passwordRequest("Admin@2026", "NewAdmin@2026")));
    }

    // ------------------------------------------------------------------
    // PUT /me
    // ------------------------------------------------------------------

    @Test
    void updatePersistsAllowedFieldsAndKeepsEmailRoleStatusAndHash() {
        authenticateAs(customer);
        String originalEmail = customer.getEmail();
        String originalHash = dbColumn("password_hash", customer.getUserId());

        UserProfileResponse response = userAccountService.updateProfile(updateRequest(
                "Phase 11A Renamed", customerPhone, "42 Residency Road", "Bengaluru", "Karnataka", "560001"));

        userRepository.flush();
        assertEquals("Phase 11A Renamed", dbColumn("full_name", customer.getUserId()));
        assertEquals("42 Residency Road", dbColumn("address", customer.getUserId()));
        assertEquals("Bengaluru", dbColumn("city", customer.getUserId()));
        assertEquals("Karnataka", dbColumn("state", customer.getUserId()));
        assertEquals("560001", dbColumn("pincode", customer.getUserId()));
        // Immutable:
        assertEquals(originalEmail, dbColumn("email", customer.getUserId()));
        assertEquals("CUSTOMER", dbColumn("role", customer.getUserId()));
        assertEquals("ACTIVE", dbColumn("status", customer.getUserId()));
        assertEquals(originalHash, dbColumn("password_hash", customer.getUserId()));
        assertEquals(originalEmail, response.getEmail());
        assertEquals("CUSTOMER", response.getRole());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void blankOptionalFieldsAreClearedToNull() {
        authenticateAs(customer);

        userAccountService.updateProfile(updateRequest(
                "Phase 11A Customer", customerPhone, "", null, "  ", "411001"));

        assertNull(dbColumn("address", customer.getUserId()));
        assertNull(dbColumn("city", customer.getUserId()));
        assertNull(dbColumn("state", customer.getUserId()));
        assertEquals("411001", dbColumn("pincode", customer.getUserId()));
    }

    @Test
    void anotherCustomersPhoneIsRejectedWithConflict() {
        authenticateAs(customer);

        assertThrows(DuplicateResourceException.class, () -> userAccountService.updateProfile(
                updateRequest("Phase 11A Customer", otherPhone, null, null, null, null)));

        userRepository.flush();
        assertEquals(customerPhone, dbColumn("phone", customer.getUserId()),
                "conflicting update must leave the stored phone untouched");
    }

    @Test
    void ownExistingPhoneIsAccepted() {
        authenticateAs(customer);

        UserProfileResponse response = userAccountService.updateProfile(updateRequest(
                "Phase 11A Customer", customerPhone, null, null, null, null));

        assertEquals(customerPhone, response.getPhone());
        userRepository.flush();
        assertEquals(customerPhone, dbColumn("phone", customer.getUserId()));
    }

    // ------------------------------------------------------------------
    // PATCH /me/password
    // ------------------------------------------------------------------

    @Test
    void passwordChangePersistsBcryptHashOnly() {
        authenticateAs(customer);
        String newPassword = "Phase11ANew@2026";

        userAccountService.changePassword(passwordRequest(oldPassword, newPassword));

        String stored = dbColumn("password_hash", customer.getUserId());
        assertTrue(stored.startsWith("$2a$"), "stored value must be a BCrypt hash");
        assertTrue(passwordEncoder.matches(newPassword, stored), "new password must match stored hash");
        assertFalse(passwordEncoder.matches(oldPassword, stored), "old password must no longer match");
        // Everything else untouched:
        assertEquals(customer.getEmail(), dbColumn("email", customer.getUserId()));
        assertEquals("CUSTOMER", dbColumn("role", customer.getUserId()));
        assertEquals("ACTIVE", dbColumn("status", customer.getUserId()));
        assertEquals("Phase 11A Customer", dbColumn("full_name", customer.getUserId()));
        assertEquals(customerPhone, dbColumn("phone", customer.getUserId()));
    }

    @Test
    void newEnablesRealLoginWhileOldPasswordFails() {
        authenticateAs(customer);
        String newPassword = "Phase11ALogin@2026";

        userAccountService.changePassword(passwordRequest(oldPassword, newPassword));

        assertNotNull(authService.login(customer.getEmail(), newPassword));
        assertThrows(RuntimeException.class, () -> authService.login(customer.getEmail(), oldPassword));
    }

    @Test
    void wrongCurrentPasswordAndEqualNewPasswordAreRejectedWithoutPersistence() {
        authenticateAs(customer);
        String originalHash = dbColumn("password_hash", customer.getUserId());

        assertThrows(Exception.class, () -> userAccountService.changePassword(
                passwordRequest("TotallyWrong@1", "Phase11ANew@2026")));
        assertThrows(Exception.class, () -> userAccountService.changePassword(
                passwordRequest(oldPassword, oldPassword)));

        assertEquals(originalHash, dbColumn("password_hash", customer.getUserId()),
                "failed attempts must not modify the stored hash");
    }
}
