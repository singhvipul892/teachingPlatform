package com.maths.teacher.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maths.teacher.app.data.prefs.SessionManager
import com.maths.teacher.app.data.repository.VideoRepository
import com.maths.teacher.app.domain.model.SectionWithVideos
import com.maths.teacher.app.domain.model.Video
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val sections: List<SectionWithVideos> = emptyList(),
    val errorMessage: String? = null,
    val selectedVideo: Video? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val userId = sessionManager.userId
    val displayName = sessionManager.displayName

    init {
        loadHomeSections()
    }

    private fun loadHomeSections() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val sections = repository.getHomeSections()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sections = sections
                )
            } catch (ex: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load videos. Please try again."
                )
            }
        }
    }

    fun selectVideo(videoId: Long) {
        val video = _uiState.value.sections
            .flatMap { it.videos }
            .firstOrNull { it.id == videoId }
        _uiState.value = _uiState.value.copy(selectedVideo = video)
    }

    fun clearSelectedVideo() {
        _uiState.value = _uiState.value.copy(selectedVideo = null)
    }

    suspend fun logout() {
        sessionManager.clearSession()
    }
}
