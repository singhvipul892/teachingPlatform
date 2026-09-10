package com.maths.teacher.app.data.model

data class CourseDto(
    val id: Long,
    val title: String,
    val description: String?,
    val pricePaise: Int,
    val currency: String,
    val thumbnailUrl: String?,
    /** Days of access a new purchase gets; 0 means the course never expires. */
    val validityDays: Int = 0,
    /** Last day this student can use the course (yyyy-MM-dd), or null for lifetime access. */
    val expiryDate: String? = null,
    val expired: Boolean = false,
    /** Days until access ends; 0 means tonight. Null for lifetime access. */
    val daysRemaining: Int? = null
)

data class UserCoursesResponse(
    val purchasedCourses: List<CourseDto>
)
