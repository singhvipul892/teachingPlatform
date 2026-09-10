package com.maths.teacher.catalog.web.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO for a student enrolled in a course.
 */
public class StudentResponse {

    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String mobileNumber;
    private final Instant purchasedAt;
    /** Last day of access, Indian time. Null means access never ends. */
    private final LocalDate expiryDate;
    private final boolean expired;

    public StudentResponse(Long id, String firstName, String lastName, String email, String mobileNumber, Instant purchasedAt) {
        this(id, firstName, lastName, email, mobileNumber, purchasedAt, null, false);
    }

    public StudentResponse(Long id, String firstName, String lastName, String email, String mobileNumber,
                           Instant purchasedAt, LocalDate expiryDate, boolean expired) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.purchasedAt = purchasedAt;
        this.expiryDate = expiryDate;
        this.expired = expired;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public boolean isExpired() {
        return expired;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
