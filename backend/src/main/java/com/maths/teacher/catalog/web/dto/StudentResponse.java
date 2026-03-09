package com.maths.teacher.catalog.web.dto;

import java.time.Instant;

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

    public StudentResponse(Long id, String firstName, String lastName, String email, String mobileNumber, Instant purchasedAt) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.purchasedAt = purchasedAt;
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

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
