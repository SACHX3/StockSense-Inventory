package com.stocksense.controller;

import com.stocksense.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    public String list(Model model, @RequestParam(defaultValue = "0") int page) {
        model.addAttribute("logs", auditLogService.findAll(
                org.springframework.data.domain.PageRequest.of(page, 50)));
        model.addAttribute("pageTitle", "Audit Logs");
        return "admin/audit";
    }
}
