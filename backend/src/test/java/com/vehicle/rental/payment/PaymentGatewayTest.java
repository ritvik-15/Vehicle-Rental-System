package com.vehicle.rental.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.PaymentGatewayException;
import com.vehicle.rental.repository.PaymentRepository;
import com.vehicle.rental.repository.RentalRepository;
import com.vehicle.rental.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Phase 3 contract tests, adapted in Phase 8 to the real gateway contract:
 * interface abstraction, exact INR->paise conversion, HMAC signature
 * verification, and the rule that an unconfigured gateway fails loudly
 * instead of fabricating order IDs.
 */
class PaymentGatewayTest {

    private static final String SECRET = "test_secret_key_123";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RazorpayPaymentGateway configured() {
        return new RazorpayPaymentGateway("rzp_test_key", SECRET, "", MAPPER);
    }

    private RazorpayPaymentGateway unconfigured() {
        return new RazorpayPaymentGateway("", "", "", MAPPER);
    }

    private String hmac(String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void razorpayGatewayImplementsPaymentGateway() {
        PaymentGateway gateway = configured();
        assertNotNull(gateway);
        assertInstanceOf(PaymentGateway.class, gateway);
        assertEquals("rzp_test_key", gateway.getKeyId());
    }

    @Test
    void paymentServiceDependsOnGatewayAbstraction() {
        PaymentService service = new PaymentService(
                mock(PaymentGateway.class),
                mock(PaymentRepository.class),
                mock(RentalRepository.class),
                mock(UserRepository.class));
        assertNotNull(service);
    }

    @Test
    void unconfiguredGatewayRejectsCreateOrderInsteadOfFabricatingOne() {
        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class,
                () -> unconfigured().createOrder(new BigDecimal("100.00"), "receipt_1"));
        assertEquals("Payment gateway is not configured", ex.getMessage());
    }

    @Test
    void unconfiguredGatewayRejectsFetchPayment() {
        assertThrows(PaymentGatewayException.class,
                () -> unconfigured().fetchPayment("pay_123"));
    }

    @Test
    void unconfiguredGatewayRejectsSignatureVerification() {
        assertThrows(PaymentGatewayException.class,
                () -> unconfigured().verifySignature("order_1", "pay_1", "signature"));
    }

    @Test
    void verifySignatureAcceptsValidHmacForTrustedOrderAndPayment() throws Exception {
        String orderId = "order_abc123";
        String paymentId = "pay_xyz789";
        String signature = hmac(orderId + "|" + paymentId);

        assertTrue(configured().verifySignature(orderId, paymentId, signature));
    }

    @Test
    void verifySignatureRejectsSignatureForDifferentOrder() throws Exception {
        String signature = hmac("order_other|pay_xyz789");

        assertFalse(configured().verifySignature("order_abc123", "pay_xyz789", signature));
    }

    @Test
    void verifySignatureRejectsTamperedSignature() throws Exception {
        String orderId = "order_abc123";
        String paymentId = "pay_xyz789";
        String signature = hmac(orderId + "|" + paymentId);
        String tampered = "d" + signature.substring(1);

        assertFalse(configured().verifySignature(orderId, paymentId, tampered));
    }

    @Test
    void verifySignatureRejectsMissingInputsWithoutThrowing() {
        RazorpayPaymentGateway gateway = configured();
        assertFalse(gateway.verifySignature(null, "pay_1", "sig"));
        assertFalse(gateway.verifySignature("order_1", "", "sig"));
        assertFalse(gateway.verifySignature("order_1", "pay_1", null));
        assertFalse(gateway.verifySignature("order_1", "pay_1", " "));
    }

    @Test
    void inrToPaiseConversionIsExact() {
        assertEquals(10050L, PaymentGateway.toPaise(new BigDecimal("100.50")));
        assertEquals(331200L, PaymentGateway.toPaise(new BigDecimal("3312.00")));
        assertEquals(1L, PaymentGateway.toPaise(new BigDecimal("0.01")));
        assertEquals(0L, PaymentGateway.toPaise(BigDecimal.ZERO));
        assertEquals(302880L, PaymentGateway.toPaise(new BigDecimal("3028.80")));
    }

    @Test
    void inrToPaiseRejectsExcessiveScaleInsteadOfRounding() {
        assertThrows(InvalidRequestException.class,
                () -> PaymentGateway.toPaise(new BigDecimal("100.505")));
        assertThrows(InvalidRequestException.class, () -> PaymentGateway.toPaise(null));
    }
}
