package com.vehicle.rental.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.CustomerResponse;
import com.vehicle.rental.dto.CustomerStatusRequest;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9: admin customer endpoint authorization (401/403), validation (400),
 * not-found semantics for non-customer accounts (404), and response hygiene —
 * password_hash must never appear in any payload.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminCustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    private CustomerResponse sampleResponse() {
        CustomerResponse response = new CustomerResponse();
        response.setUserId(7L);
        response.setFullName("Asha Patil");
        response.setEmail("asha@example.com");
        response.setPhone("9812345670");
        response.setAddress("12 MG Road");
        response.setCity("Pune");
        response.setState("MH");
        response.setPincode("411001");
        response.setRole("CUSTOMER");
        response.setStatus("ACTIVE");
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());
        return response;
    }

    // ------------------------------------------------------------------
    // 401: unauthenticated
    // ------------------------------------------------------------------

    @Test
    void unauthenticatedListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedDetailReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/customers/7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedStatusUpdateReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/admin/customers/7/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 403: authenticated but not ADMIN
    // ------------------------------------------------------------------

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerRoleListReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerRoleDetailReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/customers/7"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerRoleStatusUpdateReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/admin/customers/7/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // 200: admin success
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminListReturnsOkWithCustomerFields() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(7))
                .andExpect(jsonPath("$[0].fullName").value("Asha Patil"))
                .andExpect(jsonPath("$[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminListNeverExposesPasswordHash() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password_hash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminDetailReturnsOk() throws Exception {
        when(customerService.getCustomerDetails(7L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/admin/customers/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminStatusUpdateReturnsOkWithNewStatus() throws Exception {
        CustomerResponse updated = sampleResponse();
        updated.setStatus("INACTIVE");
        when(customerService.updateCustomerStatus(eq(7L), any(CustomerStatusRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/admin/customers/7/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ------------------------------------------------------------------
    // 404: unknown or non-CUSTOMER account
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminDetailUnknownIdReturnsNotFound() throws Exception {
        when(customerService.getCustomerDetails(999L))
                .thenThrow(new ResourceNotFoundException("Customer not found"));

        mockMvc.perform(get("/api/admin/customers/999"))
                .andExpect(status().isNotFound());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminStatusUpdateOnAdminAccountReturnsNotFound() throws Exception {
        // Admin accounts are not CUSTOMER accounts: the service reports 404 and
        // never performs the update.
        when(customerService.updateCustomerStatus(eq(5L), any(CustomerStatusRequest.class)))
                .thenThrow(new ResourceNotFoundException("Customer not found"));

        mockMvc.perform(patch("/api/admin/customers/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isNotFound());

        verify(customerService).updateCustomerStatus(eq(5L), any(CustomerStatusRequest.class));
    }

    // ------------------------------------------------------------------
    // 400: validation
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminStatusUpdateBlankStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/admin/customers/7/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", ""))))
                .andExpect(status().isBadRequest());

        verify(customerService, never()).updateCustomerStatus(eq(7L), any(CustomerStatusRequest.class));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminStatusUpdateInvalidStatusValueReturnsBadRequest() throws Exception {
        when(customerService.updateCustomerStatus(eq(7L), any(CustomerStatusRequest.class)))
                .thenThrow(new InvalidRequestException("Invalid customer status"));

        mockMvc.perform(patch("/api/admin/customers/7/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED"))))
                .andExpect(status().isBadRequest());
    }
}
