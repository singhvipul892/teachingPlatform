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
    private final int studentCount;
    private final Instant createdAt;

    public AdminCourseResponse(
            Long id,
            String title,
            String description,
            int pricePaise,
            String currency,
            String thumbnailUrl,
            boolean active,
            int studentCount,
            Instant createdAt
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.pricePaise = pricePaise;
        this.currency = currency;
        this.thumbnailUrl = thumbnailUrl;
        this.active = active;
        this.studentCount = studentCount;
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

    public int getStudentCount() {
        return studentCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
