package com.example.pawsociety.api

import com.google.gson.annotations.SerializedName

/**
 * Response body for POST /api/settings/change-password
 */
data class ChangePasswordResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("passwordChangedAt") val passwordChangedAt: String? = null
)
