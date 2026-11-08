package com.vehicle.rental.payment;

/**
 * Authoritative order details fetched from Razorpay's server-side API.
 * Used for recovery when frontend verification is lost.
 */
public class RazorpayOrderInfo {

    private final String id;
    private final String status;
    private final long amountPaise;
    private final String currency;
    private final String receipt;

    public RazorpayOrderInfo(String id, String status, long amountPaise, String currency, String receipt) {
        this.id = id;
        this.status = status;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.receipt = receipt;
    }

    public String getId() { return id; }
    public String getStatus() { return status; }
    public long getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
    public String getReceipt() { return receipt; }
}