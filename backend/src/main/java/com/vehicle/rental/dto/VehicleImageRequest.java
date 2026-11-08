package com.vehicle.rental.dto;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

public class VehicleImageRequest {

    @NotBlank(message = "Image URL is required")
    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    @URL(message = "Image URL must be a valid URL")
    private String imageUrl;

    private Boolean isPrimary = false;

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
}
