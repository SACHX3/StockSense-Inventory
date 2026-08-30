package com.stocksense.service;

import com.stocksense.dto.request.SupplierRequest;
import com.stocksense.entity.Supplier;
import com.stocksense.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final AuditLogService auditLogService;

    public List<Supplier> findAll() {
        return supplierRepository.findAll();
    }

    public List<Supplier> findAllActive() {
        return supplierRepository.findByIsActiveTrue();
    }

    public Supplier findById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found: " + id));
    }

    @Transactional
    public Supplier create(SupplierRequest request) {
        Supplier supplier = mapToEntity(new Supplier(), request);
        Supplier saved = supplierRepository.save(supplier);
        auditLogService.log("SUPPLIER_CREATED", "Supplier", saved.getId(), "Created: " + saved.getName());
        return saved;
    }

    @Transactional
    public Supplier update(Long id, SupplierRequest request) {
        Supplier supplier = findById(id);
        mapToEntity(supplier, request);
        Supplier saved = supplierRepository.save(supplier);
        auditLogService.log("SUPPLIER_UPDATED", "Supplier", saved.getId(), "Updated: " + saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Supplier supplier = findById(id);
        supplier.setIsActive(false);
        supplierRepository.save(supplier);
        auditLogService.log("SUPPLIER_DELETED", "Supplier", id, "Deleted: " + supplier.getName());
    }

    private Supplier mapToEntity(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.getName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());
        supplier.setCity(request.getCity());
        supplier.setCountry(request.getCountry() != null ? request.getCountry() : "Sri Lanka");
        supplier.setTaxNumber(request.getTaxNumber());
        supplier.setPaymentTerms(request.getPaymentTerms());
        supplier.setNotes(request.getNotes());
        return supplier;
    }

    public List<Supplier> search(String keyword) {
        return supplierRepository.searchSuppliers(keyword);
    }
}
