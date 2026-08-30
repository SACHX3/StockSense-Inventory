package com.stocksense.controller;

import com.stocksense.dto.response.ApiResponse;
import com.stocksense.entity.Invoice;
import com.stocksense.entity.InvoiceItem;
import com.stocksense.repository.InvoiceItemRepository;
import com.stocksense.repository.InvoiceRepository;
import com.stocksense.service.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/ocr")
@RequiredArgsConstructor
@Slf4j
public class OCRController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final FileUploadService fileUploadService;
    private final AIIntegrationService aiService;
    private final SupplierService supplierService;

    @GetMapping
    public String list(Model model, @RequestParam(defaultValue = "0") int page) {
        Page<Invoice> invoices = invoiceRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, 20));
        model.addAttribute("invoices", invoices);
        model.addAttribute("pageTitle", "OCR Invoice Processing");
        return "ocr/list";
    }

    @GetMapping("/upload")
    public String uploadForm(Model model) {
        model.addAttribute("suppliers", supplierService.findAllActive());
        model.addAttribute("pageTitle", "Upload Invoice");
        return "ocr/upload";
    }

    @PostMapping("/upload")
    public String uploadInvoice(@RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) Long supplierId,
                                @RequestParam(required = false) String invoiceNumber,
                                RedirectAttributes redirectAttributes) {
        try {
            String filePath = fileUploadService.uploadInvoiceFile(file);
            Invoice invoice = new Invoice();
            invoice.setFilePath(filePath);
            invoice.setFileType(file.getContentType() != null && file.getContentType().contains("pdf")
                    ? Invoice.FileType.PDF : Invoice.FileType.IMAGE);
            invoice.setInvoiceNumber(invoiceNumber == null ? null : invoiceNumber.trim());
            if (supplierId != null) {
                supplierService.findAllActive().stream()
                        .filter(s -> s.getId().equals(supplierId))
                        .findFirst().ifPresent(invoice::setSupplier);
            }
            Invoice saved = invoiceRepository.save(invoice);
            redirectAttributes.addFlashAttribute("successMsg", "Invoice uploaded! Click 'Process OCR' to extract data.");
            return "redirect:/ocr/view/" + saved.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Upload failed: " + e.getMessage());
            return "redirect:/ocr/upload";
        }
    }

    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        model.addAttribute("invoice", invoice);
        model.addAttribute("items", invoiceItemRepository.findByInvoiceId(id));
        model.addAttribute("pageTitle", "Invoice #" + id);
        return "ocr/view";
    }

    @PostMapping("/api/process/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> processOcr(@PathVariable Long id) {
        try {
            Map<String, Object> result = aiService.processInvoice(id);
            if ("error".equalsIgnoreCase(String.valueOf(result.get("status")))) {
                String message = String.valueOf(result.getOrDefault("message", "OCR service unavailable"));
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error(message));
            }
            return ResponseEntity.ok(ApiResponse.success("OCR processing complete", result));
        } catch (Exception e) {
            log.error("OCR process error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Validate a single invoice item - also saves name and quantity edits
     */
    @PostMapping("/api/validate-item/{itemId}")
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> validateItem(
            @PathVariable Long itemId,
            @RequestBody Map<String, Object> updates) {
        try {
            InvoiceItem item = invoiceItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));

            // Update quantity if provided
            if (updates.containsKey("quantity") && updates.get("quantity") != null) {
                try {
                    int qty = ((Number) updates.get("quantity")).intValue();
                    if (qty > 0) item.setQuantity(qty);
                } catch (Exception e) {
                    log.warn("Invalid quantity value: {}", updates.get("quantity"));
                }
            }

            // Update product name if provided
            if (updates.containsKey("productName") && updates.get("productName") != null) {
                String name = updates.get("productName").toString().trim();
                if (!name.isEmpty()) item.setProductName(name);
            }

            // Set validated status
            Boolean validated = Boolean.TRUE; // default to true when saving
            if (updates.containsKey("isValidated") && updates.get("isValidated") != null) {
                validated = (Boolean) updates.get("isValidated");
            }
            item.setIsValidated(validated);

            invoiceItemRepository.save(item);
            return ResponseEntity.ok(ApiResponse.success("Item saved successfully"));
        } catch (Exception e) {
            log.error("Validate item error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Apply invoice items to inventory stock.
     * Works with or without linked products - matches by product name.
     */
    @PostMapping("/api/apply/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyToInventory(@PathVariable Long id) {
        try {
            Map<String, Object> result = aiService.applyInvoiceToInventory(id);
            return ResponseEntity.ok(ApiResponse.success("Invoice applied to inventory", result));
        } catch (Exception e) {
            log.error("Apply to inventory error: {}", e.getMessage());
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("already applied")
                    ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteInvoice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice not found"));
            if (invoice.getIsApplied()) {
                redirectAttributes.addFlashAttribute("errorMsg", 
                    "Cannot delete invoice #" + id + " - it has already been applied to inventory.");
            } else {
                // Delete items first, then invoice
                invoiceItemRepository.findByInvoiceId(id).forEach(item -> invoiceItemRepository.delete(item));
                // Delete the file
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(invoice.getFilePath()));
                } catch (Exception ignored) {}
                invoiceRepository.delete(invoice);
                redirectAttributes.addFlashAttribute("successMsg", "Invoice #" + id + " deleted successfully.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Delete failed: " + e.getMessage());
        }
        return "redirect:/ocr";
    }
}
