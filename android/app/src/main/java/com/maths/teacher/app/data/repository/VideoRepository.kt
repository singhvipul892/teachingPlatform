package com.maths.teacher.app.data.repository

import android.net.Uri
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.domain.model.Pdf
import com.maths.teacher.app.domain.model.SectionWithVideos
import com.maths.teacher.app.domain.model.Video

interface VideoRepository {
    suspend fun getHomeSections(): List<SectionWithVideos>
    suspend fun getSections(): List<String>
    suspend fun getVideosBySection(section: String): List<Video>
}

class DefaultVideoRepository(
    private val api: TeacherApi
) : VideoRepository {

    override suspend fun getHomeSections(): List<SectionWithVideos> {
        val sections = api.getSections()
        return sections.map { sectionDto ->
                val encodedSection = Uri.encode(sectionDto.name)
            val videos = fetchVideosForSection(encodedSection)
            SectionWithVideos(
                name = sectionDto.name,
                videos = videos
            )
        }
    }

    override suspend fun getSections(): List<String> {
        return api.getSections().map { it.name }
    }

    override suspend fun getVideosBySection(section: String): List<Video> {
        val encodedSection = Uri.encode(section)
        return fetchVideosForSection(encodedSection)
    }

    private suspend fun fetchVideosForSection(encodedSection: String): List<Video> {
        return api.getVideosBySection(encodedSection)
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
    }
}
