package com.maths.teacher.app.data.repository

import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.model.VideoDto
import com.maths.teacher.app.domain.model.Chapter
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
            // An expired course would answer 403 and take the whole Home load down
            // with it. The course still belongs on Home, just without its classes.
            val chapters = if (courseDto.expired) {
                emptyList()
            } else {
                api.getCourseChapters(courseDto.id)
                    .sortedBy { it.displayOrder }
                    .map { chapterDto ->
                        Chapter(
                            id = chapterDto.id,
                            title = chapterDto.title,
                            videos = chapterDto.videos.sortedBy { it.displayOrder }.map { it.toVideo() }
                        )
                    }
            }
            CourseWithVideos(
                courseId = courseDto.id,
                name = courseDto.title,
                videos = chapters.flatMap { it.videos },
                expiryDate = courseDto.expiryDate,
                expired = courseDto.expired,
                daysRemaining = courseDto.daysRemaining,
                thumbnailUrl = courseDto.thumbnailUrl?.takeIf { it.isNotBlank() },
                chapters = chapters
            )
        }
    }

    override suspend fun getVideoById(videoId: Long): Video? {
        return getPurchasedCourses()
            .flatMap { it.videos }
            .firstOrNull { it.id == videoId }
    }

    private fun VideoDto.toVideo() = Video(
        id = id,
        videoId = videoId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        pdfs = pdfs.map { pdfDto ->
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
