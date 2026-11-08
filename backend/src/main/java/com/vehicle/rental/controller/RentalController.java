package com.vehicle.rental.controller;

import com.vehicle.rental.dto.RentalCreateRequest;
import com.vehicle.rental.dto.RentalResponse;
import com.vehicle.rental.service.RentalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rentals")
public class RentalController {

    private final RentalService rentalService;

    public RentalController(RentalService rentalService) {
        this.rentalService = rentalService;
    }

    @PostMapping
    public ResponseEntity<RentalResponse> createRental(@Valid @RequestBody RentalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rentalService.createRental(request));
    }

    @GetMapping("/my")
    public List<RentalResponse> getMyRentals() {
        return rentalService.getMyRentals();
    }

    @GetMapping("/{id}")
    public RentalResponse getRentalDetails(@PathVariable Long id) {
        return rentalService.getRentalDetails(id);
    }

    @PatchMapping("/{id}/cancel")
    public RentalResponse cancelRental(@PathVariable Long id) {
        return rentalService.cancelRental(id);
    }
}
