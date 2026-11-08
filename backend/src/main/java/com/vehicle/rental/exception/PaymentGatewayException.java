package com.vehicle.rental.exception;

/**
 * Raised when the external payment gateway (Razorpay) cannot be reached,
 * is not configured, or returned an unusable response. Mapped to 502 so it
 * is never swallowed by the generic RuntimeException -> 401 fallback.
 * Messages are fixed and must never contain credentials or raw upstream
 * responses.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }
}
