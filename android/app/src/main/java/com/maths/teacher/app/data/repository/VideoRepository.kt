package com.maths.teacher.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.prefs.savePdfPath
import com.maths.teacher.app.domain.model.Pdf
import com.maths.teacher.app.domain.model.SectionWithVideos
import com.maths.teacher.app.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named

interface VideoRepository {
    suspend fun getHomeSections(): List<SectionWithVideos>
    suspend fun getSections(): List<String>
    suspend fun getVideosBySection(section: String): List<Video>
    suspend fun getVideoById(videoId: Long): Video?
    suspend fun downloadPdf(videoId: Long, pdfId: Long, userId: Long, pdfTitle: String): Result<String>
}

class DefaultVideoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TeacherApi,
    @Named("s3") private val s3OkHttpClient: OkHttpClient
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

    override suspend fun getVideoById(videoId: Long): Video? {
        return getHomeSections()
            .flatMap { it.videos }
            .firstOrNull { it.id == videoId }
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

    override suspend fun downloadPdf(videoId: Long, pdfId: Long, userId: Long, pdfTitle: String): Result<String> {
        return try {
            val resp = api.downloadPdf(videoId, pdfId)
            val s3Url = resp.url
            if (s3Url.isBlank()) {
                throw IllegalArgumentException("S3 pre-signed URL is empty")
            }
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val safeName = pdfTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val file = File(dir, "$safeName.pdf")

            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(s3Url)
                    .get()
                    .build()
                s3OkHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("S3 download failed: ${response.code} ${response.message}")
                    }
                    val body = response.body ?: throw java.io.IOException("S3 response body is null")
                    body.byteStream().use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }

            savePdfPath(context, userId, videoId, pdfId, file.absolutePath)
            Log.d(TAG, "PDF downloaded from S3: videoId=$videoId, pdfId=$pdfId, path=${file.absolutePath}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "PDF download from S3 failed: videoId=$videoId, pdfId=$pdfId", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "PdfDownload"
    }
}
