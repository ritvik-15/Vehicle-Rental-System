package com.vehicle.rental.service;

import com.vehicle.rental.dto.PromoValidateRequest;
import com.vehicle.rental.dto.PromoValidationResponse;
import com.vehicle.rental.entity.PromoCode;
import com.vehicle.rental.enums.DiscountType;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class PromoService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PromoCodeRepository promoCodeRepository;

    public PromoService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    public PromoValidationResponse validatePromo(PromoValidateRequest request) {
        PromoCode promo = promoCodeRepository.findByCode(request.getCode().trim())
                .orElseThrow(() -> new InvalidRequestException("Promo code not found"));
        validateEligibility(promo, request.getRentalAmount());
        BigDecimal discount = calculateDiscount(promo, request.getRentalAmount());
        return toResponse(promo, request.getRentalAmount(), discount);
    }

    /**
     * Shared eligibility checks used by the validation API and the
     * rental booking transaction. Throws InvalidRequestException (400)
     * for any ineligible promo.
     */
    public void validateEligibility(PromoCode promo, BigDecimal rentalAmount) {
        if (!"ACTIVE".equals(promo.getStatus())) {
            throw new InvalidRequestException("Promo code is not active");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(promo.getStartDate())) {
            throw new InvalidRequestException("Promo code is not yet valid");
        }
        if (today.isAfter(promo.getExpiryDate())) {
            throw new InvalidRequestException("Promo code has expired");
        }
        if (rentalAmount.compareTo(promo.getMinimumAmount()) < 0) {
            throw new InvalidRequestException(
                    "Rental amount does not meet the minimum amount required for this promo code");
        }
        if (promo.getUsageLimit() != null && promo.getUsedCount() >= promo.getUsageLimit()) {
            throw new InvalidRequestException("Promo code usage limit has been reached");
        }
    }

    /**
     * Shared server-side discount calculation. The client never
     * supplies the discount amount.
     */
    public BigDecimal calculateDiscount(PromoCode promo, BigDecimal rentalAmount) {
        BigDecimal discount;
        if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = rentalAmount.multiply(promo.getDiscountValue())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            if (promo.getMaximumDiscount() != null
                    && discount.compareTo(promo.getMaximumDiscount()) > 0) {
                discount = promo.getMaximumDiscount();
            }
        } else {
            discount = promo.getDiscountValue();
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(rentalAmount) > 0) {
            discount = rentalAmount;
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private PromoValidationResponse toResponse(PromoCode promo, BigDecimal rentalAmount, BigDecimal discount) {
        PromoValidationResponse response = new PromoValidationResponse();
        response.setCode(promo.getCode());
        response.setDiscountType(promo.getDiscountType().name());
        response.setDiscountValue(promo.getDiscountValue());
        response.setMinimumAmount(promo.getMinimumAmount());
        response.setMaximumDiscount(promo.getMaximumDiscount());
        response.setStartDate(promo.getStartDate());
        response.setExpiryDate(promo.getExpiryDate());
        response.setUsageLimit(promo.getUsageLimit());
        response.setUsedCount(promo.getUsedCount());
        response.setRentalAmount(rentalAmount);
        response.setDiscountAmount(discount);
        response.setFinalAmount(rentalAmount.subtract(discount));
        response.setMessage("Promo code applied successfully");
        return response;
    }
}
