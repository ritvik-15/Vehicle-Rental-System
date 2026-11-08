package com.vehicle.rental.controller;

import com.vehicle.rental.dto.PromoCodeAdminCreateRequest;
import com.vehicle.rental.dto.PromoCodeAdminResponse;
import com.vehicle.rental.dto.PromoCodeAdminStatusRequest;
import com.vehicle.rental.service.AdminPromoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * R2: admin-only promo code management. Every /api/admin/** route
 * already requires the ADMIN role via SecurityConfig, so customers
 * cannot reach these endpoints.
 */
@RestController
@RequestMapping("/api/admin/promos")
public class AdminPromoController {

    private final AdminPromoService adminPromoService;

    public AdminPromoController(AdminPromoService adminPromoService) {
        this.adminPromoService = adminPromoService;
    }

    @GetMapping
    public List<PromoCodeAdminResponse> getAllPromoCodes() {
        return adminPromoService.getAllPromoCodes();
    }

    @PostMapping
    public ResponseEntity<PromoCodeAdminResponse> createPromoCode(
            @Valid @RequestBody PromoCodeAdminCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminPromoService.createPromoCode(request));
    }

    @PatchMapping("/{code}/status")
    public PromoCodeAdminResponse updatePromoCodeStatus(
            @PathVariable String code,
            @Valid @RequestBody PromoCodeAdminStatusRequest request) {
        return adminPromoService.updatePromoCodeStatus(code, request);
    }
}
