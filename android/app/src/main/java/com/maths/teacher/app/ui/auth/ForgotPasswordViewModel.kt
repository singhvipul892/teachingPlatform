package com.maths.teacher.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.model.ErrorResponse
import com.maths.teacher.app.data.model.ForgotPasswordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class ForgotPasswordUiState(
    val mobileNumber: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ForgotPasswordViewModel(
    private val api: TeacherApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun updateMobileNumber(value: String) {
        _uiState.value = _uiState.value.copy(mobileNumber = value, errorMessage = null)
    }

    fun sendOtp(onSuccess: (String) -> Unit) {
        val state = _uiState.value
        if (state.mobileNumber.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Mobile number is required.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                api.forgotPassword(ForgotPasswordRequest(mobileNumber = state.mobileNumber.trim()))
                _uiState.value = state.copy(isLoading = false)
                onSuccess(state.mobileNumber.trim())
            } catch (e: HttpException) {
                val message = parseErrorMessage(e)
                _uiState.value = state.copy(isLoading = false, errorMessage = message)
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Something went wrong. Please try again."
                )
            }
        }
    }

    private fun parseErrorMessage(e: HttpException): String {
        val body = e.response()?.errorBody()?.string() ?: return e.message()
        return try {
            Gson().fromJson(body, ErrorResponse::class.java)?.message
                ?: e.message()
        } catch (_: Exception) {
            e.message()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

class ForgotPasswordViewModelFactory(
    private val api: TeacherApi
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ForgotPasswordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ForgotPasswordViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
