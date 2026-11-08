package com.vehicle.rental.payment;

import java.math.BigDecimal;

/**
 * Port to the external payment gateway. Phase 8 wires the real Razorpay
 * implementation (RazorpayPaymentGateway) behind this interface; tests provide
 * fakes so no network or real credentials are ever required.
 *
 * Phase 10 adds order fetch and webhook signature verification for recovery.
 */
public interface PaymentGateway {

    /**
     * Creates a gateway order for the given INR amount and returns the
     * gateway order ID (Razorpay: "order_...").
     *
     * @throws com.vehicle.rental.exception.PaymentGatewayException when the
     *         gateway is unconfigured or unreachable
     */
    String createOrder(BigDecimal amountInRupees, String receipt);

    /**
     * Fetches authoritative payment details for the given gateway payment ID.
     *
     * @throws com.vehicle.rental.exception.PaymentGatewayException when the
     *         gateway is unconfigured or unreachable
     */
    RazorpayPaymentInfo fetchPayment(String gatewayPaymentId);

    /**
     * Fetches authoritative order details for the given gateway order ID.
     * Used for recovery when frontend verification is lost.
     *
     * @throws com.vehicle.rental.exception.PaymentGatewayException when the
     *         gateway is unconfigured or unreachable
     */
    RazorpayOrderInfo fetchOrder(String gatewayOrderId);

    /**
     * Verifies the checkout signature against the server-side secret using the
     * trusted stored order ID. Returns false on any invalid/missing input.
     */
    boolean verifySignature(String orderId, String paymentId, String signature);

    /**
     * Verifies a Razorpay webhook signature using the webhook secret.
     * Returns false on any invalid/missing input.
     */
    boolean verifyWebhookSignature(String payload, String signature);

    /**
     * Publishable key ID, safe to expose to the frontend. Never the secret.
     */
    String getKeyId();

    /**
     * Exact INR to paise conversion (BigDecimal movePointRight, never
     * double math). Scale greater than 2 is rejected instead of rounded.
     */
    static long toPaise(BigDecimal amountInRupees) {
        try {
            return amountInRupees.movePointRight(2).longValueExact();
        } catch (ArithmeticException | NullPointerException e) {
            throw new com.vehicle.rental.exception.InvalidRequestException("Invalid payment amount");
        }
    }
}
