package com.vehicle.rental.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.PaymentSimulateRequest;
import com.vehicle.rental.dto.PaymentSimulateResponse;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.VehicleExceptionHandler;
import com.vehicle.rental.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone (no Spring context, no database) coverage of
 * POST /api/payments/simulate response mapping for the demo outcome
 * selector. PaymentService is mocked, so no live database is touched.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSimulateControllerStandaloneTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new VehicleExceptionHandler())
                .build();
    }

    private PaymentSimulateResponse simulateResponse(String paymentStatus, String rentalStatus) {
        PaymentSimulateResponse response = new PaymentSimulateResponse();
        response.setPaymentId(99L);
        response.setRentalId(10L);
        response.setAmount(new BigDecimal("3312.00"));
        response.setPaymentStatus(paymentStatus);
        response.setRentalStatus(rentalStatus);
        response.setDemoReference("DEMO-ref");
        response.setMessage("SIMULATED DEMO PAYMENT - outcome " + paymentStatus);
        return response;
    }

    private String body(String simulatedStatus) throws Exception {
        PaymentSimulateRequest request = new PaymentSimulateRequest(10L);
        if (simulatedStatus != null) {
            request.setSimulatedStatus(simulatedStatus);
        }
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void simulateSuccessReturns200WithStatuses() throws Exception {
        when(paymentService.simulatePayment(any()))
                .thenReturn(simulateResponse("SUCCESS", "CONFIRMED"));

        mockMvc.perform(post("/api/payments/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.rentalStatus").value("CONFIRMED"));
    }

    @Test
    void simulateFailedReturns200WithPendingRental() throws Exception {
        when(paymentService.simulatePayment(any()))
                .thenReturn(simulateResponse("FAILED", "PENDING"));

        mockMvc.perform(post("/api/payments/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("FAILED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.rentalStatus").value("PENDING"));
    }

    @Test
    void simulateCancelledReturns200WithPendingRental() throws Exception {
        when(paymentService.simulatePayment(any()))
                .thenReturn(simulateResponse("CANCELLED", "PENDING"));

        mockMvc.perform(post("/api/payments/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.rentalStatus").value("PENDING"));
    }

    @Test
    void simulateUnknownOutcomeReturns400() throws Exception {
        when(paymentService.simulatePayment(any()))
                .thenThrow(new InvalidRequestException(
                        "simulatedStatus must be SUCCESS, FAILED, or CANCELLED"));

        mockMvc.perform(post("/api/payments/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PARTIAL")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "simulatedStatus must be SUCCESS, FAILED, or CANCELLED")));
    }

    @Test
    void simulateAlreadyPaidRentalReturns409() throws Exception {
        when(paymentService.simulatePayment(any()))
                .thenThrow(new DuplicateResourceException("Rental has already been paid"));

        mockMvc.perform(post("/api/payments/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("FAILED")))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Rental has already been paid")));
    }
}
