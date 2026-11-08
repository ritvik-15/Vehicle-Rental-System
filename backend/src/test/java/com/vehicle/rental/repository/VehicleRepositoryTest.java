package com.vehicle.rental.repository;

import com.vehicle.rental.dto.VehicleImageRequest;
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
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class VehicleRepositoryTest {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleImageRepository vehicleImageRepository;

    @Autowired
    private VehicleService vehicleService;

    private Vehicle saveVehicle(String registrationNumber, VehicleStatus status,
                                BigDecimal dailyRate, VehicleType type,
                                String location, FuelType fuelType,
                                TransmissionType transmission) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber(registrationNumber);
        vehicle.setBrand("Brand " + registrationNumber);
        vehicle.setModel("Model " + registrationNumber);
        vehicle.setVehicleType(type);
        vehicle.setDailyRate(dailyRate);
        vehicle.setSeatingCapacity(4);
        vehicle.setFuelType(fuelType);
        vehicle.setTransmission(transmission);
        vehicle.setLocation(location);
        vehicle.setStatus(status);
        return vehicleRepository.save(vehicle);
    }

    private int indexOfRegistration(List<Vehicle> vehicles, String registrationNumber) {
        for (int i = 0; i < vehicles.size(); i++) {
            if (registrationNumber.equals(vehicles.get(i).getRegistrationNumber())) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void publicSearchExcludesInactiveVehicles() {
        saveVehicle("PVTINACT1", VehicleStatus.INACTIVE, new BigDecimal("100.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);
        saveVehicle("PVTACTV01", VehicleStatus.AVAILABLE, new BigDecimal("200.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);

        List<Vehicle> result = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, null, null, null, null, null, null, Sort.unsorted());

        assertTrue(result.stream().anyMatch(v -> "PVTACTV01".equals(v.getRegistrationNumber())));
        assertTrue(result.stream().noneMatch(v -> "PVTINACT1".equals(v.getRegistrationNumber())));
    }

    @Test
    void searchMatchesBrandModelAndLocation() {
        saveVehicle("PVTSRCH01", VehicleStatus.AVAILABLE, new BigDecimal("100.00"),
                VehicleType.CAR, "Mumbai", FuelType.PETROL, TransmissionType.MANUAL);
        saveVehicle("PVTSRCH02", VehicleStatus.AVAILABLE, new BigDecimal("200.00"),
                VehicleType.BIKE, "Delhi", FuelType.DIESEL, TransmissionType.AUTOMATIC);

        List<Vehicle> byBrand = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, "Brand PVTSRCH01", null, null, null, null, null, Sort.unsorted());
        assertTrue(byBrand.stream().anyMatch(v -> "PVTSRCH01".equals(v.getRegistrationNumber())));
        assertTrue(byBrand.stream().noneMatch(v -> "PVTSRCH02".equals(v.getRegistrationNumber())));

        List<Vehicle> byModel = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, "Model PVTSRCH02", null, null, null, null, null, Sort.unsorted());
        assertTrue(byModel.stream().anyMatch(v -> "PVTSRCH02".equals(v.getRegistrationNumber())));
        assertTrue(byModel.stream().noneMatch(v -> "PVTSRCH01".equals(v.getRegistrationNumber())));

        List<Vehicle> byLocation = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, "Mumbai", null, null, null, null, null, Sort.unsorted());
        assertTrue(byLocation.stream().anyMatch(v -> "PVTSRCH01".equals(v.getRegistrationNumber())));
        assertTrue(byLocation.stream().noneMatch(v -> "PVTSRCH02".equals(v.getRegistrationNumber())));
    }

    @Test
    void filtersByTypeFuelTransmissionAndLocation() {
        saveVehicle("PVTFILT01", VehicleStatus.AVAILABLE, new BigDecimal("100.00"),
                VehicleType.CAR, "Pune", FuelType.ELECTRIC, TransmissionType.AUTOMATIC);
        saveVehicle("PVTFILT02", VehicleStatus.AVAILABLE, new BigDecimal("200.00"),
                VehicleType.BUS, "Pune", FuelType.PETROL, TransmissionType.MANUAL);

        List<Vehicle> byType = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, null, VehicleType.BUS, null, null, null, null, Sort.unsorted());
        assertTrue(byType.stream().anyMatch(v -> "PVTFILT02".equals(v.getRegistrationNumber())));
        assertTrue(byType.stream().noneMatch(v -> "PVTFILT01".equals(v.getRegistrationNumber())));

        List<Vehicle> byFuel = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, null, null, null, FuelType.ELECTRIC, null, null, Sort.unsorted());
        assertTrue(byFuel.stream().anyMatch(v -> "PVTFILT01".equals(v.getRegistrationNumber())));
        assertTrue(byFuel.stream().noneMatch(v -> "PVTFILT02".equals(v.getRegistrationNumber())));

        List<Vehicle> byTransmission = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, null, null, null, null, TransmissionType.AUTOMATIC, null, Sort.unsorted());
        assertTrue(byTransmission.stream().anyMatch(v -> "PVTFILT01".equals(v.getRegistrationNumber())));
        assertTrue(byTransmission.stream().noneMatch(v -> "PVTFILT02".equals(v.getRegistrationNumber())));

        List<Vehicle> byLocation = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, null, null, "Pune", null, null, null, Sort.unsorted());
        assertTrue(byLocation.stream().anyMatch(v -> "PVTFILT01".equals(v.getRegistrationNumber())));
        assertTrue(byLocation.stream().anyMatch(v -> "PVTFILT02".equals(v.getRegistrationNumber())));
    }

    @Test
    void sortingByDailyRateAscendingAndDescending() {
        saveVehicle("PVTSORT1", VehicleStatus.AVAILABLE, new BigDecimal("300.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);
        saveVehicle("PVTSORT2", VehicleStatus.AVAILABLE, new BigDecimal("100.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);
        saveVehicle("PVTSORT3", VehicleStatus.AVAILABLE, new BigDecimal("200.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);

        List<Vehicle> ascending = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, null, null, null, null, null, null,
                Sort.by(Sort.Direction.ASC, "dailyRate"));
        assertTrue(indexOfRegistration(ascending, "PVTSORT2")
                < indexOfRegistration(ascending, "PVTSORT3"));
        assertTrue(indexOfRegistration(ascending, "PVTSORT3")
                < indexOfRegistration(ascending, "PVTSORT1"));

        List<Vehicle> descending = vehicleRepository.searchVehicles(
                VehicleStatus.INACTIVE, null, null, null, null, null, null,
                Sort.by(Sort.Direction.DESC, "dailyRate"));
        assertTrue(indexOfRegistration(descending, "PVTSORT1")
                < indexOfRegistration(descending, "PVTSORT3"));
        assertTrue(indexOfRegistration(descending, "PVTSORT3")
                < indexOfRegistration(descending, "PVTSORT2"));
    }

    @Test
    void servicePublicListingExcludesInactive() {
        saveVehicle("PVTSVCL1", VehicleStatus.INACTIVE, new BigDecimal("100.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);
        saveVehicle("PVTSVCA01", VehicleStatus.AVAILABLE, new BigDecimal("200.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);

        List<VehicleResponse> listing = vehicleService.listVehicles(
                null, null, null, null, null, null, "createdAt", "desc");

        assertTrue(listing.stream().anyMatch(r -> "PVTSVCA01".equals(r.getRegistrationNumber())));
        assertTrue(listing.stream().noneMatch(r -> "PVTSVCL1".equals(r.getRegistrationNumber())));
    }

    @Test
    void vehicleDetailsIncludeImages() {
        Vehicle vehicle = saveVehicle("PVTDETL1", VehicleStatus.AVAILABLE, new BigDecimal("100.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);

        VehicleImageRequest imageRequest = new VehicleImageRequest();
        imageRequest.setImageUrl("https://example.com/detail.jpg");
        imageRequest.setIsPrimary(true);
        vehicleService.addVehicleImage(vehicle.getVehicleId(), imageRequest);

        VehicleResponse details = vehicleService.getVehicleDetails(vehicle.getVehicleId());

        assertNotNull(details.getImages());
        assertEquals(1, details.getImages().size());
        assertEquals("https://example.com/detail.jpg", details.getImages().get(0).getImageUrl());
        assertTrue(details.getImages().get(0).getIsPrimary());
    }

    @Test
    void inactiveVehicleDetailsAreHidden() {
        Vehicle vehicle = saveVehicle("PVTHIDD1", VehicleStatus.INACTIVE, new BigDecimal("100.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);

        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.getVehicleDetails(vehicle.getVehicleId()));
    }

    @Test
    void onlyOnePrimaryImagePerVehicle() {
        Vehicle vehicle = saveVehicle("PVTPRIM1", VehicleStatus.AVAILABLE, new BigDecimal("100.00"),
                VehicleType.CAR, "Pune", FuelType.PETROL, TransmissionType.MANUAL);

        VehicleImageRequest first = new VehicleImageRequest();
        first.setImageUrl("https://example.com/first.jpg");
        first.setIsPrimary(true);
        vehicleService.addVehicleImage(vehicle.getVehicleId(), first);

        VehicleImageRequest second = new VehicleImageRequest();
        second.setImageUrl("https://example.com/second.jpg");
        second.setIsPrimary(true);
        vehicleService.addVehicleImage(vehicle.getVehicleId(), second);

        List<VehicleImage> primaries =
                vehicleImageRepository.findByVehicleVehicleIdAndIsPrimaryTrue(vehicle.getVehicleId());

        assertEquals(1, primaries.size());
        assertEquals("https://example.com/second.jpg", primaries.get(0).getImageUrl());
    }
}
