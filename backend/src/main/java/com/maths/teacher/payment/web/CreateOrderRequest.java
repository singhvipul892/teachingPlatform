package com.maths.teacher.payment.web;

import jakarta.validation.constraints.NotNull;

public class CreateOrderRequest {

    @NotNull(message = "courseId is required")
    private Long courseId;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
}
