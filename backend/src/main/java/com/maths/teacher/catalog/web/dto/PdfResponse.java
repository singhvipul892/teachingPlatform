package com.maths.teacher.catalog.web.dto;

public class PdfResponse {

    private final Long id;
    private final String title;
    private final String pdfType;
    private final String fileUrl;
    private final Integer displayOrder;

    public PdfResponse(Long id, String title, String pdfType, String fileUrl, Integer displayOrder) {
        this.id = id;
        this.title = title;
        this.pdfType = pdfType;
        this.fileUrl = fileUrl;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
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
}
