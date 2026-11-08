package com.vehicle.rental.service;

import com.vehicle.rental.dto.AuthenticationResponse;
import com.vehicle.rental.dto.RegisterRequest;
import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.repository.UserRepository;
import com.vehicle.rental.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            // Maps to 409 Conflict via VehicleExceptionHandler. A plain
            // RuntimeException here fell through to the generic handler
            // and was reported as a misleading 401.
            throw new DuplicateResourceException("Email already exists");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Phone already exists");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAddress(request.getAddress());
        user.setCity(request.getCity());
        user.setState(request.getState());
        user.setPincode(request.getPincode());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    public AuthenticationResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new RuntimeException("Account is inactive");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getUserId(), user.getEmail(), user.getRole().name());

        return new AuthenticationResponse(
                token,
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
