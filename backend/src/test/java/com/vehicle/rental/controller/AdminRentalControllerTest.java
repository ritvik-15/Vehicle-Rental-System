package com.vehicle.rental.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.dto.RentalStatusRequest;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.service.RentalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
 * Phase 9: admin rental endpoint authorization (401/403), wiring for the
 * status matrix and /return (400s delegated to the service), 404s, and the
 * actual_return_datetime field on completion.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminRentalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RentalService rentalService;

    private RentalResponse sampleResponse() {
        RentalResponse response = new RentalResponse();
        response.setRentalId(10L);
        response.setUserId(7L);
        response.setVehicleId(1L);
        response.setVehicleRegistrationNumber("MH05AB1234");
        response.setVehicleBrand("Hyundai");
        response.setVehicleModel("i20");
        response.setPickupDateTime(LocalDateTime.now().plusDays(2));
        response.setReturnDateTime(LocalDateTime.now().plusDays(4));
        response.setNumberOfDays(2);
        response.setDailyRate(new BigDecimal("1200.00"));
        response.setSubtotal(new BigDecimal("2400.00"));
        response.setDiscountAmount(new BigDecimal("0.00"));
        response.setTaxAmount(new BigDecimal("432.00"));
        response.setSecurityDeposit(new BigDecimal("480.00"));
        response.setTotalAmount(new BigDecimal("3312.00"));
        response.setRentalStatus("PENDING");
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());
        return response;
    }

    // ------------------------------------------------------------------
    // 401: unauthenticated
    // ------------------------------------------------------------------

    @Test
    void unauthenticatedListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/rentals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedDetailReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/rentals/10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedStatusUpdateReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/admin/rentals/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedReturnReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/admin/rentals/10/return"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 403: authenticated but not ADMIN
    // ------------------------------------------------------------------

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerRoleListReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/rentals"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerRoleStatusUpdateReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/admin/rentals/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerRoleReturnReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/admin/rentals/10/return"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // 200: admin success
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminListReturnsOk() throws Exception {
        when(rentalService.getAllRentals()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/rentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rentalId").value(10))
                .andExpect(jsonPath("$[0].rentalStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].totalAmount").value(3312.00));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminDetailReturnsOk() throws Exception {
        when(rentalService.getRentalDetails(10L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/admin/rentals/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalId").value(10))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminStatusUpdateReturnsOk() throws Exception {
        RentalResponse active = sampleResponse();
        active.setRentalStatus("ACTIVE");
        when(rentalService.updateRentalStatusForAdmin(eq(10L), any(RentalStatusRequest.class)))
                .thenReturn(active);

        mockMvc.perform(patch("/api/admin/rentals/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalStatus").value("ACTIVE"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminReturnReturnsOkWithActualReturnDatetime() throws Exception {
        RentalResponse completed = sampleResponse();
        completed.setRentalStatus("COMPLETED");
        completed.setActualReturnDatetime(LocalDateTime.now());
        when(rentalService.returnRentalForAdmin(10L)).thenReturn(completed);

        mockMvc.perform(patch("/api/admin/rentals/10/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.actualReturnDatetime").exists());
    }

    // ------------------------------------------------------------------
    // 404
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminDetailUnknownIdReturnsNotFound() throws Exception {
        when(rentalService.getRentalDetails(999L))
                .thenThrow(new ResourceNotFoundException("Rental not found"));

        mockMvc.perform(get("/api/admin/rentals/999"))
                .andExpect(status().isNotFound());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminReturnUnknownIdReturnsNotFound() throws Exception {
        when(rentalService.returnRentalForAdmin(999L))
                .thenThrow(new ResourceNotFoundException("Rental not found"));

        mockMvc.perform(patch("/api/admin/rentals/999/return"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // 400: matrix violations and validation
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminStatusBlankReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/admin/rentals/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", ""))))
                .andExpect(status().isBadRequest());

        verify(rentalService, never())
                .updateRentalStatusForAdmin(eq(10L), any(RentalStatusRequest.class));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminStatusInvalidEnumValueReturnsBadRequest() throws Exception {
        when(rentalService.updateRentalStatusForAdmin(eq(10L), any(RentalStatusRequest.class)))
                .thenThrow(new InvalidRequestException("Invalid rental status"));

        mockMvc.perform(patch("/api/admin/rentals/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "BOGUS"))))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminPendingToConfirmedReturnsBadRequest() throws Exception {
        when(rentalService.updateRentalStatusForAdmin(eq(10L), any(RentalStatusRequest.class)))
                .thenThrow(new InvalidRequestException(
                        "Rentals can only be confirmed through successful payment"));

        mockMvc.perform(patch("/api/admin/rentals/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CONFIRMED"))))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminInvalidTransitionReturnsBadRequest() throws Exception {
        when(rentalService.updateRentalStatusForAdmin(eq(10L), any(RentalStatusRequest.class)))
                .thenThrow(new InvalidRequestException("Invalid rental status transition"));

        mockMvc.perform(patch("/api/admin/rentals/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminReturnNonActiveRentalReturnsBadRequest() throws Exception {
        when(rentalService.returnRentalForAdmin(10L))
                .thenThrow(new InvalidRequestException("Only active rentals can be returned"));

        mockMvc.perform(patch("/api/admin/rentals/10/return"))
                .andExpect(status().isBadRequest());
    }
}
