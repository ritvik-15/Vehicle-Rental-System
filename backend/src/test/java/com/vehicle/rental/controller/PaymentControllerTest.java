package com.vehicle.rental.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.PaymentCreateOrderRequest;
import com.vehicle.rental.dto.PaymentCreateOrderResponse;
import com.vehicle.rental.dto.PaymentVerifyRequest;
import com.vehicle.rental.dto.PaymentVerifyResponse;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.ForbiddenException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.PaymentFailedException;
import com.vehicle.rental.exception.PaymentGatewayException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private String createOrderJson() throws Exception {
        return objectMapper.writeValueAsString(new PaymentCreateOrderRequest(10L));
    }

    private String verifyJson() throws Exception {
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setRazorpayOrderId("order_rzp1");
        request.setRazorpayPaymentId("pay_1");
        request.setRazorpaySignature("signature_abc");
        return objectMapper.writeValueAsString(request);
    }

    private PaymentCreateOrderResponse createOrderResponse() {
        PaymentCreateOrderResponse response = new PaymentCreateOrderResponse();
        response.setPaymentId(99L);
        response.setRentalId(10L);
        response.setRazorpayOrderId("order_rzp1");
        response.setAmountPaise(331200L);
        response.setCurrency("INR");
        response.setKeyId("rzp_test_public");
        return response;
    }

    private PaymentVerifyResponse verifyResponse() {
        PaymentVerifyResponse response = new PaymentVerifyResponse();
        response.setPaymentId(99L);
        response.setRentalId(10L);
        response.setRazorpayOrderId("order_rzp1");
        response.setRazorpayPaymentId("pay_1");
        response.setAmount(new BigDecimal("3312.00"));
        response.setPaymentStatus("SUCCESS");
        response.setRentalStatus("CONFIRMED");
        return response;
    }

    @Test
    void createOrderUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderJson()))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).createOrderForRental(any());
    }

    @Test
    void verifyUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson()))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).verifyPayment(any());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void createOrderAsAdminReturnsForbidden() throws Exception {
        when(paymentService.createOrderForRental(any()))
                .thenThrow(new ForbiddenException("Only customers can make payments"));

        mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only customers can make payments"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void verifyAsAdminReturnsForbidden() throws Exception {
        when(paymentService.verifyPayment(any()))
                .thenThrow(new ForbiddenException("Only customers can verify payments"));

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson()))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void createOrderWithMissingRentalIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));

        verify(paymentService, never()).createOrderForRental(any());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void verifyWithMissingFieldsReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));

        verify(paymentService, never()).verifyPayment(any());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void createOrderSuccessReturnsCreatedWithSafeFieldsOnly() throws Exception {
        when(paymentService.createOrderForRental(any())).thenReturn(createOrderResponse());

        String body = mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(99))
                .andExpect(jsonPath("$.rentalId").value(10))
                .andExpect(jsonPath("$.razorpayOrderId").value("order_rzp1"))
                .andExpect(jsonPath("$.amountPaise").value(331200))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.keyId").value("rzp_test_public"))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(
                body.toLowerCase().contains("secret"), "response must never expose a secret: " + body);
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void verifySuccessReturnsOkWithStateFields() throws Exception {
        when(paymentService.verifyPayment(any())).thenReturn(verifyResponse());

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(99))
                .andExpect(jsonPath("$.rentalId").value(10))
                .andExpect(jsonPath("$.razorpayPaymentId").value("pay_1"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.rentalStatus").value("CONFIRMED"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void verifyInvalidSignatureReturnsBadRequest() throws Exception {
        when(paymentService.verifyPayment(any()))
                .thenThrow(new InvalidRequestException("Payment signature verification failed"));

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Payment signature verification failed"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void verifyAuthoritativeFailureReturnsBadRequest() throws Exception {
        when(paymentService.verifyPayment(any()))
                .thenThrow(new PaymentFailedException("Payment failed at Razorpay"));

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Payment failed at Razorpay"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void duplicateVerificationReturnsConflict() throws Exception {
        when(paymentService.verifyPayment(any()))
                .thenThrow(new DuplicateResourceException("Payment has already been processed"));

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Payment has already been processed"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void unknownPaymentReturnsNotFound() throws Exception {
        when(paymentService.verifyPayment(any()))
                .thenThrow(new ResourceNotFoundException("Payment not found"));

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson()))
                .andExpect(status().isNotFound());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void gatewayFailureReturnsBadGatewayWithoutLeakingDetails() throws Exception {
        when(paymentService.createOrderForRental(any()))
                .thenThrow(new PaymentGatewayException("Payment gateway is not configured"));

        mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderJson()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Payment gateway is not configured"));
    }
}
