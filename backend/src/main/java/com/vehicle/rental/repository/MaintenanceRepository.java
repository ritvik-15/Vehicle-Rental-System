package com.vehicle.rental.repository;

import com.vehicle.rental.entity.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

    @Query("SELECT COUNT(m) FROM Maintenance m WHERE m.vehicle.vehicleId = :vehicleId"
            + " AND m.status IN ('SCHEDULED', 'IN_PROGRESS')"
            + " AND (m.completionDate IS NOT NULL AND :pickupDate <= m.completionDate"
            + " OR m.completionDate IS NULL AND m.status = 'IN_PROGRESS'"
            + " OR m.completionDate IS NULL AND m.status = 'SCHEDULED' AND :pickupDate <= m.maintenanceDate)"
            + " AND :returnDate >= m.maintenanceDate")
    long countConflictingMaintenance(@Param("vehicleId") Long vehicleId,
                                      @Param("pickupDate") LocalDate pickupDate,
                                      @Param("returnDate") LocalDate returnDate);

    /**
     * Phase 10 maintenance report: per-status count and cost sum; totals and
     * average are derived from these rows (zero-cost records included).
     */
    @Query("SELECT m.status, COUNT(m), SUM(m.cost) FROM Maintenance m GROUP BY m.status")
    List<Object[]> findMaintenanceStatusCountsAndSums();
}
