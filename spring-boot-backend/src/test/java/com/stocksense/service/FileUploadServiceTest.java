package com.stocksense.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileUploadService Tests")
class FileUploadServiceTest {

    @TempDir Path tempDir;

    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        fileUploadService = new FileUploadService();
        ReflectionTestUtils.setField(fileUploadService, "productsDir", tempDir.resolve("products").toString());
        ReflectionTestUtils.setField(fileUploadService, "invoicesDir", tempDir.resolve("invoices").toString());
        ReflectionTestUtils.setField(fileUploadService, "avatarsDir", tempDir.resolve("avatars").toString());
    }

    @Test
    @DisplayName("TC53 - file upload: stores an allowed product image with a generated filename")
    void uploadProductImage_validPng_savesFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "imageFile", "product.png", "image/png", new byte[]{1, 2, 3});

        String path = fileUploadService.uploadProductImage(file);

        assertThat(path).startsWith(tempDir.resolve("products").toString());
        assertThat(Files.exists(Path.of(path))).isTrue();
        assertThat(Files.readAllBytes(Path.of(path))).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("TC54 - file upload: rejects an unsupported invoice type")
    void uploadInvoiceFile_executableType_rejectsFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.exe", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> fileUploadService.uploadInvoiceFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File type not allowed");
    }

    @Test
    @DisplayName("TC55 - file upload: rejects an empty file")
    void uploadProductImage_emptyFile_rejectsFile() {
        MockMultipartFile file = new MockMultipartFile(
                "imageFile", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileUploadService.uploadProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File is empty");
    }

    @Test
    @DisplayName("TC56 - file upload: rejects files over the 10 MB limit")
    void uploadAvatar_oversizedFile_rejectsFile() {
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "large.png", "image/png", new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> fileUploadService.uploadAvatar(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB limit");
    }

    @Test
    @DisplayName("TC57 - file upload: identifies invoice PDFs case-insensitively")
    void isInvoicePdf_handlesCaseAndNull() {
        assertThat(fileUploadService.isInvoicePdf("uploads/invoices/INV-01.PDF")).isTrue();
        assertThat(fileUploadService.isInvoicePdf("uploads/invoices/INV-01.jpg")).isFalse();
        assertThat(fileUploadService.isInvoicePdf(null)).isFalse();
    }

    @Test
    @DisplayName("TC58 - file upload: deleteFile removes an existing file")
    void deleteFile_existingFile_removesIt() throws Exception {
        Path path = tempDir.resolve("remove-me.txt");
        Files.writeString(path, "temporary");

        fileUploadService.deleteFile(path.toString());

        assertThat(Files.exists(path)).isFalse();
    }
}
