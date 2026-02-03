package com.maths.teacher.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@Composable
fun SignupScreen(
    viewModel: SignupViewModel,
    navController: NavController
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Sign up",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.firstName,
            onValueChange = viewModel::updateFirstName,
            label = { Text("First name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isLoading
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.lastName,
            onValueChange = viewModel::updateLastName,
            label = { Text("Last name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isLoading
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::updateEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !state.isLoading
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.mobileNumber,
            onValueChange = viewModel::updateMobileNumber,
            label = { Text("Mobile number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            enabled = !state.isLoading
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            label = { Text("Password (min 8 characters)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !state.isLoading
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::updateConfirmPassword,
            label = { Text("Confirm password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !state.isLoading
        )

        state.errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(
                onClick = { viewModel.signup { navController.navigate("login") { popUpTo("signup") { inclusive = true } } } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign up")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { navController.popBackStack() }) {
            Text("Already have an account? Log in")
        }
    }
}
