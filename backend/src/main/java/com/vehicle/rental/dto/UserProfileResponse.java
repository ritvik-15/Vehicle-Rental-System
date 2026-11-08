package com.vehicle.rental.dto;

import java.time.LocalDateTime;

/**
 * Phase 11A own-profile view for GET/PUT /api/users/me. Built only from the
 * authenticated principal's own record — deliberately has no password or
 * credential field of any kind, so hash/secret leakage is structurally
 * impossible.
 */
public class UserProfileResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String role;
    private String status;
    private LocalDateTime createdAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
