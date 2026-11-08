package com.vehicle.rental.domain;

public class Admin extends User {

    private int adminLevel;
    private String department;

    public Admin(String fullName, String email, String phone, int adminLevel, String department) {
        super(fullName, email, phone);
        this.adminLevel = adminLevel;
        this.department = department;
    }

    @Override
    public String getRoleDescription() {
        return "Admin - can manage system";
    }

    public boolean canManageVehicles() {
        return adminLevel >= 1;
    }

    public boolean canManageUsers() {
        return adminLevel >= 2;
    }

    public int getAdminLevel() { return adminLevel; }
    public void setAdminLevel(int adminLevel) { this.adminLevel = adminLevel; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}
