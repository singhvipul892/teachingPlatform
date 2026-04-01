package com.maths.teacher.app.data.model

data class CourseDto(
    val id: Long,
    val title: String,
    val description: String?,
    val pricePaise: Int,
    val currency: String,
    val thumbnailUrl: String?
)

data class UserCoursesResponse(
    val purchasedCourses: List<CourseDto>
)
