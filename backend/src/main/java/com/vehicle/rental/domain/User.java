package com.vehicle.rental.domain;

public abstract class User {

    private String fullName;
    private String email;
    private String phone;

    public User(String fullName, String email, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }

    public abstract String getRoleDescription();

    public String getDisplayName() {
        return fullName;
    }

    public String getContactInfo() {
        return email + " | " + phone;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
