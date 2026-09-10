package com.maths.teacher.catalog.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new course.
 */
public class CreateCourseRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description = "";

    @Min(value = 0, message = "Price must be >= 0")
    private Integer pricePaise;

    private String currency = "INR";

    private Boolean active = true;

    /** Days of access a new purchase gets. 0 (the default) means lifetime. */
    @Min(value = 0, message = "Validity must be >= 0")
    private Integer validityDays = 0;

    // Constructors
    public CreateCourseRequest() {
    }

    public CreateCourseRequest(String title, String description, Integer pricePaise, String currency, Boolean active) {
        this(title, description, pricePaise, currency, active, 0);
    }

    public CreateCourseRequest(String title, String description, Integer pricePaise, String currency, Boolean active, Integer validityDays) {
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
