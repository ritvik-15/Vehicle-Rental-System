package com.vehicle.rental.controller;

import com.vehicle.rental.dto.PaymentCreateOrderRequest;
import com.vehicle.rental.dto.PaymentCreateOrderResponse;
import com.vehicle.rental.dto.PaymentSimulateRequest;
import com.vehicle.rental.dto.PaymentSimulateResponse;
import com.vehicle.rental.dto.PaymentVerifyRequest;
import com.vehicle.rental.dto.PaymentVerifyResponse;
import com.vehicle.rental.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 8 payment endpoints (CUSTOMER-only, enforced in PaymentService):
 * create a Razorpay order for an owned PENDING rental, and verify the
 * checkout result. No failure/refund/webhook/admin endpoints in this phase.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<PaymentCreateOrderResponse> createOrder(
            @Valid @RequestBody PaymentCreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createOrderForRental(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentVerifyResponse> verify(@Valid @RequestBody PaymentVerifyRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }

    @PostMapping("/simulate")
    public ResponseEntity<PaymentSimulateResponse> simulate(@Valid @RequestBody PaymentSimulateRequest request) {
        return ResponseEntity.ok(paymentService.simulatePayment(request));
    }
}
