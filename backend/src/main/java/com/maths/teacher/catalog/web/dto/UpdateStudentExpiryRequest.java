package com.maths.teacher.catalog.web.dto;

import java.time.LocalDate;

/**
 * Admin override of one student's course expiry.
 *
 * The date is the LAST DAY of access in Indian time — access runs to the end of
 * it, matching how expiry is set at purchase. A null or absent date grants
 * lifetime access.
 */
public class UpdateStudentExpiryRequest {

    private LocalDate expiryDate;

    public UpdateStudentExpiryRequest() {
    }

    public UpdateStudentExpiryRequest(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }
}
