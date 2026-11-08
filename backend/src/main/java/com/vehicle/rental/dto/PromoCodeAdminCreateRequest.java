package com.vehicle.rental.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * R2: admin request for creating a promo code. Only governs
 * management input — booking-time promo validation, row locking,
 * usage consumption and release-on-cancellation/expiry remain
 * entirely in PromoService and RentalService.
 */
public class PromoCodeAdminCreateRequest {

    @NotBlank(message = "Promo code is required")
    @Size(max = 50, message = "Promo code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Discount type is required")
    private String discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be greater than 0.00")
    private BigDecimal discountValue;

    @NotNull(message = "Minimum amount is required")
    @DecimalMin(value = "0.00", message = "Minimum amount must be 0.00 or greater")
    private BigDecimal minimumAmount;

    @DecimalMin(value = "0.00", message = "Maximum discount must be 0.00 or greater")
    private BigDecimal maximumDiscount;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Expiry date is required")
    private LocalDate expiryDate;

    @Min(value = 1, message = "Usage limit must be at least 1")
    private Integer usageLimit;

    private String status;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMinimumAmount() { return minimumAmount; }
    public void setMinimumAmount(BigDecimal minimumAmount) { this.minimumAmount = minimumAmount; }
    public BigDecimal getMaximumDiscount() { return maximumDiscount; }
    public void setMaximumDiscount(BigDecimal maximumDiscount) { this.maximumDiscount = maximumDiscount; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
