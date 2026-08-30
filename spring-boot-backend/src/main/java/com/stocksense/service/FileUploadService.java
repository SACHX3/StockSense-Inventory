package com.stocksense.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileUploadService {

    @Value("${app.upload.products-dir:uploads/products}")
    private String productsDir;

    @Value("${app.upload.invoices-dir:uploads/invoices}")
    private String invoicesDir;

    @Value("${app.upload.avatars-dir:uploads/avatars}")
    private String avatarsDir;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp");
    private static final List<String> ALLOWED_INVOICE_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "application/pdf");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public String uploadProductImage(MultipartFile file) throws IOException {
        validateFile(file, ALLOWED_IMAGE_TYPES);
        return saveFile(file, productsDir);
    }

    public String uploadInvoiceFile(MultipartFile file) throws IOException {
        validateFile(file, ALLOWED_INVOICE_TYPES);
        return saveFile(file, invoicesDir);
    }

    public String uploadAvatar(MultipartFile file) throws IOException {
        validateFile(file, ALLOWED_IMAGE_TYPES);
        return saveFile(file, avatarsDir);
    }

    private String saveFile(MultipartFile file, String directory) throws IOException {
        Path uploadPath = Paths.get(directory);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String newFilename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(newFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return directory + "/" + newFilename;
    }

    private void validateFile(MultipartFile file, List<String> allowedTypes) {
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("File size exceeds 10MB limit");
        if (!allowedTypes.contains(file.getContentType())) {
            throw new IllegalArgumentException("File type not allowed: " + file.getContentType());
        }
    }

    public void deleteFile(String filePath) {
        if (filePath != null) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException e) {
                // Log but don't fail
            }
        }
    }

    public boolean isInvoicePdf(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".pdf");
    }
}
