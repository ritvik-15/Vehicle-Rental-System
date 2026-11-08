package com.vehicle.rental.exception;

/**
 * Thrown when authoritative Razorpay information says the payment attempt
 * failed or was cancelled. Extends InvalidRequestException so it maps to the
 * project's standard 400 error body. The transactional payment flow declares
 * noRollbackFor on this exception so the FAILED/CANCELLED payment mark is
 * committed while nothing else changes (the rental is never confirmed).
 */
public class PaymentFailedException extends InvalidRequestException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
