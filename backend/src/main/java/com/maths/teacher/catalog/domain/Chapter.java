package com.maths.teacher.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A section of a course (Percentage, Profit & Loss, ...) holding its classes.
 * displayOrder is position within the course; the admin panel writes it from
 * drag order, so it is always 1..n.
 */
@Entity
@Table(
        name = "chapters",
        indexes = @Index(name = "idx_chapters_course_order", columnList = "course_id,display_order")
)
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Chapter() {
        // for JPA
    }

    public Chapter(Long courseId, String title, Integer displayOrder) {
        this.courseId = courseId;
        this.title = title;
        this.displayOrder = displayOrder;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public Integer getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
