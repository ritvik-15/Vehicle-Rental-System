package com.vehicle.rental.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Accepts exactly the existing RentalStatus enum values, case-insensitively.
 * Which transitions are legal is decided by RentalService (Phase 9 matrix).
 */
public class RentalStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
