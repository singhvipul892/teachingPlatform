package com.maths.teacher.app.data.model

data class VideoDto(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val displayOrder: Int
)
