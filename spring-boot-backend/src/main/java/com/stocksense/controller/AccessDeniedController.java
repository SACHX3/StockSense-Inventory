package com.stocksense.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccessDeniedController {

    @GetMapping("/access-denied")
    public String accessDenied(HttpServletRequest request, Model model) {
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        model.addAttribute("pageTitle", "Access Denied");
        model.addAttribute("requestedResource",
                originalUri == null ? "the requested administrative resource" : originalUri.toString());
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());
        return "error/access-denied";
    }
}
