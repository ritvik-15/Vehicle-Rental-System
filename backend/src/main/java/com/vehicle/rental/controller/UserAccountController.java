package com.vehicle.rental.controller;

import com.vehicle.rental.dto.PasswordChangeRequest;
import com.vehicle.rental.dto.ProfileUpdateRequest;
import com.vehicle.rental.dto.UserProfileResponse;
import com.vehicle.rental.service.UserAccountService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 11A customer self-service account endpoints. No path/query/body
 * identity input exists — the account is always the authenticated JWT
 * principal's own. Covered by the existing anyRequest().authenticated()
 * rule (401 unauthenticated); the CUSTOMER-only restriction (ADMIN -> 403)
 * is enforced in the service.
 */
@RestController
@RequestMapping("/api/users")
public class UserAccountController {

    private final UserAccountService userAccountService;

    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public UserProfileResponse getProfile() {
        return userAccountService.getProfile();
    }

    @PutMapping("/me")
    public UserProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return userAccountService.updateProfile(request);
    }

    @PatchMapping("/me/password")
    public Map<String, String> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        return userAccountService.changePassword(request);
    }
}
