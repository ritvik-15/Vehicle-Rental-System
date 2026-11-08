package com.vehicle.rental.exception;

import com.vehicle.rental.controller.AuthController;
import com.vehicle.rental.controller.PaymentController;
import com.vehicle.rental.controller.VehicleController;
import com.vehicle.rental.service.AuthService;
import com.vehicle.rental.service.RentalService;
import com.vehicle.rental.service.VehicleService;
import com.vehicle.rental.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 12B exception-mapping tests.
 *
 * Uses MockMvcBuilders.standaloneSetup: no Spring application context,
 * no database, no MySQL connection — only the real controllers and the
 * two controller-advice classes, so the response each exception
 * actually receives can be verified safely from any machine.
 */
class ExceptionMappingStandaloneTest {

    private MockMvc mockMvc;

    private AuthService authService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        paymentService = mock(PaymentService.class);
        VehicleService vehicleService = mock(VehicleService.class);
        RentalService rentalService = mock(RentalService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authService),
                        new PaymentController(paymentService),
                        new VehicleController(vehicleService, rentalService),
                        new ExplodingController())
                .setControllerAdvice(new VehicleExceptionHandler(), new SecurityExceptionHandler())
                .build();
    }

    // --- genuinely unexpected failures: generic 500, no internal details ---

    @Test
    void unexpectedRuntimeExceptionReturnsGeneric500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/boom/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(content().string(not(containsString("secret internal message"))))
                .andExpect(content().string(not(containsString("SQL fragment"))));
    }

    @Test
    void nonUniqueDataIntegrityViolationReturnsGeneric500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/boom/data-integrity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(content().string(not(containsString("SQL insert"))));
    }

    // --- expected database uniqueness conflicts: 409 ---

    @Test
    void duplicateKeyExceptionReturns409() throws Exception {
        mockMvc.perform(get("/boom/duplicate-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Duplicate resource"));
    }

    // --- Spring MVC framework errors: 400 / 405 ---

    @Test
    void malformedRequestBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    @Test
    void missingRequiredParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/vehicles/1/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Required parameter is missing: pickupDateTime"));
    }

    @Test
    void unsupportedHttpMethodReturns405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/api/vehicles/1/availability"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.error")
                        .value("HTTP method not supported for this endpoint"));
    }

    // --- preserved mappings: genuine 401, business rules, payment errors ---

    @Test
    void genuineLoginFailureRemains401() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new DuplicateResourceException("Email already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Jane Doe\",\"email\":\"jane@example.com\","
                                + "\"phone\":\"9876543210\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already exists"));
    }

    @Test
    void forbiddenRemains403() throws Exception {
        mockMvc.perform(get("/boom/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void notFoundRemains404() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Rental not found"));
    }

    @Test
    void invalidRequestRemains400() throws Exception {
        mockMvc.perform(get("/boom/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void paymentGatewayFailureRemains502() throws Exception {
        when(paymentService.createOrderForRental(any()))
                .thenThrow(new PaymentGatewayException("Payment gateway is unavailable"));

        mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rentalId\":10}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Payment gateway is unavailable"));
    }

    @Test
    void paymentSignatureFailureRemains400() throws Exception {
        when(paymentService.verifyPayment(any()))
                .thenThrow(new PaymentFailedException("Payment failed at Razorpay"));

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razorpayOrderId\":\"order_1\",\"razorpayPaymentId\":\"pay_1\","
                                + "\"razorpaySignature\":\"sig\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Payment failed at Razorpay"));
    }

    /** Stub endpoints that throw representative exceptions directly. */
    @RestController
    static class ExplodingController {

        @GetMapping("/boom/runtime")
        public String runtime() {
            throw new RuntimeException("secret internal message with SQL fragment");
        }

        @GetMapping("/boom/data-integrity")
        public String dataIntegrity() {
            throw new DataIntegrityViolationException("SQL insert into users failed");
        }

        @GetMapping("/boom/duplicate-key")
        public String duplicateKey() {
            throw new DuplicateKeyException("could not execute statement");
        }

        @GetMapping("/boom/forbidden")
        public String forbidden() {
            throw new ForbiddenException("Access denied");
        }

        @GetMapping("/boom/not-found")
        public String notFound() {
            throw new ResourceNotFoundException("Rental not found");
        }

        @GetMapping("/boom/invalid")
        public String invalid() {
            throw new InvalidRequestException("Validation failed");
        }
    }
}
