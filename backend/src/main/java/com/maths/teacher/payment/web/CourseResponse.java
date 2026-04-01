package com.maths.teacher.payment.web;

public class CourseResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final int pricePaise;
    private final String currency;
    private final String thumbnailUrl;

    public CourseResponse(Long id, String title, String description, int pricePaise, String currency, String thumbnailUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.pricePaise = pricePaise;
        this.currency = currency;
        this.thumbnailUrl = thumbnailUrl;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getPricePaise() { return pricePaise; }
    public String getCurrency() { return currency; }
    public String getThumbnailUrl() { return thumbnailUrl; }
}
