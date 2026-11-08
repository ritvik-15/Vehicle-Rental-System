package com.vehicle.rental.controller;

import com.vehicle.rental.dto.AvailabilityResponse;
import com.vehicle.rental.dto.VehicleResponse;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.service.RentalService;
import com.vehicle.rental.service.VehicleService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final RentalService rentalService;

    public VehicleController(VehicleService vehicleService, RentalService rentalService) {
        this.vehicleService = vehicleService;
        this.rentalService = rentalService;
    }

    @GetMapping
    public List<VehicleResponse> listVehicles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) FuelType fuelType,
            @RequestParam(required = false) TransmissionType transmission,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        return vehicleService.listVehicles(search, vehicleType, location, fuelType,
                transmission, status, sortBy, sortDir);
    }

    @GetMapping("/{id}")
    public VehicleResponse getVehicleDetails(@PathVariable Long id) {
        return vehicleService.getVehicleDetails(id);
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponse checkVehicleAvailability(
            @PathVariable Long id,
            @RequestParam LocalDateTime pickupDateTime,
            @RequestParam LocalDateTime returnDateTime) {
        return rentalService.checkAvailability(id, pickupDateTime, returnDateTime);
    }
}
