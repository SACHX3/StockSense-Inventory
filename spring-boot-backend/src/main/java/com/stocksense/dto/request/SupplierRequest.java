package com.stocksense.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SupplierRequest {
    @NotBlank(message = "Supplier name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 200)
    private String contactPerson;

    @Email(message = "Invalid email")
    private String email;

    @NotBlank(message = "Phone is required")
    @Size(max = 30)
    private String phone;

    private String address;
    private String city;
    private String country = "Sri Lanka";
    private String taxNumber;
    private String paymentTerms;

    /** Days from order to delivery. Defaults to a week when left blank. */
    private Integer leadTimeDays = 7;
    private String notes;
}
