package com.vehicle.rental.domain;

public class Customer extends User {

    private String licenseNumber;
    private int rentalCount;

    public Customer(String fullName, String email, String phone, String licenseNumber) {
        super(fullName, email, phone);
        this.licenseNumber = licenseNumber;
        this.rentalCount = 0;
    }

    @Override
    public String getRoleDescription() {
        return "Customer - can rent vehicles";
    }

    public boolean canRent() {
        return licenseNumber != null && !licenseNumber.isEmpty();
    }

    public void incrementRentalCount() {
        this.rentalCount++;
    }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public int getRentalCount() { return rentalCount; }
}
