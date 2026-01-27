package com.maths.teacher.catalog.web.dto;

public class PdfDownloadResponse {

    private final String url;
    private final Integer expiresInSeconds;

    public PdfDownloadResponse(String url, Integer expiresInSeconds) {
        this.url = url;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getUrl() {
        return url;
    }

    public Integer getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
