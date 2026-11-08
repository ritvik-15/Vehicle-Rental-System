package com.vehicle.rental.service;

import com.vehicle.rental.dto.MaintenanceCreateRequest;
import com.vehicle.rental.dto.MaintenanceResponse;
import com.vehicle.rental.dto.MaintenanceStatusRequest;
import com.vehicle.rental.dto.MaintenanceUpdateRequest;
import com.vehicle.rental.entity.Maintenance;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.MaintenanceStatus;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.MaintenanceRepository;
import com.vehicle.rental.repository.VehicleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class MaintenanceService {

    /**
     * R1-B: explicit maintenance status transition matrix.
     *
     * SCHEDULED  -> IN_PROGRESS, COMPLETED, CANCELLED
     * IN_PROGRESS -> COMPLETED, CANCELLED
     * COMPLETED  -> terminal (no further transitions)
     * CANCELLED  -> terminal (no further transitions)
     *
     * Re-setting the current status is also rejected, matching the
     * rental status endpoint convention. Anything outside the matrix
     * is rejected with a 400 InvalidRequestException.
     */
    private static final Map<MaintenanceStatus, Set<MaintenanceStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            MaintenanceStatus.SCHEDULED,
            EnumSet.of(MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.COMPLETED, MaintenanceStatus.CANCELLED),
            MaintenanceStatus.IN_PROGRESS,
            EnumSet.of(MaintenanceStatus.COMPLETED, MaintenanceStatus.CANCELLED));

    private final MaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;

    public MaintenanceService(MaintenanceRepository maintenanceRepository,
                              VehicleRepository vehicleRepository) {
        this.maintenanceRepository = maintenanceRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceResponse> getAllMaintenance() {
        return maintenanceRepository.findAll(Sort.by(Sort.Direction.DESC, "maintenanceDate"))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse getMaintenanceDetails(Long maintenanceId) {
        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance record not found"));
        return toResponse(maintenance);
    }

    public MaintenanceResponse createMaintenance(MaintenanceCreateRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        validateDates(request.getMaintenanceDate(), request.getCompletionDate());

        Maintenance maintenance = new Maintenance();
        maintenance.setVehicle(vehicle);
        maintenance.setMaintenanceType(request.getMaintenanceType().trim());
        maintenance.setDescription(request.getDescription());
        maintenance.setMaintenanceDate(request.getMaintenanceDate());
        maintenance.setCompletionDate(request.getCompletionDate());
        maintenance.setCost(request.getCost());
        maintenance.setStatus(MaintenanceStatus.SCHEDULED);
        maintenance.setServiceProvider(request.getServiceProvider());
        maintenance.setNotes(request.getNotes());
        return toResponse(maintenanceRepository.save(maintenance));
    }

    public MaintenanceResponse updateMaintenance(Long maintenanceId, MaintenanceUpdateRequest request) {
        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance record not found"));
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        validateDates(request.getMaintenanceDate(), request.getCompletionDate());

        maintenance.setVehicle(vehicle);
        maintenance.setMaintenanceType(request.getMaintenanceType().trim());
        maintenance.setDescription(request.getDescription());
        maintenance.setMaintenanceDate(request.getMaintenanceDate());
        maintenance.setCompletionDate(request.getCompletionDate());
        maintenance.setCost(request.getCost());
        maintenance.setServiceProvider(request.getServiceProvider());
        maintenance.setNotes(request.getNotes());
        return toResponse(maintenanceRepository.save(maintenance));
    }

    public MaintenanceResponse updateMaintenanceStatus(Long maintenanceId, MaintenanceStatusRequest request) {
        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance record not found"));

        MaintenanceStatus newStatus;
        try {
            newStatus = MaintenanceStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid maintenance status");
        }

        MaintenanceStatus current = maintenance.getStatus();
        if (newStatus == current) {
            throw new InvalidRequestException("Maintenance status is already " + current.name());
        }
        Set<MaintenanceStatus> allowed = ALLOWED_STATUS_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new InvalidRequestException(
                    "Invalid maintenance status transition from " + current.name() + " to " + newStatus.name());
        }

        maintenance.setStatus(newStatus);
        return toResponse(maintenanceRepository.save(maintenance));
    }

    private void validateDates(LocalDate maintenanceDate, LocalDate completionDate) {
        if (completionDate != null && completionDate.isBefore(maintenanceDate)) {
            throw new InvalidRequestException("Completion date cannot be before maintenance date");
        }
    }

    private MaintenanceResponse toResponse(Maintenance maintenance) {
        MaintenanceResponse response = new MaintenanceResponse();
        response.setMaintenanceId(maintenance.getMaintenanceId());
        response.setVehicleId(maintenance.getVehicle().getVehicleId());
        response.setVehicleRegistrationNumber(maintenance.getVehicle().getRegistrationNumber());
        response.setVehicleBrand(maintenance.getVehicle().getBrand());
        response.setVehicleModel(maintenance.getVehicle().getModel());
        response.setMaintenanceType(maintenance.getMaintenanceType());
        response.setDescription(maintenance.getDescription());
        response.setMaintenanceDate(maintenance.getMaintenanceDate());
        response.setCompletionDate(maintenance.getCompletionDate());
        response.setCost(maintenance.getCost());
        response.setStatus(maintenance.getStatus().name());
        response.setServiceProvider(maintenance.getServiceProvider());
        response.setNotes(maintenance.getNotes());
        response.setCreatedAt(maintenance.getCreatedAt());
        response.setUpdatedAt(maintenance.getUpdatedAt());
        return response;
    }
}
