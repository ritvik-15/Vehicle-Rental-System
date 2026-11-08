package com.vehicle.rental.dto;

/**
 * Create-order response. Contains only frontend-safe fields: the exact amount
 * in paise, the currency, the gateway order ID and the publishable key ID.
 * Never contains the key secret.
 */
public class PaymentCreateOrderResponse {

    private Long paymentId;
    private Long rentalId;
    private String razorpayOrderId;
    private Long amountPaise;
    private String currency;
    private String keyId;

    public PaymentCreateOrderResponse() {
    }

    public PaymentCreateOrderResponse(Long paymentId, Long rentalId, String razorpayOrderId,
                                      Long amountPaise, String currency, String keyId) {
        this.paymentId = paymentId;
        this.rentalId = rentalId;
        this.razorpayOrderId = razorpayOrderId;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.keyId = keyId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Long getRentalId() {
        return rentalId;
    }

    public void setRentalId(Long rentalId) {
        this.rentalId = rentalId;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public Long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(Long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
}
