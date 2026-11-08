package com.vehicle.rental.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testCustomerCreation() {
        Customer customer = new Customer("John Doe", "john@example.com", "9876543210", "DL12345");
        assertEquals("John Doe", customer.getFullName());
        assertEquals("john@example.com", customer.getEmail());
        assertEquals("9876543210", customer.getPhone());
        assertEquals("DL12345", customer.getLicenseNumber());
        assertEquals(0, customer.getRentalCount());
    }

    @Test
    void testCustomerCanRent() {
        Customer customer = new Customer("John Doe", "john@example.com", "9876543210", "DL12345");
        assertTrue(customer.canRent());
    }

    @Test
    void testCustomerCannotRentWithoutLicense() {
        Customer customer = new Customer("John Doe", "john@example.com", "9876543210", "");
        assertFalse(customer.canRent());
    }

    @Test
    void testCustomerRentalCount() {
        Customer customer = new Customer("John Doe", "john@example.com", "9876543210", "DL12345");
        customer.incrementRentalCount();
        customer.incrementRentalCount();
        assertEquals(2, customer.getRentalCount());
    }

    @Test
    void testAdminCreation() {
        Admin admin = new Admin("Admin User", "admin@example.com", "9876543210", 2, "IT");
        assertEquals("Admin User", admin.getFullName());
        assertEquals("admin@example.com", admin.getEmail());
        assertEquals(2, admin.getAdminLevel());
        assertEquals("IT", admin.getDepartment());
    }

    @Test
    void testAdminPermissions() {
        Admin superAdmin = new Admin("Super Admin", "admin@example.com", "9876543210", 2, "IT");
        assertTrue(superAdmin.canManageVehicles());
        assertTrue(superAdmin.canManageUsers());

        Admin basicAdmin = new Admin("Basic Admin", "admin@example.com", "9876543210", 1, "Support");
        assertTrue(basicAdmin.canManageVehicles());
        assertFalse(basicAdmin.canManageUsers());
    }

    @Test
    void testPolymorphismThroughUserAbstraction() {
        User customer = new Customer("John Doe", "john@example.com", "9876543210", "DL12345");
        User admin = new Admin("Admin User", "admin@example.com", "9876543210", 2, "IT");

        assertEquals("Customer - can rent vehicles", customer.getRoleDescription());
        assertEquals("Admin - can manage system", admin.getRoleDescription());
    }

    @Test
    void testCommonUserBehavior() {
        User customer = new Customer("John Doe", "john@example.com", "9876543210", "DL12345");
        User admin = new Admin("Admin User", "admin@example.com", "9876543210", 2, "IT");

        assertEquals("John Doe", customer.getDisplayName());
        assertEquals("Admin User", admin.getDisplayName());
        assertEquals("john@example.com | 9876543210", customer.getContactInfo());
        assertEquals("admin@example.com | 9876543210", admin.getContactInfo());
    }
}
