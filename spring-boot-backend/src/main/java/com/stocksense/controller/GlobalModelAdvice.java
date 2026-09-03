package com.stocksense.controller;

import com.stocksense.entity.StoreProfile;
import com.stocksense.service.StoreProfileService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts the store profile into the model for EVERY page, so the receipt (and any
 * future letterhead) can print the shop's own name and phone number without each
 * controller having to remember to add it.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final StoreProfileService storeProfileService;

    @ModelAttribute("store")
    public StoreProfile store() {
        try {
            return storeProfileService.get();
        } catch (Exception e) {
            // Never let a settings lookup take a page down - fall back to defaults.
            return new StoreProfile();
        }
    }
}
