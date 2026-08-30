-- ============================================================
-- SMART INVENTORY - LOGIN FIX SQL
-- Run this in phpMyAdmin on smart_inventory_db
-- All users password: admin123
--
-- NOTE: You probably don't need this anymore. DataInitializer.java now
-- re-encodes and resets the "admin" account's password every time the
-- Spring Boot app starts, so a broken admin login self-heals automatically.
-- Keep this script only as a manual fallback / for resetting manager or
-- staff1 if their hashes ever get corrupted directly in the database.
-- ============================================================

USE smart_inventory_db;

-- Step 1: Make sure roles exist
INSERT IGNORE INTO roles (id, name, description) VALUES
(1, 'ROLE_ADMIN', 'Full system access - Admin'),
(2, 'ROLE_INVENTORY_MANAGER', 'Manage inventory, products, suppliers'),
(3, 'ROLE_STAFF', 'View and process sales only');

-- Step 2: DELETE old users with wrong hashes and re-insert correctly
DELETE FROM users WHERE username IN ('admin', 'manager', 'staff1');

-- Step 3: Insert users with CORRECT BCrypt hashes for password "admin123"
-- These hashes are verified to work with Spring Security BCryptPasswordEncoder
INSERT INTO users (username, email, password, full_name, phone, role_id, is_active) VALUES
('admin',   'admin@stocksense.com',   '$2a$10$lr0HplxNH7OgdJWsmanBJuG5gCRtxBTqZKYJXvfFVidC4R/v0IvfS', 'System Administrator', '+94 77 123 4567', 1, TRUE),
('manager', 'manager@stocksense.com', '$2a$10$fDVGWnyquRlSqxugsEFtI.2/F0FsmoD0cFnhsfaaCgblVhXzmS.RW', 'Inventory Manager',    '+94 77 234 5678', 2, TRUE),
('staff1',  'staff@stocksense.com',   '$2a$10$MUyVzb9hKuEmPBEnd9f1xOaibiYcpCLYpO0CbeOcHg4qr5gD4myG2', 'Sales Staff',          '+94 77 345 6789', 3, TRUE);

-- Step 4: Verify (should show 3 users)
SELECT id, username, email, role_id, is_active,
       SUBSTRING(password, 1, 20) as hash_preview
FROM users
WHERE username IN ('admin','manager','staff1');

-- ============================================================
-- Login Credentials:
-- Username: admin    Password: admin123  (Full Admin Access)
-- Username: manager  Password: admin123  (Inventory Manager)
-- Username: staff1   Password: admin123  (Staff/Sales Only)
-- ============================================================
