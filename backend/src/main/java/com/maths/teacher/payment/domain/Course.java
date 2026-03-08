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

    @Column(name = "price_paise", nullable = false)
    private int pricePaise;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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
    public Instant getCreatedAt() { return createdAt; }
}
