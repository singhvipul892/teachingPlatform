package com.maths.teacher.app.ui.videodetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maths.teacher.app.data.repository.VideoRepository
import com.maths.teacher.app.domain.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoDetailUiState(
    val isLoading: Boolean = true,
    val video: Video? = null,
    val errorMessage: String? = null
)

class VideoDetailViewModel(
    private val repository: VideoRepository,
    private val videoId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoDetailUiState(isLoading = true))
    val uiState: StateFlow<VideoDetailUiState> = _uiState.asStateFlow()

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
}

class VideoDetailViewModelFactory(
    private val repository: VideoRepository,
    private val videoId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoDetailViewModel(repository, videoId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
