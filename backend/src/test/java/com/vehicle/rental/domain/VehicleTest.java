package com.vehicle.rental.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class VehicleTest {

    @Test
    void testCarCreation() {
        Car car = new Car("Toyota", "Camry", new BigDecimal("50.00"), 5, 4, true, 450.0);
        assertEquals("Toyota", car.getBrand());
        assertEquals("Camry", car.getModel());
        assertEquals(new BigDecimal("50.00"), car.getDailyRate());
        assertEquals(5, car.getSeatingCapacity());
        assertEquals(4, car.getNumberOfDoors());
        assertTrue(car.hasAC());
        assertEquals(450.0, car.getTrunkCapacity());
    }

    @Test
    void testBikeCreation() {
        Bike bike = new Bike("Honda", "CBR", new BigDecimal("20.00"), 2, 250, false, 2);
        assertEquals("Honda", bike.getBrand());
        assertEquals("CBR", bike.getModel());
        assertEquals(250, bike.getEngineCC());
        assertFalse(bike.hasCarrier());
        assertEquals(2, bike.getHelmetCount());
    }

    @Test
    void testBusCreation() {
        Bus bus = new Bus("Volvo", "9400", new BigDecimal("150.00"), 40, true, 500.0);
        assertEquals("Volvo", bus.getBrand());
        assertEquals("9400", bus.getModel());
        assertEquals(40, bus.getSeatingCapacity());
        assertTrue(bus.hasAC());
        assertEquals(500.0, bus.getLuggageSpace());
    }

    @Test
    void testTruckCreation() {
        Truck truck = new Truck("Tata", "Prima", new BigDecimal("200.00"), 3, 15.0, 4, "General");
        assertEquals("Tata", truck.getBrand());
        assertEquals("Prima", truck.getModel());
        assertEquals(15.0, truck.getLoadCapacity());
        assertEquals(4, truck.getNumberOfAxles());
        assertEquals("General", truck.getCargoType());
    }

    @Test
    void testPolymorphismThroughVehicleAbstraction() {
        Vehicle car = new Car("Toyota", "Camry", new BigDecimal("50.00"), 5, 4, true, 450.0);
        Vehicle bike = new Bike("Honda", "CBR", new BigDecimal("20.00"), 2, 250, false, 2);
        Vehicle bus = new Bus("Volvo", "9400", new BigDecimal("150.00"), 40, true, 500.0);
        Vehicle truck = new Truck("Tata", "Prima", new BigDecimal("200.00"), 3, 15.0, 4, "General");

        assertEquals("Car", car.getVehicleCategory());
        assertEquals("Bike", bike.getVehicleCategory());
        assertEquals("Bus", bus.getVehicleCategory());
        assertEquals("Truck", truck.getVehicleCategory());
    }

    @Test
    void testPolymorphicRentalPolicy() {
        Vehicle car = new Car("Toyota", "Camry", new BigDecimal("50.00"), 5, 4, true, 450.0);
        Vehicle bike = new Bike("Honda", "CBR", new BigDecimal("20.00"), 2, 250, false, 2);
        Vehicle bus = new Bus("Volvo", "9400", new BigDecimal("150.00"), 40, true, 500.0);
        Vehicle truck = new Truck("Tata", "Prima", new BigDecimal("200.00"), 3, 15.0, 4, "General");

        assertTrue(car.getRentalPolicy().contains("Standard car rental"));
        assertTrue(bike.getRentalPolicy().contains("Bike rental"));
        assertTrue(bus.getRentalPolicy().contains("Bus rental"));
        assertTrue(truck.getRentalPolicy().contains("Truck rental"));
    }

    @Test
    void testCalculateRentalCost() {
        Vehicle car = new Car("Toyota", "Camry", new BigDecimal("50.00"), 5, 4, true, 450.0);
        assertEquals(new BigDecimal("150.00"), car.calculateRentalCost(3));

        Vehicle bike = new Bike("Honda", "CBR", new BigDecimal("20.00"), 2, 250, false, 2);
        assertEquals(new BigDecimal("60.00"), bike.calculateRentalCost(3));
    }
}
