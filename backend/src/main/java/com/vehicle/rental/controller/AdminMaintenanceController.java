package com.vehicle.rental.controller;

import com.vehicle.rental.dto.MaintenanceCreateRequest;
import com.vehicle.rental.dto.MaintenanceResponse;
import com.vehicle.rental.dto.MaintenanceStatusRequest;
import com.vehicle.rental.dto.MaintenanceUpdateRequest;
import com.vehicle.rental.service.MaintenanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/maintenance")
public class AdminMaintenanceController {

    private final MaintenanceService maintenanceService;

    public AdminMaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public List<MaintenanceResponse> getAllMaintenance() {
        return maintenanceService.getAllMaintenance();
    }

    @PostMapping
    public ResponseEntity<MaintenanceResponse> createMaintenance(
            @Valid @RequestBody MaintenanceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(maintenanceService.createMaintenance(request));
    }

    @GetMapping("/{id}")
    public MaintenanceResponse getMaintenance(@PathVariable Long id) {
        return maintenanceService.getMaintenanceDetails(id);
    }

    @PutMapping("/{id}")
    public MaintenanceResponse updateMaintenance(@PathVariable Long id,
                                                  @Valid @RequestBody MaintenanceUpdateRequest request) {
        return maintenanceService.updateMaintenance(id, request);
    }

    @PatchMapping("/{id}/status")
    public MaintenanceResponse updateMaintenanceStatus(@PathVariable Long id,
                                                      @Valid @RequestBody MaintenanceStatusRequest request) {
        return maintenanceService.updateMaintenanceStatus(id, request);
    }
}
