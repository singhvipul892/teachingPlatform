package com.maths.teacher.app.data.model

import com.google.gson.annotations.SerializedName

data class ResetPasswordRequest(
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("otp") val otp: String,
    @SerializedName("newPassword") val newPassword: String
)
