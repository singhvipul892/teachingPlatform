package com.maths.teacher.catalog.web.dto;

import java.time.Instant;

/**
 * Response DTO for admin course listing/detail views.
 * Extends the public CourseResponse with admin-specific fields.
 */
public class AdminCourseResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final int pricePaise;
    private final String currency;
    private final String thumbnailUrl;
    private final boolean active;
    /** 0 = lifetime access for new purchases. */
    private final int validityDays;
    /** Last day a new student may join. Null = open indefinitely. */
    private final java.time.LocalDate enrolmentClosesOn;
    /** Whether the course takes new students today — active, and within its window. */
    private final boolean enrolmentOpen;
    /** Everyone who ever enrolled, expired or not — this is what was sold. */
    private final int studentCount;
    /** Enrolments that have not lapsed. Equals studentCount for lifetime courses. */
    private final int activeStudentCount;
    private final Instant createdAt;

    public AdminCourseResponse(
            Long id,
            String title,
            String description,
            int pricePaise,
            String currency,
            String thumbnailUrl,
            boolean active,
            int validityDays,
            java.time.LocalDate enrolmentClosesOn,
            boolean enrolmentOpen,
            int studentCount,
            int activeStudentCount,
            Instant createdAt
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.pricePaise = pricePaise;
        this.currency = currency;
        this.thumbnailUrl = thumbnailUrl;
        this.active = active;
        this.validityDays = validityDays;
        this.enrolmentClosesOn = enrolmentClosesOn;
        this.enrolmentOpen = enrolmentOpen;
        this.studentCount = studentCount;
        this.activeStudentCount = activeStudentCount;
        this.createdAt = createdAt;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getPricePaise() {
        return pricePaise;
    }

    public String getCurrency() {
        return currency;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public boolean isActive() {
        return active;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public java.time.LocalDate getEnrolmentClosesOn() {
        return enrolmentClosesOn;
    }

    public boolean isEnrolmentOpen() {
        return enrolmentOpen;
    }

    public int getStudentCount() {
        return studentCount;
    }

    public int getActiveStudentCount() {
        return activeStudentCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
