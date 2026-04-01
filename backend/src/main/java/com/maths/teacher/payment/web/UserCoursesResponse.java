package com.maths.teacher.payment.web;

import java.util.List;

public class UserCoursesResponse {

    private final List<CourseResponse> purchasedCourses;

    public UserCoursesResponse(List<CourseResponse> purchasedCourses) {
        this.purchasedCourses = purchasedCourses;
    }

    public List<CourseResponse> getPurchasedCourses() { return purchasedCourses; }
}
