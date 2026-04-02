package com.maths.teacher.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "videos",
        indexes = {
                @Index(name = "idx_videos_course_id", columnList = "course_id"),
                @Index(name = "idx_videos_course_order", columnList = "course_id,display_order")
        }
)
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false, length = 64)
    private String videoId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Column(length = 20)
    private String duration;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<VideoPdf> pdfs = new ArrayList<>();

    protected Video() {
        // for JPA
    }

    public Video(
            Long id,
            String videoId,
            String title,
            Long courseId,
            String thumbnailUrl,
            String duration,
            Integer displayOrder
    ) {
        this.id = id;
        this.videoId = videoId;
        this.title = title;
        this.courseId = courseId;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public String getVideoId() { return videoId; }
    public String getTitle() { return title; }
    public Long getCourseId() { return courseId; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDuration() { return duration; }
    public Integer getDisplayOrder() { return displayOrder; }
    public List<VideoPdf> getPdfs() { return pdfs; }

    public void setTitle(String title) { this.title = title; }
    public void setDuration(String duration) { this.duration = duration; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
