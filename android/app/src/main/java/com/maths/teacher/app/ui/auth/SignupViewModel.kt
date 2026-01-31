package com.maths.teacher.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.model.ErrorResponse
import com.maths.teacher.app.data.model.SignupRequest
import com.maths.teacher.app.data.prefs.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class SignupUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val mobileNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SignupViewModel(
    private val api: TeacherApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun updateFirstName(value: String) {
        _uiState.value = _uiState.value.copy(firstName = value, errorMessage = null)
    }

    fun updateLastName(value: String) {
        _uiState.value = _uiState.value.copy(lastName = value, errorMessage = null)
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun updateMobileNumber(value: String) {
        _uiState.value = _uiState.value.copy(mobileNumber = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun updateConfirmPassword(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    fun signup(onSuccess: () -> Unit) {
        val state = _uiState.value
        val errors = mutableListOf<String>()
        if (state.firstName.isBlank()) errors.add("First name is required.")
        if (state.lastName.isBlank()) errors.add("Last name is required.")
        if (state.email.isBlank()) errors.add("Email is required.")
        if (state.mobileNumber.isBlank()) errors.add("Mobile number is required.")
        if (state.password.length < 8) errors.add("Password must be at least 8 characters.")
        if (state.password != state.confirmPassword) errors.add("Passwords do not match.")
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errorMessage = errors.joinToString(" "))
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                val response = api.signup(
                    SignupRequest(
                        firstName = state.firstName.trim(),
                        lastName = state.lastName.trim(),
                        email = state.email.trim(),
                        mobileNumber = state.mobileNumber.trim(),
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

class SignupViewModelFactory(
    private val api: TeacherApi,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SignupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SignupViewModel(api, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
