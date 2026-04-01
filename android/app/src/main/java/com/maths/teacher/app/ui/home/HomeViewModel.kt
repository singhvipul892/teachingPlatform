package com.maths.teacher.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maths.teacher.app.data.repository.VideoRepository
import com.maths.teacher.app.domain.model.CourseWithVideos
import com.maths.teacher.app.domain.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val courses: List<CourseWithVideos> = emptyList(),
    val errorMessage: String? = null,
    val selectedVideo: Video? = null
)

class HomeViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPurchasedCourses()
    }

    fun loadPurchasedCourses() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val courses = repository.getPurchasedCourses()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    courses = courses
                )
            } catch (ex: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load courses. Please try again."
                )
            }
        }
    }

    fun selectVideo(videoId: Long) {
        val video = _uiState.value.courses
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
