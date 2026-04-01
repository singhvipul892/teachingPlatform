package com.maths.teacher.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.model.ErrorResponse
import com.maths.teacher.app.data.model.ResetPasswordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class ResetPasswordUiState(
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ResetPasswordViewModel(
    private val api: TeacherApi,
    private val mobileNumber: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun updateOtp(value: String) {
        _uiState.value = _uiState.value.copy(otp = value, errorMessage = null)
    }

    fun updateNewPassword(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value, errorMessage = null)
    }

    fun updateConfirmPassword(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    fun resetPassword(onSuccess: () -> Unit) {
        val state = _uiState.value
        val errors = mutableListOf<String>()
        if (state.otp.isBlank() || state.otp.length != 6) errors.add("Enter the 6-digit OTP.")
        if (state.newPassword.length < 8) errors.add("Password must be at least 8 characters.")
        if (state.newPassword != state.confirmPassword) errors.add("Passwords do not match.")
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errorMessage = errors.joinToString(" "))
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                api.resetPassword(
                    ResetPasswordRequest(
                        mobileNumber = mobileNumber,
                        otp = state.otp.trim(),
                        newPassword = state.newPassword
                    )
                )
                _uiState.value = state.copy(isLoading = false)
                onSuccess()
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

class ResetPasswordViewModelFactory(
    private val api: TeacherApi,
    private val mobileNumber: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResetPasswordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResetPasswordViewModel(api, mobileNumber) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
