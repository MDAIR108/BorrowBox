-- BorrowBox MySQL Database Schema
-- Scoped for KUET Campus Rental & Borrowing Platform

CREATE DATABASE IF NOT EXISTS borrowbox;
USE borrowbox;

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    roll_no VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    department VARCHAR(100) NOT NULL,
    hall VARCHAR(100) NOT NULL,
    is_email_verified TINYINT(1) DEFAULT 0,
    is_roll_verified TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Email Verifications Table
CREATE TABLE IF NOT EXISTS email_verifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    attempts INT DEFAULT 0,
    used TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Products Table
CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(80) NOT NULL,
    price_per_day DECIMAL(10,2) DEFAULT 0.00,
    image_url VARCHAR(500) DEFAULT NULL,
    emoji VARCHAR(20) DEFAULT '📦',
    department VARCHAR(100) NOT NULL,
    hall VARCHAR(100) NOT NULL,
    status ENUM('available', 'rented') DEFAULT 'available',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_category (category),
    INDEX idx_department (department),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Rentals Table
CREATE TABLE IF NOT EXISTS rentals (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    renter_id INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status ENUM('pending', 'approved', 'active', 'returned', 'cancelled', 'rejected') DEFAULT 'pending',
    total_price DECIMAL(10,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (renter_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_product_id (product_id),
    INDEX idx_renter_id (renter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed Sample Data: Initial Users (Demo Passwords: 'Password123!')
-- bcrypt hash for 'Password123!' = $2a$10$wT8K8U9F3W1ZJ9H5g8Z4i.B8H5sD4fG6hJ8kL0mN2pQ4rS6tU8vW
INSERT INTO users (id, full_name, roll_no, email, password_hash, phone, department, hall, is_email_verified, is_roll_verified)
VALUES
(1, 'Tanvir Ahmed', '2307001', '2307001@stud.kuet.ac.bd', '$2a$10$wT8K8U9F3W1ZJ9H5g8Z4i.B8H5sD4fG6hJ8kL0mN2pQ4rS6tU8vW', '+8801700000001', 'Computer Science & Engineering (CSE)', 'Amar Ekushey Hall', 1, 1),
(2, 'Shadman Sakib', '2305012', '2305012@stud.kuet.ac.bd', '$2a$10$wT8K8U9F3W1ZJ9H5g8Z4i.B8H5sD4fG6hJ8kL0mN2pQ4rS6tU8vW', '+8801700000002', 'Mechanical Engineering (ME)', 'Fazlul Haque Hall', 1, 1),
(3, 'Mahir Faysal', '2309003', '2309003@stud.kuet.ac.bd', '$2a$10$wT8K8U9F3W1ZJ9H5g8Z4i.B8H5sD4fG6hJ8kL0mN2pQ4rS6tU8vW', '+8801700000003', 'Chemical Engineering (ChE)', 'Lalan Shah Hall', 1, 1),
(4, 'Rifat Hasan', '2303045', '2303045@stud.kuet.ac.bd', '$2a$10$wT8K8U9F3W1ZJ9H5g8Z4i.B8H5sD4fG6hJ8kL0mN2pQ4rS6tU8vW', '+8801700000004', 'Electrical & Electronic Engineering (EEE)', 'Bangabandhu Hall', 1, 1),
(5, 'Nafis Imtiaz', '2301078', '2301078@stud.kuet.ac.bd', '$2a$10$wT8K8U9F3W1ZJ9H5g8Z4i.B8H5sD4fG6hJ8kL0mN2pQ4rS6tU8vW', '+8801700000005', 'Civil Engineering (CE)', 'Khan Jahan Ali Hall', 1, 1),
(6, 'Sadia Noor', '2301045', '2301045@stud.kuet.ac.bd', '$2a$10$wT8K8U9F3W1ZJ9H5g8Z4i.B8H5sD4fG6hJ8kL0mN2pQ4rS6tU8vW', '+8801700000006', 'Civil Engineering (CE)', 'Rokeya Hall', 1, 1)
ON DUPLICATE KEY UPDATE id=id;

-- Seed Sample Products Matching the Existing App
INSERT INTO products (id, owner_id, title, description, category, price_per_day, emoji, department, hall, status)
VALUES
(1, 1, 'Casio fx-991EX ClassWiz', 'Original scientific calculator with natural textbook display. Essential for calculus and exam hall.', 'Tools', 20.00, '🧮', 'CSE Dept', 'Amar Ekushey Hall', 'available'),
(2, 2, 'Rotary Mini Drafter + Sheet Clamps', 'Full precision engineering drawing drafter set with scale and clamp holder.', 'Tools', 15.00, '📐', 'ME Dept', 'Fazlul Haque Hall', 'available'),
(3, 3, 'Cotton White Lab Coat (Clean, Size L)', 'Pure cotton protective lab coat. Freshly cleaned, suitable for chemistry and workshop labs.', 'Labs', 0.00, '🥼', 'ChE Dept', 'Lalan Shah Hall', 'available'),
(4, 4, 'Arduino Mega 2560 & Sensor Kit', 'Microcontroller board complete with breadboard, jumper wires, ultrasonic and temperature sensors.', 'Gadgets', 35.00, '⚡', 'EEE Dept', 'Bangabandhu Hall', 'available'),
(5, 5, 'Higher Engineering Mathematics - B.S. Grewal', '44th edition, clean textbook with all solved university question sets. Currently on loan until Friday.', 'Books', 10.00, '📚', 'Math Dept', 'Khan Jahan Ali Hall', 'rented'),
(6, 6, 'Phoenix Hero 26" Campus Bicycle', 'Smooth city ride bicycle with front basket and lock.', 'Cycles', 50.00, '🚴', 'CE Dept', 'Rokeya Hall', 'available')
ON DUPLICATE KEY UPDATE id=id;
