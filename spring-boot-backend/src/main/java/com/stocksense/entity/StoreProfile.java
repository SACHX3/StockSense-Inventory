package com.stocksense.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * The shop's own details - name, phone, address - used as the letterhead on
 * printed receipts and reports.
 *
 * This is a SINGLETON row: there is exactly one store, always at id = 1. Storing
 * it as a table rather than in application.properties means the owner can change
 * the shop name from the UI without editing a config file and restarting.
 */
@Entity
@Table(name = "store_profile")
@Data
@NoArgsConstructor
public class StoreProfile {

    /** Always 1. See StoreProfileService.get(). */
    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 200)
    private String storeName = "StockSense";

    @Column(length = 200)
    private String tagline = "AI-Powered Inventory Management";

    @Column(length = 60)
    private String phone = "";

    @Column(length = 120)
    private String email = "";

    @Column(length = 300)
    private String address = "";

    /** VAT / tax registration number, printed on the receipt when set. */
    @Column(name = "tax_number", length = 60)
    private String taxNumber = "";

    /** Footer line printed at the bottom of every receipt. */
    @Column(name = "receipt_footer", length = 200)
    private String receiptFooter = "Thank you for your purchase!";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
