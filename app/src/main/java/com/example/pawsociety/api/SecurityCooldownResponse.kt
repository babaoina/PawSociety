package com.example.pawsociety.api

import com.google.gson.annotations.SerializedName

data class SecurityCooldownResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("settings") val settings: SecurityCooldownSettings? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

data class SecurityCooldownSettings(
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("passwordChangedAt") val passwordChangedAt: String? = null,
    @SerializedName("emailChangedAt") val emailChangedAt: String? = null,
    @SerializedName("passwordCooldownRemainingMs") val passwordCooldownRemainingMs: Long = 0L,
    @SerializedName("emailCooldownRemainingMs") val emailCooldownRemainingMs: Long = 0L
)
