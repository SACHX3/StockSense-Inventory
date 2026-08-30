package com.stocksense.controller;

import com.stocksense.dto.request.UserRequest;
import com.stocksense.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("pageTitle", "User Management");
        return "admin/users";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("user", new UserRequest());
        model.addAttribute("roles", userService.findAllRoles());
        model.addAttribute("pageTitle", "Add User");
        return "admin/user-form";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute UserRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.create(request);
            redirectAttributes.addFlashAttribute("successMsg", "User created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/users/create";
        }
        return "redirect:/users";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        var user = userService.findById(id);
        UserRequest req = new UserRequest();
        req.setUsername(user.getUsername());
        req.setEmail(user.getEmail());
        req.setFullName(user.getFullName());
        req.setPhone(user.getPhone());
        req.setRoleId(user.getRole().getId());
        model.addAttribute("user", req);
        model.addAttribute("userId", id);
        model.addAttribute("roles", userService.findAllRoles());
        model.addAttribute("pageTitle", "Edit User");
        return "admin/user-form";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute UserRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.update(id, request);
            redirectAttributes.addFlashAttribute("successMsg", "User updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/toggle/{id}")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.toggleStatus(id);
            redirectAttributes.addFlashAttribute("successMsg", "User status updated!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/users";
    }
}
