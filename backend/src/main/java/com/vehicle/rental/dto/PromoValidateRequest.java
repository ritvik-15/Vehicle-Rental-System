package com.vehicle.rental.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class PromoValidateRequest {

    @NotBlank(message = "Promo code is required")
    @Size(max = 50, message = "Promo code must not exceed 50 characters")
    private String code;

    @NotNull(message = "Rental amount is required")
    @DecimalMin(value = "0.01", message = "Rental amount must be greater than 0")
    private BigDecimal rentalAmount;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getRentalAmount() { return rentalAmount; }
    public void setRentalAmount(BigDecimal rentalAmount) { this.rentalAmount = rentalAmount; }
}
