package com.maths.teacher.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maths.teacher.app.data.repository.VideoRepository
import com.maths.teacher.app.domain.model.SectionWithVideos
import com.maths.teacher.app.domain.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val sections: List<SectionWithVideos> = emptyList(),
    val errorMessage: String? = null,
    val selectedVideo: Video? = null
)

class HomeViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
}

class HomeViewModelFactory(
    private val repository: VideoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
