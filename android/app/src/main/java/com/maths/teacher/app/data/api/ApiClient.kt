package com.maths.teacher.app.data.api

import com.maths.teacher.app.config.AppConstants
import com.maths.teacher.app.data.prefs.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    fun createApi(sessionManager: SessionManager): TeacherApi {
        val authInterceptor = Interceptor { chain ->
            val token = sessionManager.currentToken
            val hasToken = !token.isNullOrBlank()
            val request = if (hasToken) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            val response = chain.proceed(request)
            // An authenticated request that comes back 401 means the token has expired
            // or is no longer valid. Signal the UI to log out and return to login.
            if (response.code == 401 && hasToken) {
                AuthEventBus.notifyUnauthorized()
            }
            response
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(AppConstants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TeacherApi::class.java)
    }
}
