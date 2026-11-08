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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * R2: admin-only promo code management on the existing PromoCode
 * entity, repository and promo_codes table — no schema changes.
 * Booking-time eligibility validation, pessimistic locking, usage
 * consumption and release-on-cancellation/expiry are unchanged and
 * remain solely in PromoService and RentalService; nothing here
 * duplicates or replaces them.
 */
@Service
public class AdminPromoService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final PromoCodeRepository promoCodeRepository;

    public AdminPromoService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    @Transactional(readOnly = true)
    public List<PromoCodeAdminResponse> getAllPromoCodes() {
        return promoCodeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PromoCodeAdminResponse createPromoCode(PromoCodeAdminCreateRequest request) {
        String code = request.getCode().trim().toUpperCase();
        DiscountType discountType = parseDiscountType(request.getDiscountType());
        BigDecimal discountValue = request.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        BigDecimal minimumAmount = request.getMinimumAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal maximumDiscount = request.getMaximumDiscount() != null
                ? request.getMaximumDiscount().setScale(2, RoundingMode.HALF_UP)
                : null;
        LocalDate startDate = request.getStartDate();
        LocalDate expiryDate = request.getExpiryDate();
        String status = parseStatus(request.getStatus());

        if (discountType == DiscountType.PERCENTAGE
                && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new InvalidRequestException("Percentage discount value must be 100 or less");
        }
        if (maximumDiscount != null && maximumDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Maximum discount must be greater than 0.00");
        }
        if (!expiryDate.isAfter(startDate)) {
            throw new InvalidRequestException("Expiry date must be after start date");
        }
        if (promoCodeRepository.findByCode(code).isPresent()) {
            throw new DuplicateResourceException("Promo code already exists: " + code);
        }

        PromoCode promo = new PromoCode();
        promo.setCode(code);
        promo.setDiscountType(discountType);
        promo.setDiscountValue(discountValue);
        promo.setMinimumAmount(minimumAmount);
        promo.setMaximumDiscount(maximumDiscount);
        promo.setStartDate(startDate);
        promo.setExpiryDate(expiryDate);
        promo.setUsageLimit(request.getUsageLimit());
        promo.setUsedCount(0);
        promo.setStatus(status);
        return toResponse(promoCodeRepository.save(promo));
    }

    @Transactional
    public PromoCodeAdminResponse updatePromoCodeStatus(String code,
                                                        PromoCodeAdminStatusRequest request) {
        PromoCode promo = promoCodeRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Promo code not found"));

        String newStatus = parseStatus(request.getStatus());
        if (newStatus.equals(promo.getStatus())) {
            throw new InvalidRequestException("Promo code status is already " + promo.getStatus());
        }

        promo.setStatus(newStatus);
        return toResponse(promoCodeRepository.save(promo));
    }

    private DiscountType parseDiscountType(String value) {
        try {
            return DiscountType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid discount type");
        }
    }

    private String parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return STATUS_ACTIVE;
        }
        String status = value.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(status) && !STATUS_INACTIVE.equals(status)) {
            throw new InvalidRequestException("Promo code status must be ACTIVE or INACTIVE");
        }
        return status;
    }

    private PromoCodeAdminResponse toResponse(PromoCode promo) {
        PromoCodeAdminResponse response = new PromoCodeAdminResponse();
        response.setCode(promo.getCode());
        response.setDiscountType(promo.getDiscountType().name());
        response.setDiscountValue(promo.getDiscountValue());
        response.setMinimumAmount(promo.getMinimumAmount());
        response.setMaximumDiscount(promo.getMaximumDiscount());
        response.setStartDate(promo.getStartDate());
        response.setExpiryDate(promo.getExpiryDate());
        response.setUsageLimit(promo.getUsageLimit());
        response.setUsedCount(promo.getUsedCount());
        response.setStatus(promo.getStatus());
        response.setCreatedAt(promo.getCreatedAt());
        return response;
    }
}
