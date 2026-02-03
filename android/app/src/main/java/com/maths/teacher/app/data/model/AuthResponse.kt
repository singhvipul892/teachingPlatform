package com.maths.teacher.app.data.model

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("userId") val userId: Long,
    @SerializedName("email") val email: String,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("mobileNumber") val mobileNumber: String?
)
