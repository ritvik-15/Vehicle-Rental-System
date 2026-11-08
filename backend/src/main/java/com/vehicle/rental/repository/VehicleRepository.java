package com.vehicle.rental.repository;

import com.vehicle.rental.entity.Vehicle;
import com.vehicle.rental.enums.FuelType;
import com.vehicle.rental.enums.TransmissionType;
import com.vehicle.rental.enums.VehicleStatus;
import com.vehicle.rental.enums.VehicleType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumberAndVehicleIdNot(String registrationNumber, Long vehicleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.vehicleId = :vehicleId")
    Optional<Vehicle> findByIdWithPessimisticLock(@Param("vehicleId") Long vehicleId);

    @Query("SELECT v FROM Vehicle v WHERE v.status <> :excludedStatus"
            + " AND (:search IS NULL OR :search = ''"
            + " OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :search, '%'))"
            + " OR LOWER(v.model) LIKE LOWER(CONCAT('%', :search, '%'))"
            + " OR LOWER(v.location) LIKE LOWER(CONCAT('%', :search, '%')))"
            + " AND (:vehicleType IS NULL OR v.vehicleType = :vehicleType)"
            + " AND (:location IS NULL OR :location = '' OR LOWER(v.location) = LOWER(:location))"
            + " AND (:fuelType IS NULL OR v.fuelType = :fuelType)"
            + " AND (:transmission IS NULL OR v.transmission = :transmission)"
            + " AND (:status IS NULL OR v.status = :status)")
    List<Vehicle> searchVehicles(@Param("excludedStatus") VehicleStatus excludedStatus,
                                 @Param("search") String search,
                                 @Param("vehicleType") VehicleType vehicleType,
                                 @Param("location") String location,
                                 @Param("fuelType") FuelType fuelType,
                                 @Param("transmission") TransmissionType transmission,
                                 @Param("status") VehicleStatus status,
                                 Sort sort);

    /** Phase 10 vehicles report: current stored-status snapshot counts. */
    @Query("SELECT v.status, COUNT(v) FROM Vehicle v GROUP BY v.status")
    List<Object[]> findVehicleStatusCounts();

    /**
     * Phase 10 popular vehicles: rentalCount over PENDING/CONFIRMED/ACTIVE/
     * COMPLETED rentals only (CANCELLED excluded, no date filtering). The
     * inner join drops vehicles with zero qualifying rentals; ordering is
     * rentalCount DESC then vehicleId ASC.
     */
    @Query("SELECT v.vehicleId, v.brand, v.model, v.vehicleType, COUNT(r)"
            + " FROM Vehicle v JOIN v.rentals r"
            + " WHERE r.rentalStatus IN ('PENDING', 'CONFIRMED', 'ACTIVE', 'COMPLETED')"
            + " GROUP BY v.vehicleId, v.brand, v.model, v.vehicleType"
            + " ORDER BY COUNT(r) DESC, v.vehicleId ASC")
    List<Object[]> findPopularVehicles();
}
