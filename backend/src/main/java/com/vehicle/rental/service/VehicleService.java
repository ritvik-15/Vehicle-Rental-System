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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class VehicleService {

    private static final Map<String, String> ALLOWED_SORT_FIELDS = Map.of(
            "dailyRate", "dailyRate",
            "brand", "brand",
            "model", "model",
            "registrationNumber", "registrationNumber",
            "createdAt", "createdAt"
    );

    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;

    public VehicleService(VehicleRepository vehicleRepository,
                          VehicleImageRepository vehicleImageRepository) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleImageRepository = vehicleImageRepository;
    }

    public VehicleResponse createVehicle(VehicleCreateRequest request) {
        if (vehicleRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException("Registration number already exists");
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber(request.getRegistrationNumber());
        vehicle.setBrand(request.getBrand());
        vehicle.setModel(request.getModel());
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setDescription(request.getDescription());
        vehicle.setDailyRate(request.getDailyRate());
        vehicle.setSeatingCapacity(request.getSeatingCapacity());
        vehicle.setFuelType(request.getFuelType());
        vehicle.setTransmission(request.getTransmission());
        vehicle.setLocation(request.getLocation());
        vehicle.setStatus(VehicleStatus.AVAILABLE);

        return toResponse(vehicleRepository.save(vehicle));
    }

    public VehicleResponse updateVehicle(Long vehicleId, VehicleUpdateRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        if (vehicleRepository.existsByRegistrationNumberAndVehicleIdNot(
                request.getRegistrationNumber(), vehicleId)) {
            throw new DuplicateResourceException("Registration number already exists");
        }

        vehicle.setRegistrationNumber(request.getRegistrationNumber());
        vehicle.setBrand(request.getBrand());
        vehicle.setModel(request.getModel());
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setDescription(request.getDescription());
        vehicle.setDailyRate(request.getDailyRate());
        vehicle.setSeatingCapacity(request.getSeatingCapacity());
        vehicle.setFuelType(request.getFuelType());
        vehicle.setTransmission(request.getTransmission());
        vehicle.setLocation(request.getLocation());

        return toResponse(vehicleRepository.save(vehicle));
    }

    public VehicleResponse updateVehicleStatus(Long vehicleId, VehicleStatusRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        VehicleStatus newStatus;
        try {
            newStatus = VehicleStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid vehicle status");
        }

        if (newStatus != VehicleStatus.AVAILABLE && newStatus != VehicleStatus.INACTIVE) {
            throw new InvalidRequestException(
                    "Only AVAILABLE or INACTIVE status can be set manually");
        }

        vehicle.setStatus(newStatus);
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listVehicles(String search, VehicleType vehicleType,
                                              String location, FuelType fuelType,
                                              TransmissionType transmission,
                                              VehicleStatus status, String sortBy, String sortDir) {
        VehicleStatus statusFilter =
                (status == null || status == VehicleStatus.INACTIVE) ? null : status;

        List<Vehicle> vehicles = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, search, vehicleType, location, fuelType,
                transmission, statusFilter, buildSort(sortBy, sortDir));

        return vehicles.stream().map(this::toDetailResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleDetails(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        if (vehicle.getStatus() == VehicleStatus.INACTIVE) {
            throw new ResourceNotFoundException("Vehicle not found");
        }

        return toDetailResponse(vehicle);
    }

    /**
     * Phase 11C admin detail: identical response mapping as the public
     * detail (including images), but without the INACTIVE visibility rule
     * so admins can view any vehicle. ADMIN-only via /api/admin/**.
     */
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleDetailsForAdmin(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        return toDetailResponse(vehicle);
    }

    /**
     * Phase 11C admin fleet list: every status (incl. INACTIVE), createdAt
     * DESC with vehicleId ASC as deterministic tie-break, no pagination or
     * filter/sort parameters, images left null by contract here (Phase 12F
     * added them to the public list mapping only). Read-only; mutates nothing.
     */
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehiclesForAdmin() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.ASC, "vehicleId"));
        return vehicleRepository.findAll(sort).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private VehicleResponse toDetailResponse(Vehicle vehicle) {
        VehicleResponse response = toResponse(vehicle);
        response.setImages(vehicleImageRepository
                .findByVehicleVehicleIdOrderByCreatedAtAsc(vehicle.getVehicleId()).stream()
                .map(this::toImageResponse)
                .collect(Collectors.toList()));
        return response;
    }

    public VehicleImageResponse addVehicleImage(Long vehicleId, VehicleImageRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        boolean makePrimary = Boolean.TRUE.equals(request.getIsPrimary());

        if (makePrimary) {
            List<VehicleImage> existingPrimary =
                    vehicleImageRepository.findByVehicleVehicleIdAndIsPrimaryTrue(vehicleId);
            for (VehicleImage image : existingPrimary) {
                image.setIsPrimary(false);
                vehicleImageRepository.save(image);
            }
        }

        VehicleImage image = new VehicleImage();
        image.setVehicle(vehicle);
        image.setImageUrl(request.getImageUrl());
        image.setIsPrimary(makePrimary);

        return toImageResponse(vehicleImageRepository.save(image));
    }

    public void deleteVehicleImage(Long vehicleId, Long imageId) {
        VehicleImage image = vehicleImageRepository
                .findByVehicleVehicleIdAndImageId(vehicleId, imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for this vehicle"));
        vehicleImageRepository.delete(image);
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.getOrDefault(sortBy, "createdAt");
        Sort.Direction direction = Sort.Direction.ASC.name().equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        VehicleResponse response = new VehicleResponse();
        response.setVehicleId(vehicle.getVehicleId());
        response.setRegistrationNumber(vehicle.getRegistrationNumber());
        response.setBrand(vehicle.getBrand());
        response.setModel(vehicle.getModel());
        response.setVehicleType(vehicle.getVehicleType().name());
        response.setDescription(vehicle.getDescription());
        response.setDailyRate(vehicle.getDailyRate());
        response.setSeatingCapacity(vehicle.getSeatingCapacity());
        response.setFuelType(vehicle.getFuelType().name());
        response.setTransmission(vehicle.getTransmission().name());
        response.setLocation(vehicle.getLocation());
        response.setStatus(vehicle.getStatus().name());
        response.setCreatedAt(vehicle.getCreatedAt());
        response.setUpdatedAt(vehicle.getUpdatedAt());
        return response;
    }

    private VehicleImageResponse toImageResponse(VehicleImage image) {
        VehicleImageResponse response = new VehicleImageResponse();
        response.setImageId(image.getImageId());
        response.setImageUrl(image.getImageUrl());
        response.setIsPrimary(image.getIsPrimary());
        response.setCreatedAt(image.getCreatedAt());
        return response;
    }
}
