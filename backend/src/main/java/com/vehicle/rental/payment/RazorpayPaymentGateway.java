package com.vehicle.rental.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.PaymentGatewayException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Real Razorpay integration over the public REST API using only JDK +
 * Spring (RestTemplate, Jackson, javax.crypto HMAC-SHA256) — no extra
 * dependencies. Reads credentials from RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET
 * via application properties; when unconfigured every call fails loudly with
 * {@link PaymentGatewayException} instead of fabricating orders or results.
 *
 * Phase 10 adds order fetch and webhook signature verification for recovery.
 */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final String BASE_URL = "https://api.razorpay.com/v1";
    private static final String CURRENCY = "INR";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RazorpayPaymentGateway(
            @Value("${razorpay.key.id:}") String keyId,
            @Value("${razorpay.key.secret:}") String keySecret,
            @Value("${razorpay.webhook.secret:}") String webhookSecret,
            ObjectMapper objectMapper) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String createOrder(BigDecimal amountInRupees, String receipt) {
        requireConfigured();
        try {
            long amountPaise = PaymentGateway.toPaise(amountInRupees);
            if (amountPaise <= 0) {
                throw new InvalidRequestException("Rental amount must be greater than zero");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", amountPaise);
            body.put("currency", CURRENCY);
            body.put("receipt", receipt);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    BASE_URL + "/orders", new HttpEntity<>(body, authHeaders()), String.class);
            JsonNode node = objectMapper.readTree(response.getBody());
            String orderId = node == null ? "" : node.path("id").asText("");
            if (!orderId.startsWith("order_")) {
                throw new PaymentGatewayException("Payment gateway returned an invalid response");
            }
            return orderId;
        } catch (PaymentGatewayException | InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Unable to create payment order");
        }
    }

    @Override
    public RazorpayPaymentInfo fetchPayment(String gatewayPaymentId) {
        requireConfigured();
        if (gatewayPaymentId == null || gatewayPaymentId.isBlank()) {
            throw new InvalidRequestException("Razorpay payment ID is required");
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/payments/{id}",
                    HttpMethod.GET,
                    new HttpEntity<Void>(authHeaders()),
                    String.class,
                    gatewayPaymentId);
            JsonNode node = objectMapper.readTree(response.getBody());
            if (node == null || !node.has("id") || !node.path("amount").isNumber()) {
                throw new PaymentGatewayException("Payment gateway returned an invalid response");
            }
            return new RazorpayPaymentInfo(
                    node.path("order_id").asText(""),
                    node.path("status").asText(""),
                    node.path("amount").asLong(),
                    node.path("currency").asText(""));
        } catch (PaymentGatewayException | InvalidRequestException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new InvalidRequestException("Razorpay payment not found");
        } catch (Exception e) {
            throw new PaymentGatewayException("Payment gateway is unavailable");
        }
    }

    @Override
    public RazorpayOrderInfo fetchOrder(String gatewayOrderId) {
        requireConfigured();
        if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
            throw new InvalidRequestException("Razorpay order ID is required");
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/orders/{id}",
                    HttpMethod.GET,
                    new HttpEntity<Void>(authHeaders()),
                    String.class,
                    gatewayOrderId);
            JsonNode node = objectMapper.readTree(response.getBody());
            if (node == null || !node.has("id") || !node.path("amount").isNumber()) {
                throw new PaymentGatewayException("Payment gateway returned an invalid response");
            }
            return new RazorpayOrderInfo(
                    node.path("id").asText(""),
                    node.path("status").asText(""),
                    node.path("amount").asLong(),
                    node.path("currency").asText(""),
                    node.path("receipt").asText(""));
        } catch (PaymentGatewayException | InvalidRequestException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new InvalidRequestException("Razorpay order not found");
        } catch (Exception e) {
            throw new PaymentGatewayException("Payment gateway is unavailable");
        }
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        if (orderId == null || orderId.isBlank()
                || paymentId == null || paymentId.isBlank()
                || signature == null || signature.isBlank()) {
            return false;
        }
        requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new PaymentGatewayException("Payment gateway is unavailable");
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            return false;
        }
        requireConfigured();
        if (webhookSecret.isEmpty()) {
            throw new PaymentGatewayException("Webhook secret not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new PaymentGatewayException("Payment gateway is unavailable");
        }
    }

    @Override
    public String getKeyId() {
        return keyId;
    }

    private void requireConfigured() {
        if (keyId.isEmpty() || keySecret.isEmpty()) {
            throw new PaymentGatewayException("Payment gateway is not configured");
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String credentials = keyId + ":" + keySecret;
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        return headers;
    }
}
