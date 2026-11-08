package com.vehicle.rental.config;

import com.vehicle.rental.entity.User;
import com.vehicle.rental.enums.UserRole;
import com.vehicle.rental.enums.UserStatus;
import com.vehicle.rental.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {

    @Value("${admin.bootstrap.enabled}")
    private boolean enabled;

    @Value("${admin.bootstrap.email}")
    private String email;

    @Value("${admin.bootstrap.password}")
    private String password;

    @Value("${admin.bootstrap.phone}")
    private String phone;

    @Value("${admin.bootstrap.name}")
    private String name;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return;
        }

        if (userRepository.existsByEmail(email)) {
            return;
        }

        User admin = new User();
        admin.setFullName(name != null ? name : "Administrator");
        admin.setEmail(email);
        admin.setPhone(phone != null ? phone : "0000000000");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);

        userRepository.save(admin);
    }
}
