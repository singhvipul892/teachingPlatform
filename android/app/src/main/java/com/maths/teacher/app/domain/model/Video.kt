package com.maths.teacher.app.domain.model

data class Video(
    val id: Long,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val pdfs: List<Pdf> = emptyList()
)

data class Pdf(
    val id: Long,
    val title: String,
    val pdfType: String,
    val fileUrl: String,
    val displayOrder: Int
)
