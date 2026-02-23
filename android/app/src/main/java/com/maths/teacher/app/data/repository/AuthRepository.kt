package com.maths.teacher.app.data.repository

import com.maths.teacher.app.data.model.AuthResponse

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<AuthResponse>
    suspend fun signup(
        firstName: String,
        lastName: String,
        email: String,
        mobileNumber: String,
        password: String
    ): Result<AuthResponse>
}
