package com.vehicle.rental.controller;

import com.vehicle.rental.dto.PasswordChangeRequest;
import com.vehicle.rental.dto.ProfileUpdateRequest;
import com.vehicle.rental.dto.UserProfileResponse;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.service.UserAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 11A: the three self-service endpoints are 401 without a token, 403
 * for ADMIN principals (service-level ForbiddenException mapping), 200 for
 * CUSTOMER with a safe profile payload (no password/credential keys) and 400
 * for bean-validation violations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAccountService userAccountService;

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private UserProfileResponse sampleProfile() {
        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(7L);
        response.setFullName("Asha Patil");
        response.setEmail("asha@example.com");
        response.setPhone("9812345678");
        response.setAddress("12 MG Road");
        response.setCity("Pune");
        response.setState("MH");
        response.setPincode("411001");
        response.setRole("CUSTOMER");
        response.setStatus("ACTIVE");
        response.setCreatedAt(LocalDateTime.now().minusDays(2));
        return response;
    }

    // ------------------------------------------------------------------
    // 401 — unauthenticated
    // ------------------------------------------------------------------

    @Test
    void getProfileUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Asha\",\"phone\":\"9812345678\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"new12345\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 403 — ADMIN principal (service rejects non-CUSTOMER with ForbiddenException)
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void getProfileAsAdminReturnsForbidden() throws Exception {
        when(userAccountService.getProfile())
                .thenThrow(new ForbiddenException("Self-service account management is available to customers only"));

        mockMvc.perform(get("/api/users/me")).andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void updateProfileAsAdminReturnsForbidden() throws Exception {
        when(userAccountService.updateProfile(org.mockito.ArgumentMatchers.any(ProfileUpdateRequest.class)))
                .thenThrow(new ForbiddenException("Self-service account management is available to customers only"));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Admin\",\"phone\":\"9800000000\"}"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void changePasswordAsAdminReturnsForbidden() throws Exception {
        when(userAccountService.changePassword(org.mockito.ArgumentMatchers.any(PasswordChangeRequest.class)))
                .thenThrow(new ForbiddenException("Self-service account management is available to customers only"));

        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"new12345\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // 200 — CUSTOMER with safe payloads
    // ------------------------------------------------------------------

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void getProfileAsCustomerReturnsOwnProfileWithoutAnyCredential() throws Exception {
        when(userAccountService.getProfile()).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.fullName").value("Asha Patil"))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.phone").value("9812345678"))
                .andExpect(jsonPath("$.address").value("12 MG Road"))
                .andExpect(jsonPath("$.city").value("Pune"))
                .andExpect(jsonPath("$.state").value("MH"))
                .andExpect(jsonPath("$.pincode").value("411001"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void updateProfileAsCustomerReturnsUpdatedProfile() throws Exception {
        UserProfileResponse updated = sampleProfile();
        updated.setFullName("Asha Patil Updated");
        updated.setCity("Mumbai");
        when(userAccountService.updateProfile(org.mockito.ArgumentMatchers.any(ProfileUpdateRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Asha Patil Updated\",\"phone\":\"9812345678\","
                                + "\"address\":\"12 MG Road\",\"city\":\"Mumbai\",\"state\":\"MH\","
                                + "\"pincode\":\"411001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha Patil Updated"))
                .andExpect(jsonPath("$.city").value("Mumbai"))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void changePasswordAsCustomerReturnsMessageWithoutPasswordFields() throws Exception {
        when(userAccountService.changePassword(org.mockito.ArgumentMatchers.any(PasswordChangeRequest.class)))
                .thenReturn(Map.of("message", "Password updated successfully"));

        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldDemo@2026\",\"newPassword\":\"NewDemo@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.newPassword").doesNotExist())
                .andExpect(jsonPath("$.currentPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ------------------------------------------------------------------
    // 400 — bean validation
    // ------------------------------------------------------------------

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void updateProfileMissingFullNameReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9812345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void updateProfileMissingPhoneReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Asha Patil\"}"))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void changePasswordMissingFieldsReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void changePasswordShortNewPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldDemo@2026\",\"newPassword\":\"12345\"}"))
                .andExpect(status().isBadRequest());
    }
}
