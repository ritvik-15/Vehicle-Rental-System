package com.vehicle.rental.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Simulated payment response. Contains the result of a simulated demo payment.
 * Clearly marked as DEMO to distinguish from real payments.
 */
public class PaymentSimulateResponse {

    private Long paymentId;
    private Long rentalId;
    private BigDecimal amount;
    private String paymentStatus;
    private String rentalStatus;
    private String demoReference;
    private LocalDateTime paymentDate;
    private String message;

    public PaymentSimulateResponse() {
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getRentalId() { return rentalId; }
    public void setRentalId(Long rentalId) { this.rentalId = rentalId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getRentalStatus() { return rentalStatus; }
    public void setRentalStatus(String rentalStatus) { this.rentalStatus = rentalStatus; }
    public String getDemoReference() { return demoReference; }
    public void setDemoReference(String demoReference) { this.demoReference = demoReference; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}