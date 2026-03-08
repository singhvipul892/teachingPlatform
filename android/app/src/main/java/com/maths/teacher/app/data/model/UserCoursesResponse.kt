package com.maths.teacher.app.data.model

data class UserCoursesResponse(
    val purchasedCourseIds: List<Long>,
    val purchasedSectionNames: List<String>
)
