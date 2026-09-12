package com.maths.teacher.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "courses",
        indexes = {
                @Index(name = "idx_courses_active", columnList = "active")
        }
)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /**
     * Last day a NEW student may join, Indian time. Null means open with no end
     * date. Deliberately separate from validityDays: this closes the door on new
     * people, validityDays decides how long the people already inside keep access.
     */
    @Column(name = "enrolment_closes_on")
    private java.time.LocalDate enrolmentClosesOn;

    @Column(name = "price_paise", nullable = false)
    private int pricePaise;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * How long access lasts after purchase, in days. 0 means the course never
     * expires. Only applied to purchases made while this value is set — changing
     * it never affects students who have already bought.
     */
    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Course(String title, String description, int pricePaise, String currency, String thumbnailUrl, boolean active) {
        this(title, description, pricePaise, currency, thumbnailUrl, active, 0);
    }

    public Course(String title, String description, int pricePaise, String currency, String thumbnailUrl, boolean active, int validityDays) {
        this.title = title;
        this.description = description;
        this.pricePaise = pricePaise;
        this.currency = currency;
        this.thumbnailUrl = thumbnailUrl;
        this.active = active;
        this.validityDays = validityDays;
        this.createdAt = Instant.now();
    }

    protected Course() {
        // for JPA
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getPricePaise() { return pricePaise; }
    public String getCurrency() { return currency; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public boolean isActive() { return active; }
    public java.time.LocalDate getEnrolmentClosesOn() { return enrolmentClosesOn; }
    public void setEnrolmentClosesOn(java.time.LocalDate enrolmentClosesOn) {
        this.enrolmentClosesOn = enrolmentClosesOn;
    }

    /**
     * Whether the course still takes new students today. An inactive course is
     * off sale outright; a dated one closes after its last enrolment day.
     */
    public boolean isEnrolmentOpen(java.time.LocalDate today) {
        return active && (enrolmentClosesOn == null || !today.isAfter(enrolmentClosesOn));
    }
    public int getValidityDays() { return validityDays; }
    public Instant getCreatedAt() { return createdAt; }

    // Setters for admin updates
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPricePaise(int pricePaise) { this.pricePaise = pricePaise; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public void setActive(boolean active) { this.active = active; }
    public void setValidityDays(int validityDays) { this.validityDays = validityDays; }
}
