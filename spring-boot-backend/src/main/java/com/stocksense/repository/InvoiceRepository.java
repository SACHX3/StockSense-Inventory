package com.stocksense.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import com.stocksense.entity.Invoice;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Page<Invoice> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Invoice> findByOcrStatus(Invoice.OcrStatus status);
    List<Invoice> findByIsAppliedFalseAndOcrStatus(Invoice.OcrStatus status);

    /** Serialise concurrent apply requests for the same invoice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i left join fetch i.items where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    /** Find another already-applied OCR invoice with the same business invoice number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id <> :invoiceId " +
           "and i.isApplied = true " +
           "and lower(trim(i.invoiceNumber)) = lower(trim(:invoiceNumber)) " +
           "order by i.id asc")
    List<Invoice> findAppliedByInvoiceNumberForUpdate(
            @Param("invoiceNumber") String invoiceNumber,
            @Param("invoiceId") Long invoiceId);
}
