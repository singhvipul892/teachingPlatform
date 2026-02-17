package com.maths.teacher.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maths.teacher.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository
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
            authRepository.signup(
                firstName = state.firstName.trim(),
                lastName = state.lastName.trim(),
                email = state.email.trim(),
                mobileNumber = state.mobileNumber.trim(),
                password = state.password
            )
                .onSuccess {
                    _uiState.value = state.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Something went wrong. Please try again."
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
