package com.maths.teacher.app.data.repository

import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.domain.model.CourseWithVideos
import com.maths.teacher.app.domain.model.Pdf
import com.maths.teacher.app.domain.model.Video

interface VideoRepository {
    suspend fun getPurchasedCourses(): List<CourseWithVideos>
    suspend fun getVideoById(videoId: Long): Video?
}

class DefaultVideoRepository(
    private val api: TeacherApi
) : VideoRepository {

    override suspend fun getPurchasedCourses(): List<CourseWithVideos> {
        val coursesResponse = api.getUserCourses()
        return coursesResponse.purchasedCourses.map { courseDto ->
            val videos = api.getCourseVideos(courseDto.id)
                .sortedBy { it.displayOrder }
                .map { dto ->
                    Video(
                        id = dto.id,
                        videoId = dto.videoId,
                        title = dto.title,
                        thumbnailUrl = dto.thumbnailUrl,
                        duration = dto.duration,
                        pdfs = dto.pdfs.map { pdfDto ->
                            Pdf(
                                id = pdfDto.id,
                                title = pdfDto.title,
                                pdfType = pdfDto.pdfType,
                                fileUrl = pdfDto.fileUrl,
                                displayOrder = pdfDto.displayOrder
                            )
                        }.sortedBy { it.displayOrder }
                    )
                }
            CourseWithVideos(
                courseId = courseDto.id,
                name = courseDto.title,
                videos = videos
            )
        }
    }

    override suspend fun getVideoById(videoId: Long): Video? {
        return getPurchasedCourses()
            .flatMap { it.videos }
            .firstOrNull { it.id == videoId }
    }
}
