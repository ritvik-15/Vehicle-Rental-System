package com.vehicle.rental.service;

import com.vehicle.rental.dto.PromoValidateRequest;
import com.vehicle.rental.dto.PromoValidationResponse;
import com.vehicle.rental.entity.PromoCode;
import com.vehicle.rental.enums.DiscountType;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.repository.PromoCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoServiceTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @InjectMocks
    private PromoService promoService;

    private PromoCode promo(DiscountType type, BigDecimal value, BigDecimal minimum,
                                  BigDecimal maximum, LocalDate start, LocalDate expiry,
                                  Integer usageLimit, int usedCount, String status) {
        PromoCode promo = new PromoCode();
        promo.setPromoId(1L);
        promo.setCode("SAVE10");
        promo.setDiscountType(type);
        promo.setDiscountValue(value);
        promo.setMinimumAmount(minimum);
        promo.setMaximumDiscount(maximum);
        promo.setStartDate(start);
        promo.setExpiryDate(expiry);
        promo.setUsageLimit(usageLimit);
        promo.setUsedCount(usedCount);
        promo.setStatus(status);
        return promo;
    }

    private PromoValidateRequest validateRequest(BigDecimal amount) {
        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("SAVE10");
        request.setRentalAmount(amount);
        return request;
    }

    private void stubPromo(PromoCode promo) {
        when(promoCodeRepository.findByCode("SAVE10")).thenReturn(Optional.of(promo));
    }

    private LocalDate yesterday() { return LocalDate.now().minusDays(1); }
    private LocalDate today() { return LocalDate.now(); }
    private LocalDate tomorrow() { return LocalDate.now().plusDays(1); }
    private LocalDate plusDays(int days) { return LocalDate.now().plusDays(days); }

    @Test
    void validPercentagePromo() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), new BigDecimal("100.00"), null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("2000.00")));

        assertEquals(new BigDecimal("200.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("1800.00"), response.getFinalAmount());
        assertEquals("PERCENTAGE", response.getDiscountType());
        assertEquals("SAVE10", response.getCode());
        assertEquals(new BigDecimal("2000.00"), response.getRentalAmount());
        assertEquals("Promo code applied successfully", response.getMessage());
    }

    @Test
    void validFixedPromo() {
        stubPromo(promo(DiscountType.FIXED, new BigDecimal("150.00"), new BigDecimal("100.00"), null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("2000.00")));

        assertEquals(new BigDecimal("150.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("1850.00"), response.getFinalAmount());
        assertEquals("FIXED", response.getDiscountType());
    }

    @Test
    void percentageWithoutMaximumCap() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("25.00"), BigDecimal.ZERO, null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("800.00")));

        assertEquals(new BigDecimal("200.00"), response.getDiscountAmount());
    }

    @Test
    void maximumDiscountCapsPercentage() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("300.00"),
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("2000.00")));

        // 50% of 2000.00 = 1000.00, capped at maximum_discount 300.00
        assertEquals(new BigDecimal("300.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("1700.00"), response.getFinalAmount());
    }

    @Test
    void inactivePromoRejected() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                yesterday(), plusDays(10), null, 0, "INACTIVE"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(validateRequest(new BigDecimal("2000.00"))));
        assertTrue(exception.getMessage().contains("not active"));
    }

    @Test
    void notYetStartedPromoRejected() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                tomorrow(), plusDays(10), null, 0, "ACTIVE"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(validateRequest(new BigDecimal("2000.00"))));
        assertTrue(exception.getMessage().contains("not yet valid"));
    }

    @Test
    void expiredPromoRejected() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                yesterday().minusDays(5), yesterday(), null, 0, "ACTIVE"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(validateRequest(new BigDecimal("2000.00"))));
        assertTrue(exception.getMessage().contains("expired"));
    }

    @Test
    void minimumAmountFailure() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), new BigDecimal("1000.00"), null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(validateRequest(new BigDecimal("500.00"))));
        assertTrue(exception.getMessage().contains("minimum amount"));
    }

    @Test
    void minimumAmountBoundarySucceeds() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), new BigDecimal("1000.00"), null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("1000.00")));

        assertEquals(new BigDecimal("100.00"), response.getDiscountAmount());
    }

    @Test
    void usageLimitReached() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                yesterday(), plusDays(10), 5, 5, "ACTIVE"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(validateRequest(new BigDecimal("2000.00"))));
        assertTrue(exception.getMessage().contains("usage limit"));
    }

    @Test
    void unlimitedUsageAllowed() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("2000.00")));

        assertEquals(new BigDecimal("200.00"), response.getDiscountAmount());
    }

    @Test
    void discountCannotExceedRentalAmount() {
        stubPromo(promo(DiscountType.FIXED, new BigDecimal("5000.00"), BigDecimal.ZERO, null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("2000.00")));

        // fixed discount 5000.00 exceeds the rental amount: capped at 2000.00
        assertEquals(new BigDecimal("2000.00"), response.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getFinalAmount());
    }

    @Test
    void negativeDiscountValueNeverProducesNegativeDiscount() {
        stubPromo(promo(DiscountType.FIXED, new BigDecimal("-10.00"), BigDecimal.ZERO, null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("2000.00")));

        assertEquals(new BigDecimal("0.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("2000.00"), response.getFinalAmount());
    }

    @Test
    void promoNotFound() {
        when(promoCodeRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        PromoValidateRequest request = new PromoValidateRequest();
        request.setCode("MISSING");
        request.setRentalAmount(new BigDecimal("2000.00"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> promoService.validatePromo(request));
        assertTrue(exception.getMessage().contains("Promo code not found"));
    }

    @Test
    void bigDecimalRoundingPrecision() {
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                yesterday(), plusDays(10), null, 0, "ACTIVE"));

        // 10% of 1000.01 = 100.001 -> 100.00 (HALF_UP, scale 2)
        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("1000.01")));

        assertEquals(new BigDecimal("100.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("900.01"), response.getFinalAmount());
    }

    @Test
    void boundaryDatesValid() {
        // start_date = today, expiry_date = today: today is within the window
        stubPromo(promo(DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                today(), today(), null, 0, "ACTIVE"));

        PromoValidationResponse response = promoService.validatePromo(validateRequest(new BigDecimal("2000.00")));

        assertEquals(new BigDecimal("200.00"), response.getDiscountAmount());
    }
}
