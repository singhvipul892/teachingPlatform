package com.maths.teacher.catalog.web.dto;

public class VideoResponse {

    private final String videoId;
    private final String title;
    private final String thumbnailUrl;
    private final String duration;
    private final Integer displayOrder;
    private final java.util.List<PdfResponse> pdfs;

    public VideoResponse(
            String videoId,
            String title,
            String thumbnailUrl,
            String duration,
            Integer displayOrder,
            java.util.List<PdfResponse> pdfs
    ) {
        this.videoId = videoId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.duration = duration;
        this.displayOrder = displayOrder;
        this.pdfs = pdfs;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getDuration() {
        return duration;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public java.util.List<PdfResponse> getPdfs() {
        return pdfs;
    }
}
