package com.maths.teacher.app.domain.model

data class CourseWithVideos(
    val courseId: Long,
    val name: String,
    val videos: List<Video>
)
