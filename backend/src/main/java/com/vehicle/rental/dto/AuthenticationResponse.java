package com.vehicle.rental.dto;

public class AuthenticationResponse {

    private String token;
    private String type = "Bearer";
    private Long userId;
    private String fullName;
    private String email;
    private String role;

    public AuthenticationResponse(String token, Long userId, String fullName, String email, String role) {
        this.token = token;
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getToken() { return token; }
    public String getType() { return type; }
    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
}
