package com.maths.teacher.app.data.api

import com.maths.teacher.app.data.model.PdfDownloadResponse
import com.maths.teacher.app.data.model.SectionDto
import com.maths.teacher.app.data.model.VideoDto
import retrofit2.http.GET
import retrofit2.http.Path

interface TeacherApi {

    @GET("api/sections")
    suspend fun getSections(): List<SectionDto>

    @GET("api/sections/{section}/videos")
    suspend fun getVideosBySection(@Path("section") section: String): List<VideoDto>

    @GET("api/videos/{videoId}/pdfs/{pdfId}/download")
    suspend fun downloadPdf(
        @Path("videoId") videoId: Long,
        @Path("pdfId") pdfId: Long
    ): PdfDownloadResponse
}
