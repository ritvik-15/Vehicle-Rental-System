package com.vehicle.rental.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Verbatim Razorpay checkout callback values (snake_case as Razorpay sends
 * them). Amount and status are never accepted from the client — the backend
 * re-reads authoritative values from Razorpay itself.
 */
public class PaymentVerifyRequest {

    @NotBlank(message = "razorpay_order_id is required")
    @JsonProperty("razorpay_order_id")
    @JsonAlias("razorpayOrderId")
    private String razorpayOrderId;

    @NotBlank(message = "razorpay_payment_id is required")
    @JsonProperty("razorpay_payment_id")
    @JsonAlias("razorpayPaymentId")
    private String razorpayPaymentId;

    @NotBlank(message = "razorpay_signature is required")
    @JsonProperty("razorpay_signature")
    @JsonAlias("razorpaySignature")
    private String razorpaySignature;

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getRazorpaySignature() {
        return razorpaySignature;
    }

    public void setRazorpaySignature(String razorpaySignature) {
        this.razorpaySignature = razorpaySignature;
    }
}
