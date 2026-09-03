package com.stocksense.service;

import com.stocksense.entity.StoreProfile;
import com.stocksense.repository.StoreProfileRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StoreProfileService {

    private static final Long SINGLETON_ID = 1L;

    private final StoreProfileRepository repository;
    private final AuditLogService auditLogService;

    /**
     * The one and only store profile, created with sensible defaults on first
     * access so a fresh install never has to deal with a missing row.
     */
    @Transactional
    public StoreProfile get() {
        return repository.findById(SINGLETON_ID).orElseGet(() -> {
            StoreProfile p = new StoreProfile();
            p.setId(SINGLETON_ID);
            return repository.save(p);
        });
    }

    @Transactional
    public StoreProfile save(StoreProfile submitted) {
        StoreProfile p = get();
        // Name is the one field that must not be blank - it is the letterhead.
        String name = submitted.getStoreName();
        p.setStoreName(name != null && !name.isBlank() ? name.trim() : "StockSense");
        p.setTagline(nz(submitted.getTagline()));
        p.setPhone(nz(submitted.getPhone()));
        p.setEmail(nz(submitted.getEmail()));
        p.setAddress(nz(submitted.getAddress()));
        p.setTaxNumber(nz(submitted.getTaxNumber()));
        String footer = submitted.getReceiptFooter();
        p.setReceiptFooter(footer != null && !footer.isBlank()
                ? footer.trim() : "Thank you for your purchase!");
        p.setUpdatedAt(LocalDateTime.now());
        StoreProfile saved = repository.save(p);
        auditLogService.log("STORE_PROFILE_UPDATED", "StoreProfile", SINGLETON_ID,
                "Store details updated: " + saved.getStoreName());
        return saved;
    }

    private String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
