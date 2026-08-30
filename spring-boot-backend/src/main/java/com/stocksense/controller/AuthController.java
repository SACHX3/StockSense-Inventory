package com.stocksense.controller;

import com.stocksense.entity.User;
import com.stocksense.repository.UserRepository;
import com.stocksense.service.FileUploadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;

    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "auth/login";
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        if (authentication != null) {
            userRepository.findByUsername(authentication.getName()).ifPresent(u -> model.addAttribute("user", u));
        }
        model.addAttribute("pageTitle", "My Profile");
        return "auth/profile";
    }

    // Real avatar upload: stores the file via FileUploadService (same pattern as
    // product images) and persists the path on the logged-in user's row.
    @PostMapping("/profile/avatar")
    public String uploadAvatar(Authentication authentication,
                                @RequestParam("avatar") MultipartFile avatar,
                                RedirectAttributes redirectAttributes) {
        if (authentication == null) {
            return "redirect:/login";
        }
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            String path = fileUploadService.uploadAvatar(avatar);
            user.setAvatarPath(path);
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("successMsg", "Profile photo updated.");
        } catch (Exception e) {
            log.warn("Avatar upload failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMsg", "Couldn't upload photo: " + e.getMessage());
        }
        return "redirect:/profile";
    }
}
