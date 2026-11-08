package com.vehicle.rental.controller;

import com.vehicle.rental.dto.CustomerResponse;
import com.vehicle.rental.dto.CustomerStatusRequest;
import com.vehicle.rental.service.CustomerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9 admin customer management. Secured by the existing
 * /api/admin/** -> hasRole("ADMIN") rule in SecurityConfig; the service layer
 * further restricts every operation to CUSTOMER accounts only.
 */
@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {

    private final CustomerService customerService;

    public AdminCustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerResponse> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/{id}")
    public CustomerResponse getCustomerDetails(@PathVariable Long id) {
        return customerService.getCustomerDetails(id);
    }

    @PatchMapping("/{id}/status")
    public CustomerResponse updateCustomerStatus(@PathVariable Long id,
                                                 @Valid @RequestBody CustomerStatusRequest request) {
        return customerService.updateCustomerStatus(id, request);
    }
}
