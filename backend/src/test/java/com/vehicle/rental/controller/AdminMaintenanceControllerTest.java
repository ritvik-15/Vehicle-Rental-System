package com.vehicle.rental.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.MaintenanceCreateRequest;
import com.vehicle.rental.dto.MaintenanceResponse;
import com.vehicle.rental.dto.MaintenanceStatusRequest;
import com.vehicle.rental.dto.MaintenanceUpdateRequest;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.service.MaintenanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaintenanceService maintenanceService;

    private MaintenanceCreateRequest validCreate() {
        MaintenanceCreateRequest request = new MaintenanceCreateRequest();
        request.setVehicleId(1L);
        request.setMaintenanceType("Routine Service");
        request.setMaintenanceDate(LocalDate.now().plusDays(5));
        request.setCost(new BigDecimal("500.00"));
        return request;
    }

    private MaintenanceResponse sampleResponse() {
        MaintenanceResponse response = new MaintenanceResponse();
        response.setMaintenanceId(10L);
        response.setVehicleId(1L);
        response.setVehicleRegistrationNumber("MH05AB1234");
        response.setVehicleBrand("Hyundai");
        response.setVehicleModel("i20");
        response.setMaintenanceType("Routine Service");
        response.setMaintenanceDate(LocalDate.now().plusDays(5));
        response.setCost(new BigDecimal("500.00"));
        response.setStatus("SCHEDULED");
        return response;
    }

    @Test
    void unauthenticatedListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/maintenance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedCreateReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreate())))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerListReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/maintenance"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCreateReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreate())))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerStatusUpdateReturnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/admin/maintenance/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminListReturnsOk() throws Exception {
        when(maintenanceService.getAllMaintenance()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/maintenance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maintenanceId").value(10))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].vehicleRegistrationNumber").value("MH05AB1234"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCreateReturnsCreated() throws Exception {
        when(maintenanceService.createMaintenance(any(MaintenanceCreateRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/admin/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreate())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maintenanceId").value(10))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminGetByIdReturnsOk() throws Exception {
        when(maintenanceService.getMaintenanceDetails(10L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/admin/maintenance/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenanceId").value(10))
                .andExpect(jsonPath("$.cost").value(500.00));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminUpdateReturnsOk() throws Exception {
        MaintenanceUpdateRequest request = new MaintenanceUpdateRequest();
        request.setVehicleId(1L);
        request.setMaintenanceType("Major Service");
        request.setMaintenanceDate(LocalDate.now().plusDays(5));
        request.setCost(new BigDecimal("1500.00"));
        when(maintenanceService.updateMaintenance(eq(10L), any(MaintenanceUpdateRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/admin/maintenance/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenanceId").value(10));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminStatusUpdateReturnsOk() throws Exception {
        MaintenanceResponse response = sampleResponse();
        response.setStatus("IN_PROGRESS");
        when(maintenanceService.updateMaintenanceStatus(eq(10L), any(MaintenanceStatusRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/admin/maintenance/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminGetUnknownIdReturnsNotFound() throws Exception {
        when(maintenanceService.getMaintenanceDetails(999L))
                .thenThrow(new ResourceNotFoundException("Maintenance record not found"));

        mockMvc.perform(get("/api/admin/maintenance/999"))
                .andExpect(status().isNotFound());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCreateWithInvalidDataReturnsBadRequest() throws Exception {
        // missing required fields: maintenanceType, maintenanceDate, cost
        mockMvc.perform(post("/api/admin/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vehicleId", 1))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Phase 11C: maintenanceType validation (already present in
    // MaintenanceCreateRequest) — proven by focused tests, file unchanged.
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void createWithoutMaintenanceTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":1,\"maintenanceDate\":\"2026-12-01\",\"cost\":500.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void createWithBlankMaintenanceTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":1,\"maintenanceType\":\"   \","
                                + "\"maintenanceDate\":\"2026-12-01\",\"cost\":500.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void createWithOverlongMaintenanceTypeReturnsBadRequest() throws Exception {
        String overlong = "M".repeat(101);
        mockMvc.perform(post("/api/admin/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":1,\"maintenanceType\":\"" + overlong + "\","
                                + "\"maintenanceDate\":\"2026-12-01\",\"cost\":500.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }
}
