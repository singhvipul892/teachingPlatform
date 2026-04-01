package com.maths.teacher.app.data.model

import com.google.gson.annotations.SerializedName

data class ForgotPasswordRequest(
    @SerializedName("mobileNumber") val mobileNumber: String
)
