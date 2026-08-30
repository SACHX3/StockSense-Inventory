-- StockSense repair for:
-- EntityNotFoundException: Unable to find Product with id ...
--
-- Run once in phpMyAdmin against smart_inventory_db, then restart StockSense.

USE smart_inventory_db;

-- Show broken references before cleanup.
SELECT il.id, il.product_id
FROM inventory_logs il
LEFT JOIN products p ON p.id = il.product_id
WHERE p.id IS NULL;

SELECT si.id, si.sale_id, si.product_id
FROM sales_items si
LEFT JOIN products p ON p.id = si.product_id
WHERE p.id IS NULL;

-- Remove only rows whose referenced product no longer exists.
DELETE il
FROM inventory_logs il
LEFT JOIN products p ON p.id = il.product_id
WHERE p.id IS NULL;

DELETE si
FROM sales_items si
LEFT JOIN products p ON p.id = si.product_id
WHERE p.id IS NULL;

-- Verify that no broken product references remain.
SELECT COUNT(*) AS orphan_inventory_logs
FROM inventory_logs il
LEFT JOIN products p ON p.id = il.product_id
WHERE p.id IS NULL;

SELECT COUNT(*) AS orphan_sale_items
FROM sales_items si
LEFT JOIN products p ON p.id = si.product_id
WHERE p.id IS NULL;
