package com.vehicle.rental.service;

import com.vehicle.rental.dto.AuthenticationResponse;
import com.vehicle.rental.dto.RegisterRequest;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.repository.UserRepository;
import com.vehicle.rental.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void testPasswordEncoderHashesPassword() {
        PasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        String rawPassword = "password123";
        String hashed = encoder.encode(rawPassword);

        assertNotEquals(rawPassword, hashed);
        assertTrue(encoder.matches(rawPassword, hashed));
    }

    @Test
    void testSuccessfulRegistration() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("9876543210");
        request.setPassword("password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");

        User savedUser = new User();
        savedUser.setUserId(1L);
        savedUser.setEmail("john@example.com");
        savedUser.setRole(UserRole.CUSTOMER);
        savedUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = authService.register(request);

        assertNotNull(result);
        assertEquals("john@example.com", result.getEmail());
        assertEquals(UserRole.CUSTOMER, result.getRole());
        assertEquals(UserStatus.ACTIVE, result.getStatus());
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void testDuplicateEmailRejected() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPhone("9876543210");
        request.setPassword("password123");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> authService.register(request));
        assertEquals("Email already exists", exception.getMessage());
    }

    @Test
    void testDuplicatePhoneRejected() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPhone("existingPhone");
        request.setPassword("password123");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("existingPhone")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> authService.register(request));
        assertEquals("Phone already exists", exception.getMessage());
    }

    @Test
    void testRegistrationCannotChooseAdminRole() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("9876543210");
        request.setPassword("password123");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        User savedUser = new User();
        savedUser.setRole(UserRole.CUSTOMER);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = authService.register(request);
        assertEquals(UserRole.CUSTOMER, result.getRole());
    }

    @Test
    void testSuccessfulLogin() {
        User user = new User();
        user.setUserId(1L);
        user.setEmail("john@example.com");
        user.setPasswordHash("hashedPassword");
        user.setFullName("John Doe");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(1L, "john@example.com", "CUSTOMER")).thenReturn("jwt-token");

        AuthenticationResponse response = authService.login("john@example.com", "password123");

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getType());
        assertEquals(1L, response.getUserId());
        assertEquals("CUSTOMER", response.getRole());
    }

    @Test
    void testWrongPasswordRejected() {
        User user = new User();
        user.setEmail("john@example.com");
        user.setPasswordHash("hashedPassword");
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login("john@example.com", "wrongPassword"));
    }

    @Test
    void testNonexistentEmailRejected() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.login("nonexistent@example.com", "password123"));
    }

    @Test
    void testInactiveUserCannotLogin() {
        User user = new User();
        user.setEmail("inactive@example.com");
        user.setPasswordHash("hashedPassword");
        user.setStatus(UserStatus.INACTIVE);

        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () -> authService.login("inactive@example.com", "password123"));
    }
}
