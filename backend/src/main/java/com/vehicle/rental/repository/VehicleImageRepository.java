package com.vehicle.rental.repository;

import com.vehicle.rental.entity.VehicleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {

    List<VehicleImage> findByVehicleVehicleIdOrderByCreatedAtAsc(Long vehicleId);

    List<VehicleImage> findByVehicleVehicleIdAndIsPrimaryTrue(Long vehicleId);

    Optional<VehicleImage> findByVehicleVehicleIdAndImageId(Long vehicleId, Long imageId);
}
