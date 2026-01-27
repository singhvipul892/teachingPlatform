package com.maths.teacher.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "video_pdfs")
public class VideoPdf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "video_id_fk", nullable = false)
    private Video video;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "pdf_type", nullable = false, length = 100)
    private String pdfType;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected VideoPdf() {
        // for JPA
    }

    public VideoPdf(
            Long id,
            Video video,
            String title,
            String pdfType,
            String fileUrl,
            Integer displayOrder
    ) {
        this.id = id;
        this.video = video;
        this.title = title;
        this.pdfType = pdfType;
        this.fileUrl = fileUrl;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Video getVideo() {
        return video;
    }

    public String getTitle() {
        return title;
    }

    public String getPdfType() {
        return pdfType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPdfType(String pdfType) {
        this.pdfType = pdfType;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
