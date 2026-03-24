package com.example.pawsociety.api

import com.google.gson.annotations.SerializedName

data class UpdatePhoneRequest(
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("phoneNumber") val phoneNumber: String
)

data class UpdatePhoneResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("phoneNumber") val phoneNumber: String? = null,
    @SerializedName("error") val error: String? = null
)
