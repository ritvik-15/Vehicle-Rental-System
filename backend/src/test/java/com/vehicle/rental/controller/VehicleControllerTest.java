package com.vehicle.rental.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.AvailabilityResponse;
import com.vehicle.rental.dto.VehicleCreateRequest;
import com.vehicle.rental.dto.VehicleImageRequest;
import com.vehicle.rental.dto.VehicleImageResponse;
import com.vehicle.rental.dto.VehicleResponse;
import com.vehicle.rental.dto.VehicleStatusRequest;
import com.vehicle.rental.dto.VehicleUpdateRequest;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.service.RentalService;
import com.vehicle.rental.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VehicleService vehicleService;

    @MockBean
    private RentalService rentalService;

    private VehicleCreateRequest validCreateRequest() {
        VehicleCreateRequest request = new VehicleCreateRequest();
        request.setRegistrationNumber("MH12AB1234");
        request.setBrand("Toyota");
        request.setModel("Corolla");
        request.setVehicleType(VehicleType.CAR);
        request.setDailyRate(new BigDecimal("1500.00"));
        request.setSeatingCapacity(5);
        request.setFuelType(FuelType.PETROL);
        request.setTransmission(TransmissionType.AUTOMATIC);
        request.setLocation("Mumbai");
        return request;
    }

    private VehicleUpdateRequest validUpdateRequest() {
        VehicleCreateRequest create = validCreateRequest();
        VehicleUpdateRequest request = new VehicleUpdateRequest();
        request.setRegistrationNumber(create.getRegistrationNumber());
        request.setBrand(create.getBrand());
        request.setModel(create.getModel());
        request.setVehicleType(create.getVehicleType());
        request.setDailyRate(create.getDailyRate());
        request.setSeatingCapacity(create.getSeatingCapacity());
        request.setFuelType(create.getFuelType());
        request.setTransmission(create.getTransmission());
        request.setLocation(create.getLocation());
        return request;
    }

    private VehicleResponse sampleResponse() {
        VehicleResponse response = new VehicleResponse();
        response.setVehicleId(1L);
        response.setRegistrationNumber("MH12AB1234");
        response.setBrand("Toyota");
        response.setModel("Corolla");
        response.setVehicleType("CAR");
        response.setDailyRate(new BigDecimal("1500.00"));
        response.setSeatingCapacity(5);
        response.setFuelType("PETROL");
        response.setTransmission("AUTOMATIC");
        response.setLocation("Mumbai");
        response.setStatus("AVAILABLE");
        return response;
    }

    @Test
    void publicVehicleListingRequiresNoAuth() throws Exception {
        when(vehicleService.listVehicles(any(), any(), any(), any(), any(), any(),
                anyString(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk());
    }

    @Test
    void publicVehicleDetailsRequiresNoAuth() throws Exception {
        when(vehicleService.getVehicleDetails(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/vehicles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("MH12AB1234"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // ------------------------------------------------------------------
    // Phase E: public guest browsing (read-only catalog access)
    // ------------------------------------------------------------------

    private AvailabilityResponse availableResponse() {
        AvailabilityResponse response = new AvailabilityResponse();
        response.setVehicleId(1L);
        response.setRegistrationNumber("MH12AB1234");
        response.setVehicleStatus("AVAILABLE");
        response.setAvailable(true);
        response.setMessage("Vehicle is available for the selected dates");
        return response;
    }

    @Test
    void publicVehicleSearchFilterSortRequiresNoAuth() throws Exception {
        when(vehicleService.listVehicles(any(), any(), any(), any(), any(), any(),
                anyString(), anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/vehicles")
                        .param("search", "Toyota")
                        .param("vehicleType", "CAR")
                        .param("fuelType", "PETROL")
                        .param("transmission", "AUTOMATIC")
                        .param("sortBy", "dailyRate")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].brand").value("Toyota"));
    }

    // Phase 12F: public list now serializes populated image arrays (service mocked).
    @Test
    void publicVehicleListSerializesImageArrays() throws Exception {
        VehicleResponse withImages = sampleResponse();
        VehicleImageResponse primary = new VehicleImageResponse();
        primary.setImageId(10L);
        primary.setImageUrl("https://example.com/car.jpg");
        primary.setIsPrimary(true);
        withImages.setImages(List.of(primary));
        when(vehicleService.listVehicles(any(), any(), any(), any(), any(), any(),
                anyString(), anyString())).thenReturn(List.of(withImages));

        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].images[0].imageId").value(10))
                .andExpect(jsonPath("$[0].images[0].imageUrl").value("https://example.com/car.jpg"))
                .andExpect(jsonPath("$[0].images[0].isPrimary").value(true));
    }

    @Test
    void publicAvailabilityCheckRequiresNoAuthAndExposesNoPrivateData() throws Exception {
        when(rentalService.checkAvailability(eq(1L), any(), any()))
                .thenReturn(availableResponse());

        mockMvc.perform(get("/api/vehicles/1/availability")
                        .param("pickupDateTime", "2027-06-10T10:00")
                        .param("returnDateTime", "2027-06-12T10:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.vehicleStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void publicVehicleDetailsExposesNoCredentialFields() throws Exception {
        when(vehicleService.getVehicleDetails(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/vehicles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void unknownVehicleReturnsNotFound() throws Exception {
        when(vehicleService.getVehicleDetails(999L))
                .thenThrow(new ResourceNotFoundException("Vehicle not found"));

        mockMvc.perform(get("/api/vehicles/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Vehicle not found"));
    }

    @Test
    void unauthenticatedUserCannotCreateVehicle() throws Exception {
        mockMvc.perform(post("/api/admin/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotCreateVehicle() throws Exception {
        mockMvc.perform(post("/api/admin/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanCreateVehicle() throws Exception {
        when(vehicleService.createVehicle(any(VehicleCreateRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/admin/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleId").value(1))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        when(vehicleService.createVehicle(any(VehicleCreateRequest.class)))
                .thenThrow(new DuplicateResourceException("Registration number already exists"));

        mockMvc.perform(post("/api/admin/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Registration number already exists"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void invalidCreateRequestReturnsBadRequest() throws Exception {
        VehicleCreateRequest request = validCreateRequest();
        request.setDailyRate(new BigDecimal("-5.00"));

        mockMvc.perform(post("/api/admin/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotUpdateVehicle() throws Exception {
        mockMvc.perform(put("/api/admin/vehicles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanUpdateVehicle() throws Exception {
        when(vehicleService.updateVehicle(eq(1L), any(VehicleUpdateRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/admin/vehicles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(1));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void updateNonexistentVehicleReturnsNotFound() throws Exception {
        when(vehicleService.updateVehicle(eq(999L), any(VehicleUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Vehicle not found"));

        mockMvc.perform(put("/api/admin/vehicles/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdateRequest())))
                .andExpect(status().isNotFound());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanDeactivateVehicle() throws Exception {
        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("INACTIVE");
        VehicleResponse response = sampleResponse();
        response.setStatus("INACTIVE");
        when(vehicleService.updateVehicleStatus(eq(1L), any(VehicleStatusRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/admin/vehicles/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotChangeVehicleStatus() throws Exception {
        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("INACTIVE");

        mockMvc.perform(patch("/api/admin/vehicles/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void invalidStatusTransitionReturnsBadRequest() throws Exception {
        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("RENTED");
        when(vehicleService.updateVehicleStatus(eq(1L), any(VehicleStatusRequest.class)))
                .thenThrow(new InvalidRequestException(
                        "Only AVAILABLE or INACTIVE status can be set manually"));

        mockMvc.perform(patch("/api/admin/vehicles/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanAddVehicleImage() throws Exception {
        VehicleImageRequest request = new VehicleImageRequest();
        request.setImageUrl("https://example.com/car.jpg");
        request.setIsPrimary(true);
        VehicleImageResponse response = new VehicleImageResponse();
        response.setImageId(10L);
        response.setImageUrl("https://example.com/car.jpg");
        response.setIsPrimary(true);
        when(vehicleService.addVehicleImage(eq(1L), any(VehicleImageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/vehicles/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageId").value(10))
                .andExpect(jsonPath("$.isPrimary").value(true));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotAddVehicleImage() throws Exception {
        VehicleImageRequest request = new VehicleImageRequest();
        request.setImageUrl("https://example.com/car.jpg");

        mockMvc.perform(post("/api/admin/vehicles/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanDeleteVehicleImage() throws Exception {
        mockMvc.perform(delete("/api/admin/vehicles/1/images/10"))
                .andExpect(status().isNoContent());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotDeleteVehicleImage() throws Exception {
        mockMvc.perform(delete("/api/admin/vehicles/1/images/10"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Phase 11C: admin vehicle read APIs (list + detail)
    // ------------------------------------------------------------------

    private VehicleResponse inactiveDetailResponse() {
        VehicleResponse response = sampleResponse();
        response.setVehicleId(2L);
        response.setStatus("INACTIVE");
        VehicleImageResponse first = new VehicleImageResponse();
        first.setImageId(20L);
        first.setImageUrl("https://example.com/inactive-1.jpg");
        first.setIsPrimary(true);
        VehicleImageResponse second = new VehicleImageResponse();
        second.setImageId(21L);
        second.setImageUrl("https://example.com/inactive-2.jpg");
        second.setIsPrimary(false);
        response.setImages(List.of(first, second));
        return response;
    }

    @Test
    void adminVehicleListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotListAdminVehicles() throws Exception {
        mockMvc.perform(get("/api/admin/vehicles"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminVehicleListReturnsAllStatusesIncludingInactive() throws Exception {
        VehicleResponse rented = sampleResponse();
        rented.setVehicleId(3L);
        rented.setStatus("RENTED");
        VehicleResponse maintenance = sampleResponse();
        maintenance.setVehicleId(4L);
        maintenance.setStatus("MAINTENANCE");
        VehicleResponse inactive = sampleResponse();
        inactive.setVehicleId(2L);
        inactive.setStatus("INACTIVE");
        when(vehicleService.getAllVehiclesForAdmin())
                .thenReturn(List.of(sampleResponse(), rented, maintenance, inactive));

        mockMvc.perform(get("/api/admin/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].status").value("RENTED"))
                .andExpect(jsonPath("$[2].status").value("MAINTENANCE"))
                .andExpect(jsonPath("$[3].status").value("INACTIVE"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminVehicleListEmptyDatabaseReturnsEmptyArray() throws Exception {
        when(vehicleService.getAllVehiclesForAdmin()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/vehicles"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminVehicleListReturnsSafeVehicleResponseShape() throws Exception {
        when(vehicleService.getAllVehiclesForAdmin()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value(1))
                .andExpect(jsonPath("$[0].registrationNumber").value("MH12AB1234"))
                .andExpect(jsonPath("$[0].brand").value("Toyota"))
                .andExpect(jsonPath("$[0].dailyRate").value(1500.00))
                .andExpect(jsonPath("$[0].images").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    @Test
    void adminVehicleDetailRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/vehicles/1"))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerCannotViewAdminVehicleDetail() throws Exception {
        mockMvc.perform(get("/api/admin/vehicles/1"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminVehicleDetailAvailableReturnsOk() throws Exception {
        when(vehicleService.getVehicleDetailsForAdmin(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/admin/vehicles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(1))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminVehicleDetailInactiveReturnsOkWithImages() throws Exception {
        when(vehicleService.getVehicleDetailsForAdmin(2L)).thenReturn(inactiveDetailResponse());

        mockMvc.perform(get("/api/admin/vehicles/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].imageUrl").value("https://example.com/inactive-1.jpg"))
                .andExpect(jsonPath("$.images[0].isPrimary").value(true));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminVehicleDetailMissingReturnsNotFound() throws Exception {
        when(vehicleService.getVehicleDetailsForAdmin(999L))
                .thenThrow(new ResourceNotFoundException("Vehicle not found"));

        mockMvc.perform(get("/api/admin/vehicles/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Vehicle not found"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminVehicleDetailReturnsSafeResponseWithoutCredentials() throws Exception {
        when(vehicleService.getVehicleDetailsForAdmin(2L)).thenReturn(inactiveDetailResponse());

        mockMvc.perform(get("/api/admin/vehicles/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void healthEndpointStillAvailable() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
