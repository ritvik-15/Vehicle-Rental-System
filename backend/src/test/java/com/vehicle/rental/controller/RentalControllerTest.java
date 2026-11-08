package com.vehicle.rental.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.AvailabilityResponse;
import com.vehicle.rental.dto.RentalCreateRequest;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.exception.ForbiddenException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RentalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RentalService rentalService;

    private RentalCreateRequest validRequest() {
        RentalCreateRequest request = new RentalCreateRequest();
        request.setVehicleId(1L);
        request.setPickupDateTime(LocalDateTime.now().plusDays(5));
        request.setReturnDateTime(LocalDateTime.now().plusDays(7));
        request.setNotes("Test rental");
        return request;
    }

    private RentalResponse sampleResponse() {
        RentalResponse response = new RentalResponse();
        response.setRentalId(10L);
        response.setUserId(1L);
        response.setVehicleId(1L);
        response.setVehicleRegistrationNumber("MH05AB1234");
        response.setVehicleBrand("Hyundai");
        response.setVehicleModel("i20");
        response.setPickupDateTime(LocalDateTime.now().plusDays(5));
        response.setReturnDateTime(LocalDateTime.now().plusDays(7));
        response.setNumberOfDays(2);
        response.setDailyRate(new BigDecimal("1200.00"));
        response.setSubtotal(new BigDecimal("2400.00"));
        response.setDiscountAmount(new BigDecimal("0.00"));
        response.setTaxAmount(new BigDecimal("432.00"));
        response.setSecurityDeposit(new BigDecimal("480.00"));
        response.setTotalAmount(new BigDecimal("3312.00"));
        response.setRentalStatus("PENDING");
        response.setNotes("Test rental");
        return response;
    }

    @Test
    void unauthenticatedCreateRentalReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/rentals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedGetMyRentalsReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/rentals/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedGetRentalDetailsReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/rentals/10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedCancelRentalReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/rentals/10/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCanCreateRental() throws Exception {
        when(rentalService.createRental(any(RentalCreateRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/rentals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rentalId").value(10))
                .andExpect(jsonPath("$.rentalStatus").value("PENDING"))
                .andExpect(jsonPath("$.numberOfDays").value(2))
                .andExpect(jsonPath("$.dailyRate").value(1200.00))
                .andExpect(jsonPath("$.subtotal").value(2400.00))
                .andExpect(jsonPath("$.discountAmount").value(0.00))
                .andExpect(jsonPath("$.taxAmount").value(432.00))
                .andExpect(jsonPath("$.securityDeposit").value(480.00))
                .andExpect(jsonPath("$.totalAmount").value(3312.00));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCanGetMyRentals() throws Exception {
        when(rentalService.getMyRentals()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/rentals/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rentalId").value(10))
                .andExpect(jsonPath("$[0].rentalStatus").value("PENDING"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCanViewOwnRental() throws Exception {
        when(rentalService.getRentalDetails(10L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/rentals/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalId").value(10));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotViewAnotherUsersRental() throws Exception {
        when(rentalService.getRentalDetails(10L))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(get("/api/rentals/10"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCanCancelOwnRental() throws Exception {
        RentalResponse response = sampleResponse();
        response.setRentalStatus("CANCELLED");
        when(rentalService.cancelRental(10L)).thenReturn(response);

        mockMvc.perform(patch("/api/rentals/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalStatus").value("CANCELLED"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotCancelAnotherUsersRental() throws Exception {
        when(rentalService.cancelRental(10L))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(patch("/api/rentals/10/cancel"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void invalidRentalDatesReturnBadRequest() throws Exception {
        when(rentalService.createRental(any(RentalCreateRequest.class)))
                .thenThrow(new InvalidRequestException(
                        "Pickup date and time must be in the future"));

        mockMvc.perform(post("/api/rentals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void overlappingBookingReturnsConflict() throws Exception {
        when(rentalService.createRental(any(RentalCreateRequest.class)))
                .thenThrow(new com.vehicle.rental.exception.DuplicateResourceException(
                        "Vehicle is already booked for the requested period"));

        mockMvc.perform(post("/api/rentals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanViewAnyRental() throws Exception {
        when(rentalService.getRentalDetails(10L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/rentals/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalId").value(10));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCannotCreateRental() throws Exception {
        when(rentalService.createRental(any(RentalCreateRequest.class)))
                .thenThrow(new ForbiddenException("Only customers can create rentals"));

        mockMvc.perform(post("/api/rentals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void availabilityEndpointIsPublic() throws Exception {
        AvailabilityResponse response = new AvailabilityResponse();
        response.setVehicleId(1L);
        response.setRegistrationNumber("MH05AB1234");
        response.setVehicleStatus("AVAILABLE");
        response.setAvailable(true);
        response.setMessage("Vehicle is available for the requested period");
        when(rentalService.checkAvailability(eq(1L), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/vehicles/1/availability")
                        .param("pickupDateTime", "2099-01-01T10:00:00")
                        .param("returnDateTime", "2099-01-02T10:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.vehicleStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.registrationNumber").value("MH05AB1234"));
    }

    @Test
    void availabilityEndpointReturnsNotFoundForUnknownVehicle() throws Exception {
        when(rentalService.checkAvailability(eq(99L), any(), any()))
                .thenThrow(new ResourceNotFoundException("Vehicle not found"));

        mockMvc.perform(get("/api/vehicles/99/availability")
                        .param("pickupDateTime", "2099-01-01T10:00:00")
                        .param("returnDateTime", "2099-01-02T10:00:00"))
                .andExpect(status().isNotFound());
    }

    @Test
    void availabilityEndpointReturnsBadRequestForInvalidDates() throws Exception {
        when(rentalService.checkAvailability(eq(1L), any(), any()))
                .thenThrow(new InvalidRequestException(
                        "Pickup date and time must be in the future"));

        mockMvc.perform(get("/api/vehicles/1/availability")
                        .param("pickupDateTime", "2020-01-01T10:00:00")
                        .param("returnDateTime", "2020-01-02T10:00:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthEndpointStillAvailable() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
