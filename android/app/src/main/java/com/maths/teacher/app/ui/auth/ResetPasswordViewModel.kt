package com.maths.teacher.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.model.ErrorResponse
import com.maths.teacher.app.data.model.ForgotPasswordRequest
import com.maths.teacher.app.data.model.ResetPasswordRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class ResetPasswordUiState(
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val resendSecondsLeft: Int = RESEND_COOLDOWN_SECONDS
)

// Matches the server's cooldown: a resend inside this window is silently ignored.
private const val RESEND_COOLDOWN_SECONDS = 60

class ResetPasswordViewModel(
    private val api: TeacherApi,
    private val email: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private var countdown: Job? = null

    init {
        startResendCountdown()
    }

    private fun startResendCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in RESEND_COOLDOWN_SECONDS downTo 0) {
                _uiState.update { it.copy(resendSecondsLeft = left) }
                if (left > 0) delay(1000)
            }
        }
    }

    fun resendOtp() {
        if (_uiState.value.resendSecondsLeft > 0) return
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        startResendCountdown()
        viewModelScope.launch {
            try {
                api.forgotPassword(ForgotPasswordRequest(email = email))
                _uiState.update {
                    it.copy(infoMessage = "A new code is on its way. Use the newest email — older codes stop working.")
                }
            } catch (e: HttpException) {
                val message = parseErrorMessage(e)
                _uiState.update { it.copy(errorMessage = message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Could not resend the code. Please try again.")
                }
            }
        }
    }

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
            _uiState.update { it.copy(errorMessage = errors.joinToString(" "), infoMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                api.resetPassword(
                    ResetPasswordRequest(
                        email = email,
                        otp = state.otp.trim(),
                        newPassword = state.newPassword
                    )
                )
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: HttpException) {
                val message = parseErrorMessage(e)
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Something went wrong. Please try again.")
                }
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
    private val email: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResetPasswordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResetPasswordViewModel(api, email) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
