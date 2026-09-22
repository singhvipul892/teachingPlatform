package com.maths.teacher.app.domain.model

data class CourseWithVideos(
    val courseId: Long,
    val name: String,
    /** Every class in the course, chapter by chapter — what search and Resources look through. */
    val videos: List<Video>,
    /** Last day of access (yyyy-MM-dd), or null when access never ends. */
    val expiryDate: String? = null,
    val expired: Boolean = false,
    /** Days until access ends; 0 means tonight. Null when access never ends. */
    val daysRemaining: Int? = null,
    val thumbnailUrl: String? = null,
    /** The course's chapters in order, each with its classes. Empty when access has ended. */
    val chapters: List<Chapter> = emptyList()
)

/** A section of a course (Percentage, Profit & Loss, ...) and its classes, in order. */
data class Chapter(
    val id: Long,
    val title: String,
    val videos: List<Video>
)
