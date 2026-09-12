package com.maths.teacher.payment.web;

import java.time.LocalDate;

public class CourseResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final int pricePaise;
    private final String currency;
    private final String thumbnailUrl;

    /** How long access lasts for a purchase made now. 0 means lifetime. */
    private final int validityDays;

    /**
     * Last day this student can use the course, in Indian time. Null both for
     * lifetime access and in the public catalogue, where there is no student.
     */
    private final LocalDate expiryDate;

    /** True only when this student's access has already lapsed. */
    private final boolean expired;

    /** Days until access ends; 0 means tonight. Null for lifetime access. */
    private final Integer daysRemaining;
    /**
     * Whether the student could buy this course today. False once it is off sale
     * or its enrolment window has closed — so a client can say "no longer
     * available" instead of offering a Buy Again button that cannot work.
     */
    private final boolean renewable;

    /** Public catalogue listing — no student, so no expiry. */
    public CourseResponse(Long id, String title, String description, int pricePaise, String currency, String thumbnailUrl, int validityDays) {
        this(id, title, description, pricePaise, currency, thumbnailUrl, validityDays, null, false, null, true);
    }

    public CourseResponse(Long id, String title, String description, int pricePaise, String currency, String thumbnailUrl,
                          int validityDays, LocalDate expiryDate, boolean expired, Integer daysRemaining) {
        this(id, title, description, pricePaise, currency, thumbnailUrl, validityDays,
                expiryDate, expired, daysRemaining, true);
    }

    public CourseResponse(Long id, String title, String description, int pricePaise, String currency, String thumbnailUrl,
                          int validityDays, LocalDate expiryDate, boolean expired, Integer daysRemaining,
                          boolean renewable) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.pricePaise = pricePaise;
        this.currency = currency;
        this.thumbnailUrl = thumbnailUrl;
        this.validityDays = validityDays;
        this.expiryDate = expiryDate;
        this.expired = expired;
        this.daysRemaining = daysRemaining;
        this.renewable = renewable;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getPricePaise() { return pricePaise; }
    public String getCurrency() { return currency; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getValidityDays() { return validityDays; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public boolean isExpired() { return expired; }
    public Integer getDaysRemaining() { return daysRemaining; }
    public boolean isRenewable() { return renewable; }
}
