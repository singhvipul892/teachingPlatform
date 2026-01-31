package com.maths.teacher.app.data.model

import com.google.gson.annotations.SerializedName

data class SignupRequest(
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("email") val email: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("password") val password: String
)
