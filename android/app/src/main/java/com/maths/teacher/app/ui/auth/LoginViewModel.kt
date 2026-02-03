package com.maths.teacher.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.model.ErrorResponse
import com.maths.teacher.app.data.prefs.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val api: TeacherApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateUsername(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.username.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Email or mobile number is required.")
            return
        }
        if (state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Password is required.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val response = api.login(
                    com.maths.teacher.app.data.model.LoginRequest(
                        username = state.username.trim(),
                        password = state.password
                    )
                )
                sessionManager.saveSession(
                    token = response.token,
                    userId = response.userId,
                    firstName = response.firstName,
                    lastName = response.lastName,
                    email = response.email
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

class LoginViewModelFactory(
    private val api: TeacherApi,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(api, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
