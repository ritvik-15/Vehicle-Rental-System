package com.vehicle.rental.dto;

import jakarta.validation.constraints.NotNull;

public class PaymentSimulateRequest {

    @NotNull(message = "rentalId is required")
    private Long rentalId;

    // Optional demo outcome: missing/null/blank means SUCCESS (unchanged
    // behaviour). FAILED and CANCELLED record the attempt without confirming
    // the rental.
    private String simulatedStatus;

    public PaymentSimulateRequest() {
    }

    public PaymentSimulateRequest(Long rentalId) {
        this.rentalId = rentalId;
    }

    public String getSimulatedStatus() {
        return simulatedStatus;
    }

    public void setSimulatedStatus(String simulatedStatus) {
        this.simulatedStatus = simulatedStatus;
    }

    public Long getRentalId() {
        return rentalId;
    }

    public void setRentalId(Long rentalId) {
        this.rentalId = rentalId;
    }
}