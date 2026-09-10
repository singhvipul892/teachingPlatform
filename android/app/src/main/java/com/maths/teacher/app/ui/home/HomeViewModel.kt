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
    val selectedVideo: Video? = null,
    val searchQuery: String = "",
    val searchResults: List<CourseSearchResult> = emptyList()
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
                    courses = courses,
                    // Keep any active query applied to the freshly loaded courses.
                    searchResults = searchCourses(courses, _uiState.value.searchQuery)
                )
            } catch (ex: Exception) {
                // A real 401 is already handled centrally by the ApiClient interceptor
                // (triggers AuthEventBus -> logout + navigation). Any other failure here
                // (network, timeout, 403/404/500, parse error) is retryable and unrelated
                // to the session -- do not force a logout for it.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Couldn't load courses. Check your connection and try again."
                )
            }
        }
    }

    /**
     * Filtering runs synchronously against the already-loaded courses -- the whole catalog is in
     * memory, so there is nothing to debounce and no network call to make.
     */
    fun onSearchQueryChange(query: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            searchQuery = query,
            searchResults = searchCourses(current.courses, query)
        )
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
