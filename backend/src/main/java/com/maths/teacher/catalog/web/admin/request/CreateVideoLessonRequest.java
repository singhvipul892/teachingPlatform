package com.maths.teacher.catalog.web.admin.request;

import org.springframework.web.multipart.MultipartFile;

/**
 * Request DTO for creating a video lesson with YouTube link and optional PDFs.
 */
public class CreateVideoLessonRequest {

    private String youtubeVideoLink;
    private String title;
    private String section;
    private String duration;
    private Integer displayOrder;
    private MultipartFile notesPdf;
    private MultipartFile solvedPracticeSetPdf;
    private MultipartFile annotatedPracticeSetPdf;

    public CreateVideoLessonRequest() {
        // Default constructor for Spring binding
    }

    public String getYoutubeVideoLink() {
        return youtubeVideoLink;
    }

    public void setYoutubeVideoLink(String youtubeVideoLink) {
        this.youtubeVideoLink = youtubeVideoLink;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public MultipartFile getNotesPdf() {
        return notesPdf;
    }

    public void setNotesPdf(MultipartFile notesPdf) {
        this.notesPdf = notesPdf;
    }

    public MultipartFile getSolvedPracticeSetPdf() {
        return solvedPracticeSetPdf;
    }

    public void setSolvedPracticeSetPdf(MultipartFile solvedPracticeSetPdf) {
        this.solvedPracticeSetPdf = solvedPracticeSetPdf;
    }

    public MultipartFile getAnnotatedPracticeSetPdf() {
        return annotatedPracticeSetPdf;
    }

    public void setAnnotatedPracticeSetPdf(MultipartFile annotatedPracticeSetPdf) {
        this.annotatedPracticeSetPdf = annotatedPracticeSetPdf;
    }
}
