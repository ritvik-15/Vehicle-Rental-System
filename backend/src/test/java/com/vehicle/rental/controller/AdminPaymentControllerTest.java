package com.vehicle.rental.controller;

import com.vehicle.rental.dto.PaymentResponse;
import com.vehicle.rental.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9: admin payment listing is GET-only, ADMIN-only, and never exposes
 * secret material (none exists on the payment row; asserted explicitly).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    private PaymentResponse sampleResponse() {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(100L);
        response.setRentalId(10L);
        response.setUserId(7L);
        response.setAmount(new BigDecimal("3312.00"));
        response.setPaymentMethod("RAZORPAY");
        response.setRazorpayOrderId("order_ABC123");
        response.setRazorpayPaymentId("pay_XYZ789");
        response.setPaymentStatus("SUCCESS");
        response.setPaymentDate(LocalDateTime.now());
        response.setCreatedAt(LocalDateTime.now());
        return response;
    }

    @Test
    void unauthenticatedListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerRoleListReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminListReturnsOkWithPaymentFields() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value(100))
                .andExpect(jsonPath("$[0].rentalId").value(10))
                .andExpect(jsonPath("$[0].amount").value(3312.00))
                .andExpect(jsonPath("$[0].paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$[0].razorpayOrderId").value("order_ABC123"));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminListEmptyReturnsOkEmptyArray() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminListNeverExposesSecrets() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keySecret").doesNotExist())
                .andExpect(jsonPath("$[0].secret").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].signature").doesNotExist());
    }
}
