-- Vehicle Rental Management System - Database Schema
-- Database: vehicle_rental_db

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    pincode VARCHAR(10),
    role ENUM('CUSTOMER','ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. Vehicles Table
CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_number VARCHAR(30) NOT NULL UNIQUE,
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(100) NOT NULL,
    vehicle_type ENUM('CAR','BIKE','BUS','TRUCK') NOT NULL,
    description TEXT,
    daily_rate DECIMAL(10,2) NOT NULL,
    seating_capacity INT NOT NULL,
    fuel_type ENUM('PETROL','DIESEL','ELECTRIC','CNG','HYBRID') NOT NULL,
    transmission ENUM('MANUAL','AUTOMATIC','NOT_APPLICABLE') DEFAULT 'NOT_APPLICABLE',
    location VARCHAR(150) NOT NULL,
    status ENUM('AVAILABLE','RENTED','MAINTENANCE','INACTIVE') NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_daily_rate CHECK (daily_rate >= 0),
    CONSTRAINT chk_seating_capacity CHECK (seating_capacity > 0)
);

-- 3. Vehicle Images Table
CREATE TABLE IF NOT EXISTS vehicle_images (
    image_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_images_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

-- 4. Promo Codes Table
CREATE TABLE IF NOT EXISTS promo_codes (
    promo_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_type ENUM('PERCENTAGE','FIXED') NOT NULL,
    discount_value DECIMAL(10,2) NOT NULL,
    minimum_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    maximum_discount DECIMAL(10,2),
    start_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    usage_limit INT,
    used_count INT NOT NULL DEFAULT 0,
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_discount_value CHECK (discount_value >= 0),
    CONSTRAINT chk_minimum_amount CHECK (minimum_amount >= 0),
    CONSTRAINT chk_usage_limit CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT chk_expiry_date CHECK (expiry_date >= start_date)
);

-- 5. Rentals Table
CREATE TABLE IF NOT EXISTS rentals (
    rental_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    promo_id BIGINT NULL,
    pickup_datetime DATETIME NOT NULL,
    return_datetime DATETIME NOT NULL,
    actual_return_datetime DATETIME NULL,
    number_of_days INT NOT NULL,
    daily_rate DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    security_deposit DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL,
    rental_status ENUM('PENDING','CONFIRMED','ACTIVE','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rentals_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_rentals_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_rentals_promo FOREIGN KEY (promo_id) REFERENCES promo_codes(promo_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_return_datetime CHECK (return_datetime > pickup_datetime),
    CONSTRAINT chk_number_of_days CHECK (number_of_days > 0),
    CONSTRAINT chk_rental_daily_rate CHECK (daily_rate >= 0),
    CONSTRAINT chk_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_discount_amount CHECK (discount_amount >= 0),
    CONSTRAINT chk_tax_amount CHECK (tax_amount >= 0),
    CONSTRAINT chk_security_deposit CHECK (security_deposit >= 0),
    CONSTRAINT chk_total_amount CHECK (total_amount >= 0)
);

-- 6. Payments Table
CREATE TABLE IF NOT EXISTS payments (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rental_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method ENUM('RAZORPAY') NOT NULL DEFAULT 'RAZORPAY',
    razorpay_order_id VARCHAR(100) UNIQUE,
    razorpay_payment_id VARCHAR(100) UNIQUE,
    payment_status ENUM('PENDING','SUCCESS','FAILED','CANCELLED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    payment_date DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_rental FOREIGN KEY (rental_id) REFERENCES rentals(rental_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
);

-- 7. Maintenance Table
CREATE TABLE IF NOT EXISTS maintenance (
    maintenance_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    maintenance_type VARCHAR(100) NOT NULL,
    description TEXT,
    maintenance_date DATE NOT NULL,
    completion_date DATE NULL,
    cost DECIMAL(10,2) NOT NULL DEFAULT 0,
    status ENUM('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    service_provider VARCHAR(150),
    notes TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_maintenance_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_maintenance_cost CHECK (cost >= 0),
    CONSTRAINT chk_completion_date CHECK (completion_date IS NULL OR completion_date >= maintenance_date)
);

-- Indexes
-- Created idempotently: each statement creates its index only when it
-- does not already exist, so schema.sql can safely be re-run against an
-- existing database without dropping, truncating, or altering any
-- existing table, index, or record.
SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'vehicles' AND index_name = 'idx_vehicle_type'), 'CREATE INDEX idx_vehicle_type ON vehicles(vehicle_type)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'vehicles' AND index_name = 'idx_vehicle_status'), 'CREATE INDEX idx_vehicle_status ON vehicles(status)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'vehicles' AND index_name = 'idx_vehicle_location'), 'CREATE INDEX idx_vehicle_location ON vehicles(location)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'rentals' AND index_name = 'idx_rental_user'), 'CREATE INDEX idx_rental_user ON rentals(user_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'rentals' AND index_name = 'idx_rental_vehicle'), 'CREATE INDEX idx_rental_vehicle ON rentals(vehicle_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'rentals' AND index_name = 'idx_rental_status'), 'CREATE INDEX idx_rental_status ON rentals(rental_status)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'rentals' AND index_name = 'idx_rental_pickup'), 'CREATE INDEX idx_rental_pickup ON rentals(pickup_datetime)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'rentals' AND index_name = 'idx_rental_return'), 'CREATE INDEX idx_rental_return ON rentals(return_datetime)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payments' AND index_name = 'idx_payment_rental'), 'CREATE INDEX idx_payment_rental ON payments(rental_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payments' AND index_name = 'idx_payment_user'), 'CREATE INDEX idx_payment_user ON payments(user_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'maintenance' AND index_name = 'idx_maintenance_vehicle'), 'CREATE INDEX idx_maintenance_vehicle ON maintenance(vehicle_id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'maintenance' AND index_name = 'idx_maintenance_status'), 'CREATE INDEX idx_maintenance_status ON maintenance(status)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
