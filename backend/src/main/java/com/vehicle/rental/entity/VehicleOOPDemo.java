
package com.vehicle.rental.entity;


interface Rentable {
    void showDetails();
    double calculateRent(int days);
}

abstract class RentalVehicle implements Rentable {
    private String brand;
    private String model;
    private double pricePerDay;

  
    RentalVehicle(String brand, String model, double pricePerDay) {
        this.brand = brand;
        this.model = model;
        this.pricePerDay = pricePerDay;
    }

  
    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public double getPricePerDay() {
        return pricePerDay;
    }

    public void showDetails() {
        System.out.println("Brand: " + brand);
        System.out.println("Model: " + model);
        System.out.println("Price per day: Rs. " + pricePerDay);
    }
}


class Car extends RentalVehicle {

    Car(String brand, String model, double price) {
        super(brand, model, price);
    }

    @Override
    public double calculateRent(int days) {
        return getPricePerDay() * days;
    }

    @Override
    public void showDetails() {
        System.out.println("\n--- Car Details ---");
        super.showDetails();
    }
}


class Bike extends RentalVehicle {

    Bike(String brand, String model, double price) {
        super(brand, model, price);
    }

    @Override
    public double calculateRent(int days) {
        return getPricePerDay() * days;
    }

    @Override
    public void showDetails() {
        System.out.println("\n--- Bike Details ---");
        super.showDetails();
    }
}


public class VehicleOOPDemo {
    public static void main(String[] args) {

        // Runtime polymorphism
        Rentable v1 = new Car("Toyota", "Innova", 2500);
        Rentable v2 = new Bike("Honda", "Shine", 500);

        v1.showDetails();
        System.out.println("Total Rent: Rs. " + v1.calculateRent(3));

        v2.showDetails();
        System.out.println("Total Rent: Rs. " + v2.calculateRent(2));
    }
}
