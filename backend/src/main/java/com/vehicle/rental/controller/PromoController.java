package com.vehicle.rental.controller;

import com.vehicle.rental.dto.PromoValidateRequest;
import com.vehicle.rental.dto.PromoValidationResponse;
import com.vehicle.rental.service.PromoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promos")
public class PromoController {

    private final PromoService promoService;

    public PromoController(PromoService promoService) {
        this.promoService = promoService;
    }

    @PostMapping("/validate")
    public PromoValidationResponse validatePromo(@Valid @RequestBody PromoValidateRequest request) {
        return promoService.validatePromo(request);
    }
}
