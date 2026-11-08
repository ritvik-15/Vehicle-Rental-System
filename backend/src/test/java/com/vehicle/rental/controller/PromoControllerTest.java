package com.vehicle.rental.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.PromoValidationResponse;
import com.vehicle.rental.dto.PromoValidateRequest;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.service.PromoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PromoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PromoService promoService;

    @Test
    void unauthenticatedValidateReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE10\",\"rentalAmount\":2000.00}"))
                .andExpect(status().isUnauthorized());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerValidateValidPromoReturnsOk() throws Exception {
        PromoValidationResponse response = new PromoValidationResponse();
        response.setCode("SAVE10");
        response.setDiscountType("PERCENTAGE");
        response.setDiscountValue(new BigDecimal("10.00"));
        response.setMinimumAmount(new BigDecimal("100.00"));
        response.setRentalAmount(new BigDecimal("2000.00"));
        response.setDiscountAmount(new BigDecimal("200.00"));
        response.setFinalAmount(new BigDecimal("1800.00"));
        response.setMessage("Promo code applied successfully");
        when(promoService.validatePromo(any(PromoValidateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "SAVE10", "rentalAmount", 2000.00))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SAVE10"))
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discountAmount").value(200.00))
                .andExpect(jsonPath("$.finalAmount").value(1800.00))
                .andExpect(jsonPath("$.message").value("Promo code applied successfully"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void customerValidateInvalidPromoReturnsBadRequest() throws Exception {
        when(promoService.validatePromo(any(PromoValidateRequest.class)))
                .thenThrow(new InvalidRequestException("Promo code has expired"));

        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "OLD10", "rentalAmount", 2000.00))))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminValidateReturnsOk() throws Exception {
        PromoValidationResponse response = new PromoValidationResponse();
        response.setCode("SAVE10");
        response.setDiscountAmount(new BigDecimal("200.00"));
        when(promoService.validatePromo(any(PromoValidateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "SAVE10", "rentalAmount", 2000.00))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SAVE10"));
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void validateWithMissingCodeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rentalAmount", 2000.00))))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void validateWithInvalidAmountReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/promos/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "SAVE10", "rentalAmount", 0))))
                .andExpect(status().isBadRequest());
    }
}
