package com.maths.teacher.app.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maths.teacher.app.data.repository.VideoRepository
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

    private val _uiState = MutableStateFlow(ResourcesUiState(isLoadingSections = true))
    val uiState: StateFlow<ResourcesUiState> = _uiState.asStateFlow()

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
