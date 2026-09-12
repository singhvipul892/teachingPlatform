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
    /**
     * The Indian day an admin removed this student, or null if they are still
     * enrolled. Only ever set on rosters asked for with includeRemoved.
     */
    private final LocalDate removedOn;
    /** How many payments this student has made for the course. Free seats count 0. */
    private final long paymentCount;
    /**
     * The Indian day of their first payment, or null if they never paid. Comes
     * from the ledger, not from purchasedAt, which moves on every re-tag.
     */
    private final LocalDate firstPaidOn;

    public StudentResponse(Long id, String firstName, String lastName, String email, String mobileNumber, Instant purchasedAt) {
        this(id, firstName, lastName, email, mobileNumber, purchasedAt, null, false);
    }

    public StudentResponse(Long id, String firstName, String lastName, String email, String mobileNumber,
                           Instant purchasedAt, LocalDate expiryDate, boolean expired) {
        this(id, firstName, lastName, email, mobileNumber, purchasedAt, expiryDate, expired, null);
    }

    public StudentResponse(Long id, String firstName, String lastName, String email, String mobileNumber,
                           Instant purchasedAt, LocalDate expiryDate, boolean expired, LocalDate removedOn) {
        this(id, firstName, lastName, email, mobileNumber, purchasedAt, expiryDate, expired, removedOn, 0, null);
    }

    public StudentResponse(Long id, String firstName, String lastName, String email, String mobileNumber,
                           Instant purchasedAt, LocalDate expiryDate, boolean expired, LocalDate removedOn,
                           long paymentCount, LocalDate firstPaidOn) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.purchasedAt = purchasedAt;
        this.expiryDate = expiryDate;
        this.expired = expired;
        this.removedOn = removedOn;
        this.paymentCount = paymentCount;
        this.firstPaidOn = firstPaidOn;
    }

    public LocalDate getRemovedOn() {
        return removedOn;
    }

    public long getPaymentCount() {
        return paymentCount;
    }

    public LocalDate getFirstPaidOn() {
        return firstPaidOn;
    }

    /** False once an admin removed them; the row is kept so it can be undone. */
    public boolean isEnrolled() {
        return removedOn == null;
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
