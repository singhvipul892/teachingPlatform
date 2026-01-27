package com.maths.teacher.app.data.repository

import android.net.Uri
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.domain.model.SectionWithVideos
import com.maths.teacher.app.domain.model.Video

interface VideoRepository {
    suspend fun getHomeSections(): List<SectionWithVideos>
}

class DefaultVideoRepository(
    private val api: TeacherApi
) : VideoRepository {

    override suspend fun getHomeSections(): List<SectionWithVideos> {
        val sections = api.getSections()
        return sections.map { sectionDto ->
            val encodedSection = Uri.encode(sectionDto.name)
            val videos = api.getVideosBySection(encodedSection)
                .sortedBy { it.displayOrder }
                .map { dto ->
                    Video(
                        videoId = dto.videoId,
                        title = dto.title,
                        thumbnailUrl = dto.thumbnailUrl,
                        duration = dto.duration
                    )
                }
            SectionWithVideos(
                name = sectionDto.name,
                videos = videos
            )
        }
    }
}
