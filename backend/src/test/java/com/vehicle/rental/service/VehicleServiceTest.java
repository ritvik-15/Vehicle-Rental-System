package com.vehicle.rental.service;

import com.vehicle.rental.dto.VehicleCreateRequest;
import com.vehicle.rental.dto.VehicleImageRequest;
import com.vehicle.rental.dto.VehicleImageResponse;
import com.vehicle.rental.dto.VehicleResponse;
import com.vehicle.rental.dto.VehicleStatusRequest;
import com.vehicle.rental.dto.VehicleUpdateRequest;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.entity.VehicleImage;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.VehicleImageRepository;
import com.vehicle.rental.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleImageRepository vehicleImageRepository;

    @InjectMocks
    private VehicleService vehicleService;

    private VehicleCreateRequest createRequest() {
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

    private VehicleUpdateRequest updateRequest() {
        VehicleUpdateRequest request = new VehicleUpdateRequest();
        request.setRegistrationNumber("MH12AB1234");
        request.setBrand("Toyota");
        request.setModel("Corolla");
        request.setVehicleType(VehicleType.CAR);
        request.setDailyRate(new BigDecimal("1600.00"));
        request.setSeatingCapacity(5);
        request.setFuelType(FuelType.PETROL);
        request.setTransmission(TransmissionType.AUTOMATIC);
        request.setLocation("Mumbai");
        return request;
    }

    private Vehicle buildVehicle(Long id, VehicleStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(id);
        vehicle.setRegistrationNumber("MH12AB1234");
        vehicle.setBrand("Toyota");
        vehicle.setModel("Corolla");
        vehicle.setVehicleType(VehicleType.CAR);
        vehicle.setDailyRate(new BigDecimal("1500.00"));
        vehicle.setSeatingCapacity(5);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setTransmission(TransmissionType.AUTOMATIC);
        vehicle.setLocation("Mumbai");
        vehicle.setStatus(status);
        return vehicle;
    }

    @Test
    void createVehicleForcesAvailableStatus() {
        when(vehicleRepository.existsByRegistrationNumber("MH12AB1234")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle vehicle = invocation.getArgument(0);
            vehicle.setVehicleId(1L);
            return vehicle;
        });

        VehicleResponse response = vehicleService.createVehicle(createRequest());

        assertNotNull(response);
        assertEquals("AVAILABLE", response.getStatus());
        verify(vehicleRepository).save(argThat(vehicle -> vehicle.getStatus() == VehicleStatus.AVAILABLE));
    }

    @Test
    void duplicateRegistrationRejected() {
        when(vehicleRepository.existsByRegistrationNumber("MH12AB1234")).thenReturn(true);
        assertThrows(DuplicateResourceException.class,
                () -> vehicleService.createVehicle(createRequest()));
    }

    @Test
    void updateVehicleSuccess() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));
        when(vehicleRepository.existsByRegistrationNumberAndVehicleIdNot("MH12AB1234", 1L))
                .thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenReturn(buildVehicle(1L, VehicleStatus.AVAILABLE));

        VehicleResponse response = vehicleService.updateVehicle(1L, updateRequest());

        assertNotNull(response);
        assertEquals("MH12AB1234", response.getRegistrationNumber());
    }

    @Test
    void updateNonexistentVehicleRejected() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.updateVehicle(99L, updateRequest()));
    }

    @Test
    void duplicateRegistrationOnUpdateRejected() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));
        when(vehicleRepository.existsByRegistrationNumberAndVehicleIdNot("MH99XY9999", 1L))
                .thenReturn(true);

        VehicleUpdateRequest request = updateRequest();
        request.setRegistrationNumber("MH99XY9999");

        assertThrows(DuplicateResourceException.class,
                () -> vehicleService.updateVehicle(1L, request));
    }

    @Test
    void deactivateVehicleSuccess() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenReturn(buildVehicle(1L, VehicleStatus.INACTIVE));

        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("INACTIVE");

        VehicleResponse response = vehicleService.updateVehicleStatus(1L, request);

        assertEquals("INACTIVE", response.getStatus());
    }

    @Test
    void activateVehicleSuccess() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.INACTIVE)));
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenReturn(buildVehicle(1L, VehicleStatus.AVAILABLE));

        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("AVAILABLE");

        VehicleResponse response = vehicleService.updateVehicleStatus(1L, request);

        assertEquals("AVAILABLE", response.getStatus());
    }

    @Test
    void manualRentedStatusRejected() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));

        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("RENTED");

        assertThrows(InvalidRequestException.class,
                () -> vehicleService.updateVehicleStatus(1L, request));
    }

    @Test
    void manualMaintenanceStatusRejected() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));

        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("MAINTENANCE");

        assertThrows(InvalidRequestException.class,
                () -> vehicleService.updateVehicleStatus(1L, request));
    }

    @Test
    void invalidStatusStringRejected() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));

        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("BOGUS");

        assertThrows(InvalidRequestException.class,
                () -> vehicleService.updateVehicleStatus(1L, request));
    }

    @Test
    void statusUpdateForNonexistentVehicleRejected() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        VehicleStatusRequest request = new VehicleStatusRequest();
        request.setStatus("INACTIVE");

        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.updateVehicleStatus(99L, request));
    }

    @Test
    void publicListingExcludesInactiveStatus() {
        when(vehicleRepository.searchVehicles(eq(VehicleStatus.INACTIVE),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(List.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));

        List<VehicleResponse> result = vehicleService.listVehicles(
                null, null, null, null, null, null, "createdAt", "desc");

        assertEquals(1, result.size());
        verify(vehicleRepository).searchVehicles(eq(VehicleStatus.INACTIVE),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void inactiveStatusFilterIsIgnoredForPublicListing() {
        when(vehicleRepository.searchVehicles(eq(VehicleStatus.INACTIVE),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(List.of());

        vehicleService.listVehicles(null, null, null, null, null,
                VehicleStatus.INACTIVE, "createdAt", "desc");

        verify(vehicleRepository).searchVehicles(eq(VehicleStatus.INACTIVE),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    // Phase 12F: public listing must include images (same mapping as detail).
    @Test
    void publicListingIncludesImagesPreservingOrderAndPrimaryFlags() {
        when(vehicleRepository.searchVehicles(eq(VehicleStatus.INACTIVE),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(List.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));

        VehicleImage first = new VehicleImage();
        first.setImageId(10L);
        first.setImageUrl("https://example.com/car-1.jpg");
        first.setIsPrimary(false);
        VehicleImage second = new VehicleImage();
        second.setImageId(11L);
        second.setImageUrl("https://example.com/car-2.jpg");
        second.setIsPrimary(true);
        when(vehicleImageRepository.findByVehicleVehicleIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(first, second));

        List<VehicleResponse> result = vehicleService.listVehicles(
                null, null, null, null, null, null, "createdAt", "desc");

        assertEquals(1, result.size());
        List<VehicleImageResponse> images = result.get(0).getImages();
        assertNotNull(images);
        assertEquals(2, images.size());
        assertEquals("https://example.com/car-1.jpg", images.get(0).getImageUrl());
        assertFalse(images.get(0).getIsPrimary());
        assertEquals("https://example.com/car-2.jpg", images.get(1).getImageUrl());
        assertTrue(images.get(1).getIsPrimary());
    }

    @Test
    void inactiveVehicleDetailsHidden() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.INACTIVE)));
        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.getVehicleDetails(1L));
    }

    @Test
    void unknownVehicleDetailsRejected() {
        when(vehicleRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.getVehicleDetails(1L));
    }

    @Test
    void vehicleDetailsIncludeImages() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));

        VehicleImage image = new VehicleImage();
        image.setImageId(10L);
        image.setImageUrl("https://example.com/car.jpg");
        image.setIsPrimary(true);
        when(vehicleImageRepository.findByVehicleVehicleIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(image));

        VehicleResponse response = vehicleService.getVehicleDetails(1L);

        assertNotNull(response.getImages());
        assertEquals(1, response.getImages().size());
        assertEquals("https://example.com/car.jpg", response.getImages().get(0).getImageUrl());
        assertTrue(response.getImages().get(0).getIsPrimary());
    }

    @Test
    void addImageWithPrimaryUnsetsPreviousPrimary() {
        when(vehicleRepository.findById(1L))
                .thenReturn(Optional.of(buildVehicle(1L, VehicleStatus.AVAILABLE)));

        VehicleImage oldPrimary = new VehicleImage();
        oldPrimary.setImageId(10L);
        oldPrimary.setIsPrimary(true);
        when(vehicleImageRepository.findByVehicleVehicleIdAndIsPrimaryTrue(1L))
                .thenReturn(List.of(oldPrimary));
        when(vehicleImageRepository.save(any(VehicleImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VehicleImageRequest request = new VehicleImageRequest();
        request.setImageUrl("https://example.com/new.jpg");
        request.setIsPrimary(true);

        VehicleImageResponse response = vehicleService.addVehicleImage(1L, request);

        assertTrue(response.getIsPrimary());
        assertFalse(oldPrimary.getIsPrimary());
        verify(vehicleImageRepository, times(2)).save(any(VehicleImage.class));
    }

    @Test
    void addImageToUnknownVehicleRejected() {
        when(vehicleRepository.findById(1L)).thenReturn(Optional.empty());

        VehicleImageRequest request = new VehicleImageRequest();
        request.setImageUrl("https://example.com/new.jpg");

        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.addVehicleImage(1L, request));
    }

    @Test
    void deleteImageSuccess() {
        VehicleImage image = new VehicleImage();
        image.setImageId(10L);
        when(vehicleImageRepository.findByVehicleVehicleIdAndImageId(1L, 10L))
                .thenReturn(Optional.of(image));

        vehicleService.deleteVehicleImage(1L, 10L);

        verify(vehicleImageRepository).delete(image);
    }

    @Test
    void deleteImageBelongingToAnotherVehicleRejected() {
        when(vehicleImageRepository.findByVehicleVehicleIdAndImageId(1L, 99L))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.deleteVehicleImage(1L, 99L));
    }
}
