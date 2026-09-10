package com.maths.teacher.app.domain.model

data class CourseWithVideos(
    val courseId: Long,
    val name: String,
    val videos: List<Video>,
    /** Last day of access (yyyy-MM-dd), or null when access never ends. */
    val expiryDate: String? = null,
    val expired: Boolean = false,
    /** Days until access ends; 0 means tonight. Null when access never ends. */
    val daysRemaining: Int? = null
)
