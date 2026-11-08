package com.vehicle.rental.service;

import com.vehicle.rental.dto.MaintenanceCreateRequest;
import com.vehicle.rental.dto.MaintenanceResponse;
import com.vehicle.rental.dto.MaintenanceStatusRequest;
import com.vehicle.rental.dto.MaintenanceUpdateRequest;
import com.vehicle.rental.entity.Maintenance;
import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.MaintenanceStatus;
import com.vehicle.rental.exception.InvalidRequestException;
import com.vehicle.rental.exception.ResourceNotFoundException;
import com.vehicle.rental.repository.MaintenanceRepository;
import com.vehicle.rental.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock
    private MaintenanceRepository maintenanceRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private MaintenanceService maintenanceService;

    private static final LocalDate MAINTENANCE_DATE = LocalDate.now().plusDays(5);
    private static final LocalDate COMPLETION_DATE = LocalDate.now().plusDays(6);

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(1L);
        vehicle.setRegistrationNumber("MH05AB1234");
        vehicle.setBrand("Hyundai");
        vehicle.setModel("i20");
        return vehicle;
    }

    private MaintenanceCreateRequest createRequest() {
        MaintenanceCreateRequest request = new MaintenanceCreateRequest();
        request.setVehicleId(1L);
        request.setMaintenanceType("Routine Service");
        request.setDescription("Oil change");
        request.setMaintenanceDate(MAINTENANCE_DATE);
        request.setCompletionDate(COMPLETION_DATE);
        request.setCost(new BigDecimal("500.00"));
        request.setServiceProvider("Authorized Service Center");
        request.setNotes("Test notes");
        return request;
    }

    private MaintenanceUpdateRequest updateRequest() {
        MaintenanceUpdateRequest request = new MaintenanceUpdateRequest();
        request.setVehicleId(1L);
        request.setMaintenanceType("Major Service");
        request.setDescription("Full service");
        request.setMaintenanceDate(MAINTENANCE_DATE);
        request.setCompletionDate(COMPLETION_DATE);
        request.setCost(new BigDecimal("1500.00"));
        request.setServiceProvider("Authorized Service Center");
        request.setNotes("Updated notes");
        return request;
    }

    private Maintenance buildMaintenance(MaintenanceStatus status) {
        Maintenance maintenance = new Maintenance();
        maintenance.setMaintenanceId(10L);
        maintenance.setVehicle(vehicle());
        maintenance.setMaintenanceType("Routine Service");
        maintenance.setDescription("Oil change");
        maintenance.setMaintenanceDate(MAINTENANCE_DATE);
        maintenance.setCompletionDate(COMPLETION_DATE);
        maintenance.setCost(new BigDecimal("500.00"));
        maintenance.setStatus(status);
        maintenance.setServiceProvider("Authorized Service Center");
        maintenance.setNotes("Test notes");
        return maintenance;
    }

    @AfterEach
    void tearDown() {
        // no security context is used by MaintenanceService
    }

    @Test
    void createMaintenanceSuccess() {
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle()));
        when(maintenanceRepository.save(any(Maintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceResponse response = maintenanceService.createMaintenance(createRequest());

        assertEquals("SCHEDULED", response.getStatus());
        assertEquals(1L, response.getVehicleId());
        assertEquals("MH05AB1234", response.getVehicleRegistrationNumber());
        assertEquals("Hyundai", response.getVehicleBrand());
        assertEquals("i20", response.getVehicleModel());
        assertEquals("Routine Service", response.getMaintenanceType());
        assertEquals("Oil change", response.getDescription());
        assertEquals(MAINTENANCE_DATE, response.getMaintenanceDate());
        assertEquals(COMPLETION_DATE, response.getCompletionDate());
        assertEquals(new BigDecimal("500.00"), response.getCost());
        assertEquals("Authorized Service Center", response.getServiceProvider());
        assertEquals("Test notes", response.getNotes());
    }

    @Test
    void createMaintenanceVehicleNotFound() {
        when(vehicleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> maintenanceService.createMaintenance(createRequest()));
    }

    @Test
    void createMaintenanceInvalidCompletionDate() {
        MaintenanceCreateRequest request = createRequest();
        request.setCompletionDate(MAINTENANCE_DATE.minusDays(1));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle()));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> maintenanceService.createMaintenance(request));
        assertTrue(exception.getMessage().contains("Completion date cannot be before maintenance date"));
    }

    @Test
    void updateMaintenanceSuccess() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle()));
        when(maintenanceRepository.save(any(Maintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceResponse response = maintenanceService.updateMaintenance(10L, updateRequest());

        assertEquals("Major Service", response.getMaintenanceType());
        assertEquals(new BigDecimal("1500.00"), response.getCost());
        assertEquals("Updated notes", response.getNotes());
    }

    @Test
    void updateMaintenanceNotFound() {
        when(maintenanceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> maintenanceService.updateMaintenance(999L, updateRequest()));
    }

    @Test
    void updateMaintenanceVehicleNotFound() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> maintenanceService.updateMaintenance(10L, updateRequest()));
    }

    @Test
    void updateMaintenanceInvalidCompletionDate() {
        MaintenanceUpdateRequest request = updateRequest();
        request.setCompletionDate(MAINTENANCE_DATE.minusDays(1));
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle()));

        assertThrows(InvalidRequestException.class,
                () -> maintenanceService.updateMaintenance(10L, request));
    }

    @Test
    void updateMaintenanceStatusSuccess() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));
        when(maintenanceRepository.save(any(Maintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("IN_PROGRESS");

        MaintenanceResponse response = maintenanceService.updateMaintenanceStatus(10L, request);

        assertEquals("IN_PROGRESS", response.getStatus());
    }

    @Test
    void updateMaintenanceStatusInvalidStatusRejected() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("BROKEN");

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> maintenanceService.updateMaintenanceStatus(10L, request));
        assertTrue(exception.getMessage().contains("Invalid maintenance status"));
    }

    @Test
    void updateMaintenanceStatusScheduledToCompleted() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));
        when(maintenanceRepository.save(any(Maintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("COMPLETED");

        MaintenanceResponse response = maintenanceService.updateMaintenanceStatus(10L, request);

        assertEquals("COMPLETED", response.getStatus());
    }

    @Test
    void updateMaintenanceStatusScheduledToCancelled() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));
        when(maintenanceRepository.save(any(Maintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("CANCELLED");

        MaintenanceResponse response = maintenanceService.updateMaintenanceStatus(10L, request);

        assertEquals("CANCELLED", response.getStatus());
    }

    @Test
    void updateMaintenanceStatusInProgressToCompleted() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.IN_PROGRESS)));
        when(maintenanceRepository.save(any(Maintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("COMPLETED");

        MaintenanceResponse response = maintenanceService.updateMaintenanceStatus(10L, request);

        assertEquals("COMPLETED", response.getStatus());
    }

    @Test
    void updateMaintenanceStatusBackToScheduledRejected() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.IN_PROGRESS)));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("SCHEDULED");

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> maintenanceService.updateMaintenanceStatus(10L, request));
        assertTrue(exception.getMessage().contains("Invalid maintenance status transition from IN_PROGRESS to SCHEDULED"));
        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceStatusFromCompletedRejected() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.COMPLETED)));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("SCHEDULED");

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> maintenanceService.updateMaintenanceStatus(10L, request));
        assertTrue(exception.getMessage().contains("Invalid maintenance status transition from COMPLETED to SCHEDULED"));
        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceStatusFromCancelledRejected() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.CANCELLED)));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("IN_PROGRESS");

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> maintenanceService.updateMaintenanceStatus(10L, request));
        assertTrue(exception.getMessage().contains("Invalid maintenance status transition from CANCELLED to IN_PROGRESS"));
        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceStatusSameStatusRejected() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("SCHEDULED");

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> maintenanceService.updateMaintenanceStatus(10L, request));
        assertTrue(exception.getMessage().contains("Maintenance status is already SCHEDULED"));
        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceStatusNotFound() {
        when(maintenanceRepository.findById(999L)).thenReturn(Optional.empty());

        MaintenanceStatusRequest request = new MaintenanceStatusRequest();
        request.setStatus("COMPLETED");

        assertThrows(ResourceNotFoundException.class,
                () -> maintenanceService.updateMaintenanceStatus(999L, request));
    }

    @Test
    void getMaintenanceDetailsSuccess() {
        when(maintenanceRepository.findById(10L)).thenReturn(Optional.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));

        MaintenanceResponse response = maintenanceService.getMaintenanceDetails(10L);

        assertEquals(10L, response.getMaintenanceId());
        assertEquals("SCHEDULED", response.getStatus());
    }

    @Test
    void getMaintenanceDetailsNotFound() {
        when(maintenanceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> maintenanceService.getMaintenanceDetails(999L));
    }

    @Test
    void getAllMaintenanceReturnsList() {
        when(maintenanceRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(buildMaintenance(MaintenanceStatus.SCHEDULED)));

        List<MaintenanceResponse> list = maintenanceService.getAllMaintenance();

        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).getMaintenanceId());
        assertEquals("SCHEDULED", list.get(0).getStatus());
    }
}
