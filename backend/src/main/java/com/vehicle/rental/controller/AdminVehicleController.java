package com.vehicle.rental.controller;

import com.vehicle.rental.dto.VehicleCreateRequest;
import com.vehicle.rental.dto.VehicleImageRequest;
import com.vehicle.rental.dto.VehicleImageResponse;
import com.vehicle.rental.dto.VehicleResponse;
import com.vehicle.rental.dto.VehicleStatusRequest;
import com.vehicle.rental.dto.VehicleUpdateRequest;
import com.vehicle.rental.service.VehicleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/vehicles")
public class AdminVehicleController {

    private final VehicleService vehicleService;

    public AdminVehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(@Valid @RequestBody VehicleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.createVehicle(request));
    }

    @PutMapping("/{id}")
    public VehicleResponse updateVehicle(@PathVariable Long id,
                                         @Valid @RequestBody VehicleUpdateRequest request) {
        return vehicleService.updateVehicle(id, request);
    }

    @PatchMapping("/{id}/status")
    public VehicleResponse updateVehicleStatus(@PathVariable Long id,
                                               @Valid @RequestBody VehicleStatusRequest request) {
        return vehicleService.updateVehicleStatus(id, request);
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<VehicleImageResponse> addVehicleImage(
            @PathVariable Long id,
            @Valid @RequestBody VehicleImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.addVehicleImage(id, request));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteVehicleImage(@PathVariable Long id,
                                                   @PathVariable Long imageId) {
        vehicleService.deleteVehicleImage(id, imageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Phase 11C admin fleet reads, ADMIN-only via the existing /api/admin/**
     * security rule (401 unauthenticated, 403 for CUSTOMER). Unlike the
     * public list, no vehicle status is excluded — INACTIVE vehicles are
     * discoverable here. createdAt DESC, vehicleId ASC tie-break, no
     * pagination or filter parameters. Read-only.
     */
    @GetMapping
    public List<VehicleResponse> getAllVehicles() {
        return vehicleService.getAllVehiclesForAdmin();
    }

    /**
     * Phase 11C admin detail: works for every status including INACTIVE
     * (the public detail keeps its 404-for-INACTIVE rule) and populates
     * the existing images mapping. Read-only.
     */
    @GetMapping("/{id}")
    public VehicleResponse getVehicleDetails(@PathVariable Long id) {
        return vehicleService.getVehicleDetailsForAdmin(id);
    }
}
