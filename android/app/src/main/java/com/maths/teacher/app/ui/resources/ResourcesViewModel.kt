package com.maths.teacher.app.ui.resources

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

data class ResourcesUiState(
    val sections: List<String> = emptyList(),
    val selectedSection: String? = null,
    val videos: List<Video> = emptyList(),
    val isLoadingSections: Boolean = false,
    val isLoadingVideos: Boolean = false,
    val errorMessage: String? = null,
    val downloadingPdfId: Long? = null
)

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResourcesUiState(isLoadingSections = true))
    val uiState: StateFlow<ResourcesUiState> = _uiState.asStateFlow()

    val userId = sessionManager.userId

    private val _openPdfAfterDownload = MutableStateFlow<Pair<Long, Long>?>(null)
    val openPdfAfterDownload: StateFlow<Pair<Long, Long>?> = _openPdfAfterDownload.asStateFlow()

    init {
        loadSections()
    }

    private fun loadSections() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSections = true, errorMessage = null)
            try {
                val sections = repository.getSections()
                _uiState.value = _uiState.value.copy(
                    sections = sections,
                    isLoadingSections = false
                )
            } catch (ex: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingSections = false,
                    errorMessage = "Failed to load sections."
                )
            }
        }
    }

    fun selectSection(section: String) {
        if (_uiState.value.selectedSection == section) return
        _uiState.value = _uiState.value.copy(
            selectedSection = section,
            videos = emptyList(),
            errorMessage = null
        )
        loadVideosForSection(section)
    }

    private fun loadVideosForSection(section: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVideos = true, errorMessage = null)
            try {
                val allVideos = repository.getVideosBySection(section)
                val videosWithPdfs = allVideos.filter { it.pdfs.isNotEmpty() }
                _uiState.value = _uiState.value.copy(
                    videos = videosWithPdfs,
                    isLoadingVideos = false
                )
            } catch (ex: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingVideos = false,
                    errorMessage = "Failed to load videos."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
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
