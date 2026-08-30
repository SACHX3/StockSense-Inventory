package com.stocksense.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stocksense.entity.InvoiceItem;

import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoiceId(Long invoiceId);
    List<InvoiceItem> findByInvoiceIdAndIsValidatedTrue(Long invoiceId);
}
