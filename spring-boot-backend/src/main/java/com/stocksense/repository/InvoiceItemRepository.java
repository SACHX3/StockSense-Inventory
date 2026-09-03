package com.stocksense.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stocksense.entity.InvoiceItem;

import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoiceId(Long invoiceId);
    List<InvoiceItem> findByInvoiceIdAndIsValidatedTrue(Long invoiceId);

    /** Clear a previous OCR run's rows before saving a new one - see
     *  AIIntegrationService.parseAndSaveInvoiceItems. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    void deleteByInvoiceId(Long invoiceId);
}
