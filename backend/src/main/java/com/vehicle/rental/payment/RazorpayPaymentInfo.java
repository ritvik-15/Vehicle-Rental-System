package com.vehicle.rental.payment;

/**
 * Authoritative payment details fetched from Razorpay's server-side API.
 * Used to verify order relationship, amount, currency and captured status
 * before any local SUCCESS state is written.
 */
public class RazorpayPaymentInfo {

    private final String orderId;
    private final String status;
    private final long amountPaise;
    private final String currency;

    public RazorpayPaymentInfo(String orderId, String status, long amountPaise, String currency) {
        this.orderId = orderId;
        this.status = status;
        this.amountPaise = amountPaise;
        this.currency = currency;
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public long getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
}
