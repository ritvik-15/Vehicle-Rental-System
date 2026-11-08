package com.vehicle.rental.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vehicle.rental.dto.VehicleResponse;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.entity.VehicleImage;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11C real-MySQL integration for the admin vehicle read APIs: every
 * status visible (incl. INACTIVE), createdAt DESC + vehicleId ASC tie-break,
 * detail with images for INACTIVE, plus the unchanged public regressions
 * (list excludes INACTIVE, detail 404s for INACTIVE). Class-level
 * @Transactional rolls everything back — no leftover data.
 */
@SpringBootTest
@Transactional
class AdminVehicleIntegrationTest {

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleImageRepository vehicleImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Vehicle fixture(VehicleStatus status, String brand) {
        String unique = String.format("%08d", Math.abs(System.nanoTime() % 100000000L));
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("11C" + unique + status.name().charAt(0));
        vehicle.setBrand(brand);
        vehicle.setModel("Model " + unique);
        vehicle.setVehicleType(VehicleType.CAR);
        vehicle.setDailyRate(new BigDecimal("1500.00"));
        vehicle.setSeatingCapacity(5);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setTransmission(TransmissionType.AUTOMATIC);
        vehicle.setLocation("Mumbai");
        vehicle.setStatus(status);
        return vehicleRepository.saveAndFlush(vehicle);
    }

    @Test
    void adminListIncludesEveryStatusIncludingInactive() {
        Vehicle available = fixture(VehicleStatus.AVAILABLE, "Alpha");
        Vehicle rented = fixture(VehicleStatus.RENTED, "Beta");
        Vehicle maintenance = fixture(VehicleStatus.MAINTENANCE, "Gamma");
        Vehicle inactive = fixture(VehicleStatus.INACTIVE, "Delta");

        List<VehicleResponse> list = vehicleService.getAllVehiclesForAdmin();
        var byId = list.stream()
                .collect(Collectors.toMap(VehicleResponse::getVehicleId, r -> r));

        assertEquals(VehicleStatus.AVAILABLE.name(), byId.get(available.getVehicleId()).getStatus());
        assertEquals(VehicleStatus.RENTED.name(), byId.get(rented.getVehicleId()).getStatus());
        assertEquals(VehicleStatus.MAINTENANCE.name(), byId.get(maintenance.getVehicleId()).getStatus());
        assertEquals(VehicleStatus.INACTIVE.name(), byId.get(inactive.getVehicleId()).getStatus());
        assertTrue(list.size() >= 4, "list must contain every fixture");
    }

    @Test
    void publicListStillExcludesInactiveVehicles() {
        Vehicle inactive = fixture(VehicleStatus.INACTIVE, "Hidden");
        Vehicle visible = fixture(VehicleStatus.AVAILABLE, "Visible");

        List<Long> publicIds = vehicleService
                .listVehicles(null, null, null, null, null, null, "createdAt", "desc")
                .stream()
                .map(VehicleResponse::getVehicleId)
                .collect(Collectors.toList());

        assertFalse(publicIds.contains(inactive.getVehicleId()),
                "public list must still hide INACTIVE");
        assertTrue(publicIds.contains(visible.getVehicleId()));
    }

    @Test
    void adminListOrdersByCreatedAtDescThenVehicleIdAscTieBreak() {
        Vehicle older = fixture(VehicleStatus.AVAILABLE, "Older");
        Vehicle tie1 = fixture(VehicleStatus.AVAILABLE, "TieFirst");
        Vehicle tie2 = fixture(VehicleStatus.AVAILABLE, "TieSecond");

        // Force: older has an earlier createdAt; tie1/tie2 share one timestamp.
        jdbcTemplate.update("UPDATE vehicles SET created_at = '2026-01-01 10:00:00' WHERE vehicle_id = ?",
                older.getVehicleId());
        jdbcTemplate.update("UPDATE vehicles SET created_at = '2026-06-01 10:00:00' WHERE vehicle_id IN (?, ?)",
                tie1.getVehicleId(), tie2.getVehicleId());

        List<Long> orderedIds = vehicleService.getAllVehiclesForAdmin().stream()
                .map(VehicleResponse::getVehicleId)
                .collect(Collectors.toList());

        int tie1Index = orderedIds.indexOf(tie1.getVehicleId());
        int tie2Index = orderedIds.indexOf(tie2.getVehicleId());
        int olderIndex = orderedIds.indexOf(older.getVehicleId());

        assertTrue(tie1Index >= 0 && tie2Index >= 0 && olderIndex >= 0, "all fixtures listed");
        assertEquals(tie1Index + 1, tie2Index, "equal createdAt must tie-break by vehicleId ASC");
        assertTrue(olderIndex > tie2Index, "earlier createdAt must sort after newer (DESC)");
    }

    @Test
    void adminDetailReturnsInactiveVehicleWithPopulatedImages() {
        Vehicle inactive = fixture(VehicleStatus.INACTIVE, "InactiveDetail");
        VehicleImage first = new VehicleImage();
        first.setVehicle(inactive);
        first.setImageUrl("https://example.com/one.jpg");
        first.setIsPrimary(true);
        vehicleImageRepository.saveAndFlush(first);
        VehicleImage second = new VehicleImage();
        second.setVehicle(inactive);
        second.setImageUrl("https://example.com/two.jpg");
        second.setIsPrimary(false);
        vehicleImageRepository.saveAndFlush(second);

        VehicleResponse response = vehicleService.getVehicleDetailsForAdmin(inactive.getVehicleId());

        assertEquals(inactive.getVehicleId(), response.getVehicleId());
        assertEquals(VehicleStatus.INACTIVE.name(), response.getStatus());
        assertEquals("InactiveDetail", response.getBrand());
        assertNotNull(response.getImages());
        assertEquals(2, response.getImages().size());
        assertTrue(response.getImages().stream()
                .anyMatch(img -> "https://example.com/one.jpg".equals(img.getImageUrl())));
    }

    @Test
    void adminDetailMissingVehicleThrowsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.getVehicleDetailsForAdmin(999999L));
    }

    @Test
    void publicDetailStillHidesInactiveVehicle() {
        Vehicle inactive = fixture(VehicleStatus.INACTIVE, "StillHidden");

        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.getVehicleDetails(inactive.getVehicleId()));
    }

    @Test
    void adminVehicleResponsesCarryNoCredentialFields() throws Exception {
        fixture(VehicleStatus.INACTIVE, "SafeList");
        Vehicle detail = fixture(VehicleStatus.AVAILABLE, "SafeDetail");

        String listJson = objectMapper.writeValueAsString(vehicleService.getAllVehiclesForAdmin());
        String detailJson = objectMapper.writeValueAsString(
                vehicleService.getVehicleDetailsForAdmin(detail.getVehicleId()));

        for (String json : List.of(listJson, detailJson)) {
            String lower = json.toLowerCase();
            assertFalse(lower.contains("password"), "response must not contain password: " + json);
            assertFalse(lower.contains("hash"), "response must not contain hash: " + json);
            assertFalse(lower.contains("secret"), "response must not contain secret: " + json);
            assertFalse(lower.contains("token"), "response must not contain token: " + json);
        }
    }
}
