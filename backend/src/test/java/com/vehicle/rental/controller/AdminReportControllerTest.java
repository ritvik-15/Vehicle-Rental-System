package com.vehicle.rental.controller;

import com.vehicle.rental.dto.MaintenanceReportResponse;
import com.vehicle.rental.dto.PopularVehicleResponse;
import com.vehicle.rental.dto.RentalReportResponse;
import com.vehicle.rental.dto.RevenueReportResponse;
import com.vehicle.rental.dto.SummaryReportResponse;
import com.vehicle.rental.dto.VehicleReportResponse;
import com.vehicle.rental.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10: all five report endpoints are ADMIN-only (401/403 via the
 * existing /api/admin/** security rule) and serialize the fixed response
 * shapes — every approved metric key is always present.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    // ------------------------------------------------------------------
    // 401 — unauthenticated
    // ------------------------------------------------------------------

    @Test
    void summaryUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revenueUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/reports/revenue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rentalsUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/reports/rentals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void vehiclesUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/reports/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void maintenanceUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/reports/maintenance"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 403 — authenticated but not ADMIN
    // ------------------------------------------------------------------

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void summaryCustomerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void revenueCustomerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reports/revenue"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void rentalsCustomerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reports/rentals"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void vehiclesCustomerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reports/vehicles"))
                .andExpect(status().isForbidden());
    }

    @WithMockUser(roles = "CUSTOMER")
    @Test
    void maintenanceCustomerRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reports/maintenance"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // 200 — ADMIN, fixed shape with every approved metric key
    // ------------------------------------------------------------------

    @WithMockUser(roles = "ADMIN")
    @Test
    void summaryAdminReturnsOkWithAllMetricKeys() throws Exception {
        SummaryReportResponse response = new SummaryReportResponse();
        response.setTotalCustomers(12);
        response.setTotalVehicles(5);
        response.setTotalRentals(9);
        response.setTotalPayments(7);
        response.setTotalRevenue(new BigDecimal("15000.00"));
        response.setTotalMaintenanceCost(new BigDecimal("1250.50"));
        when(reportService.getSummaryReport()).thenReturn(response);

        mockMvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").value(12))
                .andExpect(jsonPath("$.totalVehicles").value(5))
                .andExpect(jsonPath("$.totalRentals").value(9))
                .andExpect(jsonPath("$.totalPayments").value(7))
                .andExpect(jsonPath("$.totalRevenue").value(15000.00))
                .andExpect(jsonPath("$.totalMaintenanceCost").value(1250.50));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void revenueAdminReturnsOkWithAllPaymentStatusCounts() throws Exception {
        RevenueReportResponse response = new RevenueReportResponse();
        response.setTotalRevenue(new BigDecimal("8000.00"));
        response.setSuccessCount(4);
        response.setPendingCount(2);
        response.setFailedCount(1);
        response.setCancelledCount(1);
        response.setRefundedCount(0);
        response.setTotalTransactions(8);
        when(reportService.getRevenueReport()).thenReturn(response);

        mockMvc.perform(get("/api/admin/reports/revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(8000.00))
                .andExpect(jsonPath("$.successCount").value(4))
                .andExpect(jsonPath("$.pendingCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.refundedCount").value(0))
                .andExpect(jsonPath("$.totalTransactions").value(8));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void rentalsAdminReturnsOkWithEveryRentalStatusKey() throws Exception {
        RentalReportResponse response = new RentalReportResponse();
        response.setTotalRentals(10);
        response.setPendingCount(1);
        response.setConfirmedCount(2);
        response.setActiveCount(3);
        response.setCompletedCount(2);
        response.setCancelledCount(2);
        when(reportService.getRentalsReport()).thenReturn(response);

        mockMvc.perform(get("/api/admin/reports/rentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRentals").value(10))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.activeCount").value(3))
                .andExpect(jsonPath("$.completedCount").value(2))
                .andExpect(jsonPath("$.cancelledCount").value(2));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void vehiclesAdminReturnsOkWithStatusCountsAndPopularList() throws Exception {
        PopularVehicleResponse popular = new PopularVehicleResponse();
        popular.setVehicleId(3L);
        popular.setBrand("Toyota");
        popular.setModel("Etios");
        popular.setVehicleType("CAR");
        popular.setRentalCount(6);
        VehicleReportResponse response = new VehicleReportResponse();
        response.setTotalVehicles(4);
        response.setAvailableCount(1);
        response.setRentedCount(1);
        response.setMaintenanceCount(1);
        response.setInactiveCount(1);
        response.setPopularVehicles(List.of(popular));
        when(reportService.getVehiclesReport()).thenReturn(response);

        mockMvc.perform(get("/api/admin/reports/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(4))
                .andExpect(jsonPath("$.availableCount").value(1))
                .andExpect(jsonPath("$.rentedCount").value(1))
                .andExpect(jsonPath("$.maintenanceCount").value(1))
                .andExpect(jsonPath("$.inactiveCount").value(1))
                .andExpect(jsonPath("$.popularVehicles.length()").value(1))
                .andExpect(jsonPath("$.popularVehicles[0].vehicleId").value(3))
                .andExpect(jsonPath("$.popularVehicles[0].brand").value("Toyota"))
                .andExpect(jsonPath("$.popularVehicles[0].model").value("Etios"))
                .andExpect(jsonPath("$.popularVehicles[0].vehicleType").value("CAR"))
                .andExpect(jsonPath("$.popularVehicles[0].rentalCount").value(6));
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void vehiclesAdminEmptyPopularListSerializesAsEmptyArray() throws Exception {
        when(reportService.getVehiclesReport()).thenReturn(new VehicleReportResponse());

        mockMvc.perform(get("/api/admin/reports/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(0))
                .andExpect(jsonPath("$.availableCount").value(0))
                .andExpect(jsonPath("$.rentedCount").value(0))
                .andExpect(jsonPath("$.maintenanceCount").value(0))
                .andExpect(jsonPath("$.inactiveCount").value(0))
                .andExpect(jsonPath("$.popularVehicles").isArray())
                .andExpect(jsonPath("$.popularVehicles").isEmpty());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void maintenanceAdminReturnsOkWithTotalsAndAverage() throws Exception {
        MaintenanceReportResponse response = new MaintenanceReportResponse();
        response.setTotalRecords(6);
        response.setScheduledCount(2);
        response.setInProgressCount(1);
        response.setCompletedCount(2);
        response.setCancelledCount(1);
        response.setTotalCost(new BigDecimal("3600.00"));
        response.setAverageCost(new BigDecimal("600.00"));
        when(reportService.getMaintenanceReport()).thenReturn(response);

        mockMvc.perform(get("/api/admin/reports/maintenance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(6))
                .andExpect(jsonPath("$.scheduledCount").value(2))
                .andExpect(jsonPath("$.inProgressCount").value(1))
                .andExpect(jsonPath("$.completedCount").value(2))
                .andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.totalCost").value(3600.00))
                .andExpect(jsonPath("$.averageCost").value(600.00));
    }
}
