package com.vehicle.rental.service;

import com.vehicle.rental.dto.PromoCodeAdminCreateRequest;
import com.vehicle.rental.dto.PromoCodeAdminResponse;
import com.vehicle.rental.dto.PromoCodeAdminStatusRequest;
import com.vehicle.rental.entity.PromoCode;
import com.vehicle.rental.enums.DiscountType;
import com.vehicle.rental.exception.DuplicateResourceException;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.PromoCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * R2: pure Mockito verification of admin promo-code management.
 * No Spring context and no database access.
 */
@ExtendWith(MockitoExtension.class)
class AdminPromoServiceTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @InjectMocks
    private AdminPromoService adminPromoService;

    private static final LocalDate START = LocalDate.now().plusDays(1);
    private static final LocalDate EXPIRY = LocalDate.now().plusDays(30);

    private PromoCodeAdminCreateRequest createRequest() {
        PromoCodeAdminCreateRequest request = new PromoCodeAdminCreateRequest();
        request.setCode("COLLEGE20");
        request.setDiscountType("PERCENTAGE");
        request.setDiscountValue(new BigDecimal("20"));
        request.setMinimumAmount(new BigDecimal("1000.00"));
        request.setMaximumDiscount(new BigDecimal("500.00"));
        request.setStartDate(START);
        request.setExpiryDate(EXPIRY);
        request.setUsageLimit(100);
        return request;
    }

    private PromoCode savedPromo() {
        PromoCode promo = new PromoCode();
        promo.setCode("COLLEGE20");
        promo.setDiscountType(DiscountType.PERCENTAGE);
        promo.setDiscountValue(new BigDecimal("20.00"));
        promo.setMinimumAmount(new BigDecimal("1000.00"));
        promo.setMaximumDiscount(new BigDecimal("500.00"));
        promo.setStartDate(START);
        promo.setExpiryDate(EXPIRY);
        promo.setUsageLimit(100);
        promo.setUsedCount(0);
        promo.setStatus("ACTIVE");
        promo.setCreatedAt(LocalDateTime.now());
        return promo;
    }

    @Test
    void getAllPromoCodesReturnsMappedList() {
        when(promoCodeRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(savedPromo()));

        List<PromoCodeAdminResponse> list = adminPromoService.getAllPromoCodes();

        assertEquals(1, list.size());
        assertEquals("COLLEGE20", list.get(0).getCode());
        assertEquals("PERCENTAGE", list.get(0).getDiscountType());
        assertEquals(0, list.get(0).getUsedCount());
        assertEquals("ACTIVE", list.get(0).getStatus());
    }

    @Test
    void createPromoCodeSuccess() {
        when(promoCodeRepository.findByCode("COLLEGE20")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PromoCodeAdminResponse response = adminPromoService.createPromoCode(createRequest());

        assertEquals("COLLEGE20", response.getCode());
        assertEquals("PERCENTAGE", response.getDiscountType());
        assertEquals(0, response.getDiscountValue().compareTo(new BigDecimal("20.00")));
        assertEquals(0, response.getMinimumAmount().compareTo(new BigDecimal("1000.00")));
        assertEquals(100, response.getUsageLimit());
        assertEquals(0, response.getUsedCount());
        assertEquals("ACTIVE", response.getStatus());

        ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
        verify(promoCodeRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getUsedCount(), "new promo must start with usedCount 0");
    }

    @Test
    void createPromoCodeDefaultsToActiveAndNullsHandled() {
        PromoCodeAdminCreateRequest request = createRequest();
        request.setStatus(null);
        request.setMaximumDiscount(null);
        request.setUsageLimit(null);
        when(promoCodeRepository.findByCode("COLLEGE20")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PromoCodeAdminResponse response = adminPromoService.createPromoCode(request);

        assertEquals("ACTIVE", response.getStatus());
        assertNull(response.getMaximumDiscount());
        assertNull(response.getUsageLimit());
    }

    @Test
    void createPromoCodeDuplicateRejected() {
        when(promoCodeRepository.findByCode("COLLEGE20")).thenReturn(Optional.of(savedPromo()));

        assertThrows(DuplicateResourceException.class,
                () -> adminPromoService.createPromoCode(createRequest()));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void createPromoCodeInvalidDiscountTypeRejected() {
        PromoCodeAdminCreateRequest request = createRequest();
        request.setDiscountType("FLAT");

        assertThrows(InvalidRequestException.class,
                () -> adminPromoService.createPromoCode(request));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void createPromoCodePercentageOver100Rejected() {
        PromoCodeAdminCreateRequest request = createRequest();
        request.setDiscountValue(new BigDecimal("100.01"));

        assertThrows(InvalidRequestException.class,
                () -> adminPromoService.createPromoCode(request));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void createPromoCodeNonPositiveMaximumDiscountRejected() {
        PromoCodeAdminCreateRequest request = createRequest();
        request.setMaximumDiscount(BigDecimal.ZERO);

        assertThrows(InvalidRequestException.class,
                () -> adminPromoService.createPromoCode(request));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void createPromoCodeInvalidDateRangeRejected() {
        PromoCodeAdminCreateRequest request = createRequest();
        request.setExpiryDate(START.minusDays(1));

        assertThrows(InvalidRequestException.class,
                () -> adminPromoService.createPromoCode(request));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void createPromoCodeInvalidStatusRejected() {
        PromoCodeAdminCreateRequest request = createRequest();
        request.setStatus("DELETED");

        assertThrows(InvalidRequestException.class,
                () -> adminPromoService.createPromoCode(request));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void updatePromoCodeStatusSuccess() {
        when(promoCodeRepository.findByCode("COLLEGE20")).thenReturn(Optional.of(savedPromo()));
        when(promoCodeRepository.save(any(PromoCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PromoCodeAdminStatusRequest request = new PromoCodeAdminStatusRequest();
        request.setStatus("INACTIVE");

        PromoCodeAdminResponse response =
                adminPromoService.updatePromoCodeStatus("college20", request);

        assertEquals("INACTIVE", response.getStatus());
    }

    @Test
    void updatePromoCodeStatusSameStatusRejected() {
        when(promoCodeRepository.findByCode("COLLEGE20")).thenReturn(Optional.of(savedPromo()));

        PromoCodeAdminStatusRequest request = new PromoCodeAdminStatusRequest();
        request.setStatus("ACTIVE");

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> adminPromoService.updatePromoCodeStatus("COLLEGE20", request));
        assertTrue(exception.getMessage().contains("already ACTIVE"));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void updatePromoCodeStatusInvalidStatusRejected() {
        when(promoCodeRepository.findByCode("COLLEGE20")).thenReturn(Optional.of(savedPromo()));

        PromoCodeAdminStatusRequest request = new PromoCodeAdminStatusRequest();
        request.setStatus("EXPIRED");

        assertThrows(InvalidRequestException.class,
                () -> adminPromoService.updatePromoCodeStatus("COLLEGE20", request));
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void updatePromoCodeStatusUnknownCodeNotFound() {
        when(promoCodeRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        PromoCodeAdminStatusRequest request = new PromoCodeAdminStatusRequest();
        request.setStatus("INACTIVE");

        assertThrows(ResourceNotFoundException.class,
                () -> adminPromoService.updatePromoCodeStatus("MISSING", request));
    }
}
