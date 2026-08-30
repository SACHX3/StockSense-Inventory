package com.stocksense.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.stocksense.service.CategoryService;
import com.stocksense.service.FileUploadService;

@Controller
@RequestMapping("/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService   categoryService;
    private final FileUploadService fileUploadService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("pageTitle", "Product Categories");
        return "products/categories";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                         RedirectAttributes ra) {
        try {
            String imagePath = null;
            if (imageFile != null && !imageFile.isEmpty()) {
                imagePath = fileUploadService.uploadProductImage(imageFile);
            }
            categoryService.create(name, description, imagePath);
            ra.addFlashAttribute("successMsg", "Category '" + name + "' created!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/categories";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                         @RequestParam(value = "removeImage", required = false, defaultValue = "false") String removeImage,
                         RedirectAttributes ra) {
        try {
            log.info("Category edit: id={} removeImage='{}' hasNewImage={}",
                    id, removeImage, imageFile != null && !imageFile.isEmpty());

            boolean doRemove = "true".equals(removeImage);

            if (doRemove) {
                // User clicked "Delete Image" — clear it, show default icon
                categoryService.updateWithImage(id, name, description, null, true);
                ra.addFlashAttribute("successMsg", "Category updated — image removed.");
            } else if (imageFile != null && !imageFile.isEmpty()) {
                // New image uploaded
                String path = fileUploadService.uploadProductImage(imageFile);
                categoryService.updateWithImage(id, name, description, path, false);
                ra.addFlashAttribute("successMsg", "Category updated with new image!");
            } else {
                // Name/description only — keep existing image
                categoryService.updateWithImage(id, name, description, null, false);
                ra.addFlashAttribute("successMsg", "Category updated!");
            }
        } catch (Exception e) {
            log.error("Category update error: {}", e.getMessage(), e);
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/categories";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            categoryService.delete(id);
            ra.addFlashAttribute("successMsg", "Category deleted!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/categories";
    }
}
