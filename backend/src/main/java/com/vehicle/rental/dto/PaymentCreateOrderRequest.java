package com.vehicle.rental.dto;

import jakarta.validation.constraints.NotNull;

public class PaymentCreateOrderRequest {

    @NotNull(message = "rentalId is required")
    private Long rentalId;

    public PaymentCreateOrderRequest() {
    }

    public PaymentCreateOrderRequest(Long rentalId) {
        this.rentalId = rentalId;
    }

    public Long getRentalId() {
        return rentalId;
    }

    public void setRentalId(Long rentalId) {
        this.rentalId = rentalId;
    }
}
