package com.vehicle.rental.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Accepts exactly the existing UserStatus enum values (ACTIVE / INACTIVE),
 * case-insensitively. No new enum values are introduced.
 */
public class CustomerStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
