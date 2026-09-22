package com.maths.teacher.app.data.model

/** One chapter of a course from GET api/courses/{id}/chapters; videos are in display order. */
data class ChapterDto(
    val id: Long,
    val title: String,
    val displayOrder: Int,
    val videos: List<VideoDto> = emptyList()
)
