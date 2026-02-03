package com.maths.teacher.app.data.model

data class VideoDto(
    val id: Long,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val displayOrder: Int,
    val pdfs: List<PdfDto> = emptyList()
)

data class PdfDto(
    val id: Long,
    val title: String,
    val pdfType: String,
    val fileUrl: String,
    val displayOrder: Int
)

data class PdfDownloadResponse(
    val url: String,
    val expiresInSeconds: Int
)
