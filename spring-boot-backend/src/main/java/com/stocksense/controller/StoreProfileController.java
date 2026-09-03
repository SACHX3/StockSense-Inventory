package com.stocksense.controller;

import com.stocksense.entity.StoreProfile;
import com.stocksense.service.StoreProfileService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Store details (name, phone, address) shown as the letterhead on printed
 * receipts. Admin only - see SecurityConfig, which restricts /settings/**.
 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class StoreProfileController {

    private final StoreProfileService storeProfileService;

    @GetMapping("/store")
    public String storeForm(Model model) {
        model.addAttribute("store", storeProfileService.get());
        model.addAttribute("pageTitle", "Store Details");
        return "settings/store";
    }

    @PostMapping("/store")
    public String saveStore(@ModelAttribute("store") StoreProfile store,
                            RedirectAttributes redirectAttributes) {
        try {
            storeProfileService.save(store);
            redirectAttributes.addFlashAttribute("successMsg", "Store details saved.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Could not save: " + e.getMessage());
        }
        return "redirect:/settings/store";
    }
}
