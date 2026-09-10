package com.maths.teacher.catalog.web.dto;

import jakarta.validation.constraints.Min;

/**
 * Request DTO for updating an existing course.
 * All fields are optional to allow partial updates.
 */
public class UpdateCourseRequest {

    private String title;

    private String description;

    @Min(value = 0, message = "Price must be >= 0")
    private Integer pricePaise;

    private String currency;

    private Boolean active;

    /**
     * Days of access a new purchase gets; 0 means lifetime. Only ever applies to
     * purchases made after the change — existing students keep their own expiry.
     */
    @Min(value = 0, message = "Validity must be >= 0")
    private Integer validityDays;

    // Constructors
    public UpdateCourseRequest() {
    }

    public UpdateCourseRequest(String title, String description, Integer pricePaise, String currency, Boolean active) {
        this(title, description, pricePaise, currency, active, null);
    }

    public UpdateCourseRequest(String title, String description, Integer pricePaise, String currency, Boolean active, Integer validityDays) {
        this.title = title;
        this.description = description;
        this.pricePaise = pricePaise;
        this.currency = currency;
        this.active = active;
        this.validityDays = validityDays;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(Integer pricePaise) {
        this.pricePaise = pricePaise;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
    }
}
