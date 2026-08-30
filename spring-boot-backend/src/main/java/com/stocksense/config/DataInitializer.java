package com.stocksense.config;

import com.stocksense.entity.*;
import com.stocksense.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository            roleRepository;
    private final UserRepository            userRepository;
    private final ProductCategoryRepository categoryRepository;
    private final SupplierRepository        supplierRepository;
    private final ProductRepository         productRepository;
    private final SaleRepository            saleRepository;
    private final SaleItemRepository        saleItemRepository;
    private final PasswordEncoder           passwordEncoder;
    private final JdbcTemplate              jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        repairOrphanedReferences();
        seedRoles();
        seedUsers();
        seedCategories();
        seedSuppliers();
        seedProducts();
        seedSampleSales();
        log.info("=================================================");
        log.info("  StockSense ready!");
        log.info("  Login: admin / admin123  (Administrator)");
        log.info("  Login: manager / admin123  (Inventory Manager)");
        log.info("  Login: staff1 / admin123  (Staff)");
        log.info("=================================================");
    }

    private void repairOrphanedReferences() {
        int inventoryRows = jdbcTemplate.update(
                // Use a subquery instead of MySQL's multi-table DELETE syntax.
                // The application test profile uses H2, which rejects
                // `DELETE alias FROM ... LEFT JOIN ...`; this form works on
                // both H2 and MySQL and also removes null foreign-key rows.
                "DELETE FROM inventory_logs " +
                "WHERE product_id IS NULL " +
                "OR product_id NOT IN (SELECT id FROM products)");
        int saleItemRows = jdbcTemplate.update(
                "DELETE FROM sales_items " +
                "WHERE product_id IS NULL " +
                "OR product_id NOT IN (SELECT id FROM products)");
        if (inventoryRows > 0 || saleItemRows > 0) {
            log.warn("Repaired orphaned database rows: inventory_logs={}, sales_items={}",
                    inventoryRows, saleItemRows);
        }
    }

    private void seedRoles() {
        createRole("ROLE_ADMIN",             "Full system access");
        createRole("ROLE_INVENTORY_MANAGER", "Manage inventory, products, suppliers");
        createRole("ROLE_STAFF",             "View and process sales only");
    }

    private void createRole(String name, String desc) {
        if (roleRepository.findByName(name).isEmpty()) {
            Role r = new Role(); r.setName(name); r.setDescription(desc);
            roleRepository.save(r);
        }
    }

    private void seedUsers() {
        Role adminRole   = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        Role managerRole = roleRepository.findByName("ROLE_INVENTORY_MANAGER").orElseThrow();
        Role staffRole   = roleRepository.findByName("ROLE_STAFF").orElseThrow();

        // Always reset admin password to ensure it works
        if (userRepository.existsByUsername("admin")) {
            userRepository.findByUsername("admin").ifPresent(u -> {
                u.setPassword(passwordEncoder.encode("admin123"));
                u.setIsActive(true); u.setRole(adminRole);
                userRepository.save(u);
            });
        } else {
            saveUser("admin",   "admin@stocksense.com",   "admin123",   "System Administrator", "+94 77 100 0001", adminRole);
        }

        if (!userRepository.existsByUsername("manager"))
            saveUser("manager", "manager@stocksense.com", "admin123", "Inventory Manager",    "+94 77 100 0002", managerRole);
        if (!userRepository.existsByUsername("staff1"))
            saveUser("staff1",  "staff@stocksense.com",   "admin123", "Sales Staff",          "+94 77 100 0003", staffRole);
    }

    private void saveUser(String username, String email, String password, String fullName, String phone, Role role) {
        User u = new User();
        u.setUsername(username); u.setEmail(email);
        u.setPassword(passwordEncoder.encode(password));
        u.setFullName(fullName); u.setPhone(phone);
        u.setRole(role); u.setIsActive(true);
        userRepository.save(u);
    }

    private void seedCategories() {
        String[][] cats = {
            {"Beverages",       "Drinks, juices, water, soft drinks"},
            {"Dairy Products",  "Milk, cheese, butter, yogurt"},
            {"Bakery",          "Bread, cakes, biscuits"},
            {"Snacks",          "Chips, crackers, candy"},
            {"Personal Care",   "Shampoo, soap, toothpaste"},
            {"Household",       "Cleaning products, detergents"},
            {"Grains & Cereals","Rice, flour, pasta, oats"},
            {"Condiments",      "Sauces, spices, oil"},
            {"Frozen Foods",    "Ice cream, frozen meals"},
            {"Electronics",     "Batteries, bulbs, cables"}
        };
        for (String[] c : cats) {
            if (!categoryRepository.existsByName(c[0])) {
                ProductCategory pc = new ProductCategory();
                pc.setName(c[0]); pc.setDescription(c[1]);
                categoryRepository.save(pc);
            }
        }
    }

    private void seedSuppliers() {
        if (supplierRepository.count() > 0) return;
        Object[][] suppliers = {
            {"Ceylon Beverages Ltd",  "Kamal Silva",   "kamal@ceylonbev.lk",   "+94 11 234 5678", "Colombo",  "NET 30"},
            {"Lanka Dairy Co",        "Nimal Perera",  "nimal@lankadairy.lk",  "+94 11 345 6789", "Kandy",    "NET 15"},
            {"Fresh Bakers PLC",      "Sunil Fernando","sunil@freshbakers.lk", "+94 11 456 7890", "Gampaha",  "IMMEDIATE"},
            {"Metro Distributors",    "Chamari W.",    "chamari@metro.lk",     "+94 11 567 8901", "Colombo",  "NET 45"},
            {"Island Spices Ltd",     "Aruna Bandara", "aruna@islandspices.lk","+94 11 678 9012", "Matara",   "NET 30"},
        };
        for (Object[] s : suppliers) {
            Supplier sup = new Supplier();
            sup.setName((String)s[0]); sup.setContactPerson((String)s[1]);
            sup.setEmail((String)s[2]); sup.setPhone((String)s[3]);
            sup.setCity((String)s[4]); sup.setPaymentTerms((String)s[5]);
            sup.setCountry("Sri Lanka"); sup.setIsActive(true);
            supplierRepository.save(sup);
        }
    }

    private void seedProducts() {
        if (productRepository.count() > 0) return;

        ProductCategory bev  = categoryRepository.findByIsActiveTrue().stream().filter(c -> c.getName().equals("Beverages")).findFirst().orElse(null);
        ProductCategory dai  = categoryRepository.findByIsActiveTrue().stream().filter(c -> c.getName().equals("Dairy Products")).findFirst().orElse(null);
        ProductCategory bak  = categoryRepository.findByIsActiveTrue().stream().filter(c -> c.getName().equals("Bakery")).findFirst().orElse(null);
        ProductCategory snk  = categoryRepository.findByIsActiveTrue().stream().filter(c -> c.getName().equals("Snacks")).findFirst().orElse(null);
        ProductCategory grn  = categoryRepository.findByIsActiveTrue().stream().filter(c -> c.getName().equals("Grains & Cereals")).findFirst().orElse(null);
        ProductCategory pc   = categoryRepository.findByIsActiveTrue().stream().filter(c -> c.getName().equals("Personal Care")).findFirst().orElse(null);
        ProductCategory cond = categoryRepository.findByIsActiveTrue().stream().filter(c -> c.getName().equals("Condiments")).findFirst().orElse(null);

        List<Supplier> sups = supplierRepository.findByIsActiveTrue();
        Supplier sup0 = sups.size() > 0 ? sups.get(0) : null;
        Supplier sup1 = sups.size() > 1 ? sups.get(1) : null;
        Supplier sup3 = sups.size() > 3 ? sups.get(3) : null;
        Supplier sup4 = sups.size() > 4 ? sups.get(4) : null;

        // name, sku, unit, buyPrice, sellPrice, qty, minStock, category, supplier
        Object[][] products = {
            {"Coca-Cola 330ml Can",    "BEV-001", "can",    55.00,  80.00,  240, 50,  bev,  sup0},
            {"Pepsi 500ml Bottle",     "BEV-002", "bottle", 65.00,  95.00,  180, 50,  bev,  sup0},
            {"Sprite 330ml Can",       "BEV-004", "can",    55.00,  80.00,  5,   50,  bev,  sup0},  // low stock
            {"Milo 400g Tin",          "BEV-003", "tin",   520.00, 650.00,  55,  15,  bev,  sup0},
            {"Anchor Milk 1L",         "DAI-001", "pack",  280.00, 340.00,  85,  20,  dai,  sup1},
            {"Butter 200g",            "DAI-002", "pack",  320.00, 390.00,  40,  10,  dai,  sup1},
            {"Kottu Bread 400g",       "BAK-001", "loaf",   85.00, 110.00,  60,  15,  bak,  null},
            {"Chocolate Biscuits 200g","BAK-002", "pack",  120.00, 160.00, 150,  30,  bak,  null},
            {"Lays Classic 100g",      "SNK-001", "pack",   95.00, 130.00, 200,  30,  snk,  sup3},
            {"Maggi Noodles 75g",      "GRN-002", "pack",   55.00,  75.00, 350,  50,  grn,  sup3},
            {"Basmati Rice 5kg",       "GRN-001", "bag",  1450.00,1750.00,  45,  10,  grn,  sup4},
            {"Atta Flour 1kg",         "GRN-003", "bag",   180.00, 220.00,  4,   20,  grn,  sup4},  // low stock
            {"Sunflower Oil 1L",       "CON-001", "bottle",380.00, 460.00,  90,  20,  cond, sup4},
            {"Sunlight Soap 90g",      "PC-001",  "bar",    55.00,  75.00, 300,  50,  pc,   sup3},
            {"Dettol Soap 75g",        "PC-002",  "bar",    65.00,  90.00, 250,  40,  pc,   sup3},
        };

        for (Object[] p : products) {
            if (!productRepository.existsBySku((String)p[1])) {
                Product prod = new Product();
                prod.setName((String)p[0]); prod.setSku((String)p[1]); prod.setUnit((String)p[2]);
                prod.setBuyingPrice(BigDecimal.valueOf((Double)p[3]));
                prod.setSellingPrice(BigDecimal.valueOf((Double)p[4]));
                prod.setQuantity((Integer)p[5]); prod.setMinStockLevel((Integer)p[6]);
                prod.setMaxStockLevel(1000);
                if (p[7] != null) prod.setCategory((ProductCategory)p[7]);
                else prod.setCategory(bev != null ? bev : categoryRepository.findByIsActiveTrue().get(0));
                if (p[8] != null) prod.setSupplier((Supplier)p[8]);
                prod.setIsActive(true);
                productRepository.save(prod);
            }
        }
        log.info("Seeded {} products", productRepository.count());
    }

    private void seedSampleSales() {
        // Only seed if no sales exist
        if (saleRepository.count() > 0) return;

        List<Product> prods = productRepository.findByIsActiveTrue();
        if (prods.isEmpty()) return;

        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) return;

        // Generate 90 days of history for EVERY active product, not just a handful -
        // AI Demand Forecasting needs real per-product daily sales to train on, and a
        // product with zero rows here will (correctly) forecast zero demand. A fixed
        // seed keeps the demo data reproducible across fresh installs.
        String[] customers = {"Walk-in Customer", "Nimal Stores", "City Mart", "Kamal Shop", "Local Retail", "Cargills Express"};
        java.util.Random rnd = new java.util.Random(42);
        LocalDateTime now = LocalDateTime.now();
        int invoiceSeq = 1;

        for (int daysAgo = 90; daysAgo >= 1; daysAgo--) {
            LocalDateTime saleTime = now.minusDays(daysAgo).withHour(9 + rnd.nextInt(10)).withMinute(rnd.nextInt(60));
            boolean isWeekend = saleTime.getDayOfWeek().getValue() >= 6; // Sat/Sun
            int salesToday = isWeekend ? 3 + rnd.nextInt(3) : 2 + rnd.nextInt(2); // more foot traffic on weekends

            for (int s = 0; s < salesToday; s++) {
                String invoiceNumber = String.format("INV-SEED-%04d", invoiceSeq++);
                if (saleRepository.findByInvoiceNumber(invoiceNumber).isPresent()) continue;

                Sale sale = new Sale();
                sale.setInvoiceNumber(invoiceNumber);
                sale.setCustomerName(customers[rnd.nextInt(customers.length)]);
                sale.setUser(admin);
                sale.setDiscountAmount(BigDecimal.ZERO);
                sale.setTaxAmount(BigDecimal.ZERO);
                sale.setPaymentMethod(Sale.PaymentMethod.CASH);
                sale.setPaymentStatus(Sale.PaymentStatus.PAID);
                sale.setCreatedAt(saleTime);
                sale.setUpdatedAt(saleTime);
                Sale saved = saleRepository.save(sale);

                // 1-3 different products per sale
                java.util.Set<Long> usedInSale = new java.util.HashSet<>();
                int itemCount = 1 + rnd.nextInt(3);
                BigDecimal subtotal = BigDecimal.ZERO;
                for (int it = 0; it < itemCount; it++) {
                    Product p = prods.get(rnd.nextInt(prods.size()));
                    if (!usedInSale.add(p.getId())) continue;

                    int qty = 1 + rnd.nextInt(isWeekend ? 6 : 4);
                    SaleItem item = new SaleItem();
                    item.setSale(saved);
                    item.setProduct(p);
                    item.setQuantity(qty);
                    item.setUnitPrice(p.getSellingPrice());
                    item.setDiscountPercent(BigDecimal.ZERO);
                    BigDecimal lineTotal = p.getSellingPrice().multiply(BigDecimal.valueOf(qty));
                    item.setTotalPrice(lineTotal);
                    saleItemRepository.save(item);
                    subtotal = subtotal.add(lineTotal);
                }

                saved.setSubtotal(subtotal);
                saved.setTotalAmount(subtotal);
                saleRepository.save(saved);
            }
        }
        log.info("Seeded {} sample sales across 90 days for {} products", saleRepository.count(), prods.size());
    }
}
