package com.vehicle.rental.controller;

import com.vehicle.rental.dto.PaymentResponse;
import com.vehicle.rental.payment.PaymentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9 admin payment listing (GET only — refunds, status changes and any
 * gateway interaction are explicitly out of scope). Secured by the existing
 * /api/admin/** -> hasRole("ADMIN") rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentResponse> getAllPayments() {
        return paymentService.getAllPayments();
    }
}
