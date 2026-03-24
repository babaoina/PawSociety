package com.example.pawsociety.api

import com.google.gson.annotations.SerializedName

data class ChangeEmailRequest(
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("newEmail") val newEmail: String,
    @SerializedName("password") val password: String
)

data class ChangeEmailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("newEmail") val newEmail: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("emailChangedAt") val emailChangedAt: String? = null
)
