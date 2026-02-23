package com.maths.teacher.app.data.repository

import com.google.gson.Gson
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.model.AuthResponse
import com.maths.teacher.app.data.model.ErrorResponse
import com.maths.teacher.app.data.model.LoginRequest
import com.maths.teacher.app.data.model.SignupRequest
import retrofit2.HttpException
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val api: TeacherApi,
    private val sessionManager: com.maths.teacher.app.data.prefs.SessionManager
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(username = username.trim(), password = password))
            sessionManager.saveSession(
                token = response.token,
                userId = response.userId,
                firstName = response.firstName,
                lastName = response.lastName,
                email = response.email
            )
            Result.success(response)
        } catch (e: HttpException) {
            Result.failure(Exception(parseErrorMessage(e)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signup(
        firstName: String,
        lastName: String,
        email: String,
        mobileNumber: String,
        password: String
    ): Result<AuthResponse> {
        return try {
            val response = api.signup(
                SignupRequest(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    email = email.trim(),
                    mobileNumber = mobileNumber.trim(),
                    password = password
                )
            )
            Result.success(response)
        } catch (e: HttpException) {
            Result.failure(Exception(parseErrorMessage(e)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(e: HttpException): String {
        val body = e.response()?.errorBody()?.string() ?: return e.message()
        return try {
            Gson().fromJson(body, ErrorResponse::class.java)?.message ?: e.message()
        } catch (_: Exception) {
            e.message()
        }
    }
}
