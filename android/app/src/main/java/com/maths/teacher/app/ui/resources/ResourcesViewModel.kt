package com.maths.teacher.app.ui.resources

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

data class ResourcesUiState(
    val sections: List<String> = emptyList(),
    val selectedSection: String? = null,
    val videos: List<Video> = emptyList(),
    val isLoadingSections: Boolean = false,
    val isLoadingVideos: Boolean = false,
    val errorMessage: String? = null
)

class ResourcesViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private var allCourses: List<CourseWithVideos> = emptyList()

    private val _uiState = MutableStateFlow(ResourcesUiState(isLoadingSections = true))
    val uiState: StateFlow<ResourcesUiState> = _uiState.asStateFlow()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSections = true, errorMessage = null)
            try {
                allCourses = repository.getPurchasedCourses()
                _uiState.value = _uiState.value.copy(
                    sections = allCourses.map { it.name },
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
        val course = allCourses.firstOrNull { it.name == section }
        val videosWithPdfs = course?.videos?.filter { it.pdfs.isNotEmpty() } ?: emptyList()
        _uiState.value = _uiState.value.copy(
            selectedSection = section,
            videos = videosWithPdfs,
            isLoadingVideos = false,
            errorMessage = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

class ResourcesViewModelFactory(
    private val repository: VideoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResourcesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResourcesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
