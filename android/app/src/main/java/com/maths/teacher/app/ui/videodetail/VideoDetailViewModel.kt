package com.maths.teacher.app.ui.videodetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maths.teacher.app.data.prefs.SessionManager
import com.maths.teacher.app.data.repository.VideoRepository
import com.maths.teacher.app.domain.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoDetailUiState(
    val isLoading: Boolean = true,
    val video: Video? = null,
    val errorMessage: String? = null,
    val downloadingPdfId: Long? = null
)

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val videoId: Long = savedStateHandle.get<String>("videoId")?.toLongOrNull() ?: 0L

    private val _uiState = MutableStateFlow(VideoDetailUiState(isLoading = true))
    val uiState: StateFlow<VideoDetailUiState> = _uiState.asStateFlow()

    val userId = sessionManager.userId

    private val _openPdfAfterDownload = MutableStateFlow<Pair<Long, Long>?>(null)
    val openPdfAfterDownload: StateFlow<Pair<Long, Long>?> = _openPdfAfterDownload.asStateFlow()

    init {
        loadVideo()
    }

    private fun loadVideo() {
        viewModelScope.launch {
            try {
                val video = repository.getVideoById(videoId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    video = video,
                    errorMessage = if (video == null) "Video not found." else null
                )
            } catch (ex: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load video. Please try again."
                )
            }
        }
    }

    fun downloadPdf(videoId: Long, pdfId: Long, pdfTitle: String) {
        viewModelScope.launch {
            val uid = userId.first() ?: return@launch
            _uiState.value = _uiState.value.copy(downloadingPdfId = pdfId)
            repository.downloadPdf(videoId, pdfId, uid, pdfTitle)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(downloadingPdfId = null)
                    _openPdfAfterDownload.value = Pair(videoId, pdfId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(downloadingPdfId = null) }
        }
    }

    fun clearOpenPdfRequest() {
        _openPdfAfterDownload.value = null
    }
}
