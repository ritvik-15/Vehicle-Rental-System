package com.vehicle.rental.controller;

import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.dto.RentalStatusRequest;
import com.vehicle.rental.service.RentalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9 admin rental management. Secured by the existing
 * /api/admin/** -> hasRole("ADMIN") rule in SecurityConfig; all transition
 * rules live in RentalService (admin status matrix, /return completion).
 */
@RestController
@RequestMapping("/api/admin/rentals")
public class AdminRentalController {

    private final RentalService rentalService;

    public AdminRentalController(RentalService rentalService) {
        this.rentalService = rentalService;
    }

    @GetMapping
    public List<RentalResponse> getAllRentals() {
        return rentalService.getAllRentals();
    }

    @GetMapping("/{id}")
    public RentalResponse getRentalDetails(@PathVariable Long id) {
        // Existing admin-permissive detail lookup — reused, not duplicated.
        return rentalService.getRentalDetails(id);
    }

    @PatchMapping("/{id}/status")
    public RentalResponse updateRentalStatus(@PathVariable Long id,
                                             @Valid @RequestBody RentalStatusRequest request) {
        return rentalService.updateRentalStatusForAdmin(id, request);
    }

    @PatchMapping("/{id}/return")
    public RentalResponse returnRental(@PathVariable Long id) {
        return rentalService.returnRentalForAdmin(id);
    }
}
